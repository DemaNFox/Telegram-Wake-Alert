import asyncio
import os
import time
from collections.abc import Awaitable, Callable
from dataclasses import asdict, dataclass

import structlog
from telethon import TelegramClient, events, utils
from telethon.errors import RPCError
from telethon.tl.types import User

from app.core.config import Settings
from app.domain.events import NewMessageEvent

log = structlog.get_logger(__name__)
EventHandler = Callable[[NewMessageEvent], Awaitable[None]]


@dataclass(frozen=True)
class RecentPerson:
    sender_id: str
    name: str
    username: str | None
    last_message_at: int | None

    def to_payload(self) -> dict[str, object]:
        return asdict(self)


@dataclass(frozen=True)
class RecentGroup:
    chat_id: str
    title: str
    last_message_at: int | None

    def to_payload(self) -> dict[str, object]:
        return asdict(self)


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
        self._connected = False
        self._last_connected_at: int | None = None
        self._last_error: str | None = None
        self._last_message_at: int | None = None
        self._last_filtered_reason: str | None = None
        self._messages_seen = 0
        self._messages_sent = 0
        self._messages_filtered = 0
        self._recent_people: dict[str, RecentPerson] = {}
        self._recent_groups: dict[str, RecentGroup] = {}
        self._me_id: int | None = None

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
                me = await self._client.get_me()
                self._me_id = int(me.id) if me else None
                self._connected = True
                self._last_connected_at = int(time.time())
                self._last_error = None
                log.info("telegram_listener_connected")
                delay = self._settings.telegram_reconnect_min_seconds
                await self._client.run_until_disconnected()
            except asyncio.CancelledError:
                raise
            except (OSError, RPCError, RuntimeError) as exc:
                self._connected = False
                self._last_error = str(exc)
                log.error("telegram_listener_error", error=str(exc), retry_in_seconds=delay)
                await asyncio.sleep(delay)
                delay = min(delay * 2, self._settings.telegram_reconnect_max_seconds)
            finally:
                self._connected = False
                if self._client.is_connected():
                    await self._client.disconnect()

    async def _handle_new_message(self, event: events.NewMessage.Event) -> None:
        message = event.message
        if message.out:
            self._mark_filtered("outgoing")
            return
        if message.date and int(message.date.timestamp()) < self._started_at:
            self._mark_filtered("old_message")
            return

        sender = await event.get_sender()
        reason = await self._classify_message(event, sender)
        if reason is None:
            self._mark_filtered("unsupported_message_source")
            return

        self._messages_seen += 1
        sender_name = utils.get_display_name(sender) if sender else ""
        if not sender_name:
            sender_name = str(getattr(sender, "id", "unknown"))
        text = message.message or ""
        chat = await event.get_chat()
        chat_title = getattr(chat, "title", None)
        payload = NewMessageEvent(
            chat_id=str(event.chat_id),
            sender_id=str(getattr(sender, "id", "unknown")),
            sender_name=sender_name,
            message=text,
            timestamp=int(message.date.timestamp()) if message.date else int(time.time()),
            chat_title=chat_title,
            reason=reason,
        )
        self._messages_sent += 1
        self._last_message_at = payload.timestamp
        if isinstance(sender, User):
            self._remember_person(sender, sender_name, payload.timestamp)
        if event.is_group and chat_title:
            self._remember_group(payload.chat_id, chat_title, payload.timestamp)
        log.info(
            "telegram_new_message",
            chat_id=payload.chat_id,
            sender_id=payload.sender_id,
            reason=payload.reason,
        )
        await self._on_event(payload)

    async def _classify_message(self, event: events.NewMessage.Event, sender: object) -> str | None:
        message = event.message
        if event.is_private:
            if not isinstance(sender, User):
                return None
            return "private_bot" if sender.bot else "private_user"
        if not event.is_group:
            return None
        if message.is_reply and self._me_id is not None:
            reply = await message.get_reply_message()
            if reply and reply.sender_id == self._me_id:
                return "group_reply"
        if getattr(message, "mentioned", False):
            return "group_mention"
        return "group_message"

    def _mark_filtered(self, reason: str) -> None:
        self._messages_filtered += 1
        self._last_filtered_reason = reason

    def stats(self) -> dict[str, object]:
        return {
            "connected": self._connected,
            "started_at": self._started_at,
            "last_connected_at": self._last_connected_at,
            "last_error": self._last_error,
            "last_message_at": self._last_message_at,
            "last_filtered_reason": self._last_filtered_reason,
            "messages_seen": self._messages_seen,
            "messages_sent": self._messages_sent,
            "messages_filtered": self._messages_filtered,
        }

    async def recent_people(self, limit: int = 50) -> list[dict[str, object]]:
        if self._client.is_connected():
            try:
                async for dialog in self._client.iter_dialogs(limit=max(limit * 3, 50)):
                    entity = dialog.entity
                    if not isinstance(entity, User):
                        continue
                    name = " ".join(part for part in [entity.first_name, entity.last_name] if part).strip()
                    if not name:
                        name = entity.username or str(entity.id)
                    last_message = getattr(dialog, "message", None)
                    last_message_date = getattr(last_message, "date", None)
                    self._remember_person(
                        entity,
                        name,
                        int(last_message_date.timestamp()) if last_message_date else None,
                    )
                    if len(self._recent_people) >= limit:
                        break
            except RPCError as exc:
                self._last_error = str(exc)
                log.error("telegram_recent_people_error", error=str(exc))
        people = sorted(
            self._recent_people.values(),
            key=lambda person: person.last_message_at or 0,
            reverse=True,
        )
        return [person.to_payload() for person in people[:limit]]

    async def recent_groups(self, limit: int = 100) -> list[dict[str, object]]:
        if self._client.is_connected():
            try:
                async for dialog in self._client.iter_dialogs(limit=None):
                    if not dialog.is_group:
                        continue
                    last_message = getattr(dialog, "message", None)
                    last_message_date = getattr(last_message, "date", None)
                    self._remember_group(
                        str(dialog.id),
                        dialog.name or str(dialog.id),
                        int(last_message_date.timestamp()) if last_message_date else None,
                    )
                    if len(self._recent_groups) >= limit:
                        break
            except RPCError as exc:
                self._last_error = str(exc)
                log.error("telegram_recent_groups_error", error=str(exc))
        groups = sorted(
            self._recent_groups.values(),
            key=lambda group: group.last_message_at or 0,
            reverse=True,
        )
        return [group.to_payload() for group in groups[:limit]]

    def _remember_person(self, sender: User, name: str, last_message_at: int | None) -> None:
        self._recent_people[str(sender.id)] = RecentPerson(
            sender_id=str(sender.id),
            name=name,
            username=sender.username,
            last_message_at=last_message_at,
        )

    def _remember_group(self, chat_id: str, title: str, last_message_at: int | None) -> None:
        self._recent_groups[chat_id] = RecentGroup(
            chat_id=chat_id,
            title=title,
            last_message_at=last_message_at,
        )
