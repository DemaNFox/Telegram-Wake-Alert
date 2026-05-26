import asyncio
import os
import time
from collections.abc import Awaitable, Callable

import structlog
from telethon import TelegramClient, events
from telethon.errors import RPCError
from telethon.tl.types import User

from app.core.config import Settings
from app.domain.events import NewMessageEvent

log = structlog.get_logger(__name__)
EventHandler = Callable[[NewMessageEvent], Awaitable[None]]


class TelegramListenerService:
    def __init__(self, settings: Settings, on_event: EventHandler) -> None:
        self._settings = settings
        self._on_event = on_event
        os.makedirs("sessions", exist_ok=True)
        self._client = TelegramClient(
            f"sessions/{settings.telegram_session_name}",
            settings.telegram_api_id,
            settings.telegram_api_hash,
        )
        self._started_at = int(time.time())
        self._task: asyncio.Task[None] | None = None
        self._stop_event = asyncio.Event()

    async def start(self) -> None:
        self._stop_event.clear()
        self._started_at = int(time.time())
        self._client.add_event_handler(self._handle_new_message, events.NewMessage(incoming=True))
        self._task = asyncio.create_task(self._run_forever(), name="telegram-listener")

    async def stop(self) -> None:
        self._stop_event.set()
        if self._task:
            self._task.cancel()
            await asyncio.gather(self._task, return_exceptions=True)
        if self._client.is_connected():
            await self._client.disconnect()

    async def _run_forever(self) -> None:
        delay = self._settings.telegram_reconnect_min_seconds
        while not self._stop_event.is_set():
            try:
                await self._client.connect()
                if not await self._client.is_user_authorized():
                    raise RuntimeError(
                        "Telegram session is not authorized. Run `python -m app.telegram_login` locally first."
                    )
                log.info("telegram_listener_connected")
                delay = self._settings.telegram_reconnect_min_seconds
                await self._client.run_until_disconnected()
            except asyncio.CancelledError:
                raise
            except (OSError, RPCError, RuntimeError) as exc:
                log.error("telegram_listener_error", error=str(exc), retry_in_seconds=delay)
                await asyncio.sleep(delay)
                delay = min(delay * 2, self._settings.telegram_reconnect_max_seconds)
            finally:
                if self._client.is_connected():
                    await self._client.disconnect()

    async def _handle_new_message(self, event: events.NewMessage.Event) -> None:
        message = event.message
        if message.out or not event.is_private:
            return
        if message.date and int(message.date.timestamp()) < self._started_at:
            return
        sender = await event.get_sender()
        if not isinstance(sender, User) or sender.bot:
            return
        sender_name = " ".join(part for part in [sender.first_name, sender.last_name] if part).strip()
        if not sender_name:
            sender_name = sender.username or str(sender.id)
        text = message.message or ""
        payload = NewMessageEvent(
            chat_id=str(event.chat_id),
            sender_id=str(sender.id),
            sender_name=sender_name,
            message=text,
            timestamp=int(message.date.timestamp()) if message.date else int(time.time()),
        )
        log.info("telegram_new_private_message", chat_id=payload.chat_id, sender_id=payload.sender_id)
        await self._on_event(payload)
