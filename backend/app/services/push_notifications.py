import asyncio
import json
import os
from pathlib import Path
from datetime import timedelta

import firebase_admin
from firebase_admin import credentials, messaging
import structlog

from app.core.config import Settings
from app.domain.events import NewMessageEvent

log = structlog.get_logger(__name__)


class PushInstallationStore:
    def __init__(self, path: str) -> None:
        self._path = Path(path)
        self._lock = asyncio.Lock()
        self._installation_ids = self._load()

    async def register(
        self,
        installation_id: str,
        previous_installation_id: str | None = None,
    ) -> int:
        async with self._lock:
            if previous_installation_id and previous_installation_id != installation_id:
                self._installation_ids.discard(previous_installation_id)
            self._installation_ids.add(installation_id)
            self._persist()
            return len(self._installation_ids)

    async def remove(self, installation_ids: set[str]) -> int:
        if not installation_ids:
            return len(self._installation_ids)
        async with self._lock:
            self._installation_ids.difference_update(installation_ids)
            self._persist()
            return len(self._installation_ids)

    async def all(self) -> list[str]:
        async with self._lock:
            return sorted(self._installation_ids)

    async def count(self) -> int:
        async with self._lock:
            return len(self._installation_ids)

    def _load(self) -> set[str]:
        try:
            raw = json.loads(self._path.read_text(encoding="utf-8"))
            return {str(token).strip() for token in raw if str(token).strip()}
        except (FileNotFoundError, json.JSONDecodeError, OSError, TypeError):
            return set()

    def _persist(self) -> None:
        self._path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self._path.with_suffix(f"{self._path.suffix}.tmp")
        temporary.write_text(
            json.dumps(sorted(self._installation_ids), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        os.replace(temporary, self._path)


class FirebasePushService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._store = PushInstallationStore(settings.firebase_installation_store_path)
        self._enabled = False
        self._last_error: str | None = None
        self._sent_count = 0
        self._failed_count = 0
        self._initialize()

    def _initialize(self) -> None:
        if not self._settings.firebase_enabled:
            return
        try:
            credential = None
            if self._settings.firebase_credentials_path:
                credential = credentials.Certificate(self._settings.firebase_credentials_path)
            options = (
                {"projectId": self._settings.firebase_project_id}
                if self._settings.firebase_project_id
                else None
            )
            try:
                firebase_admin.get_app()
            except ValueError:
                firebase_admin.initialize_app(credential, options)
            self._enabled = True
            log.info("firebase_push_initialized")
        except Exception as exc:
            self._last_error = str(exc)
            log.error("firebase_push_initialization_failed", error=str(exc))

    async def register(
        self,
        installation_id: str,
        previous_installation_id: str | None = None,
    ) -> int:
        return await self._store.register(installation_id, previous_installation_id)

    async def send(self, event: NewMessageEvent) -> None:
        installation_ids = await self._store.all()
        if not self._enabled or not installation_ids:
            return
        invalid_installation_ids: set[str] = set()
        for offset in range(0, len(installation_ids), 500):
            batch = installation_ids[offset : offset + 500]
            try:
                response = await asyncio.to_thread(self._send_batch, event, batch)
                self._sent_count += response.success_count
                self._failed_count += response.failure_count
                for index, item in enumerate(response.responses):
                    if not item.success and isinstance(item.exception, messaging.UnregisteredError):
                        invalid_installation_ids.add(batch[index])
            except Exception as exc:
                self._failed_count += len(batch)
                self._last_error = str(exc)
                log.error("firebase_push_send_failed", error=str(exc), installation_count=len(batch))
        await self._store.remove(invalid_installation_ids)

    def _send_batch(
        self,
        event: NewMessageEvent,
        installation_ids: list[str],
    ) -> messaging.BatchResponse:
        data = {
            "type": "new_message",
            "event_id": event.event_id,
            "chat_id": event.chat_id,
            "sender_id": event.sender_id,
            "sender_name": event.sender_name,
            "message": event.message,
            "timestamp": str(event.timestamp),
            "chat_title": event.chat_title or "",
            "reason": event.reason,
        }
        message = messaging.MulticastMessage(
            data=data,
            android=messaging.AndroidConfig(priority="high", ttl=timedelta(seconds=60)),
            fids=installation_ids,
        )
        return messaging.send_each_for_multicast(message)

    async def stats(self) -> dict[str, object]:
        return {
            "enabled": self._enabled,
            "registered_devices": await self._store.count(),
            "sent_count": self._sent_count,
            "failed_count": self._failed_count,
            "last_error": self._last_error,
        }
