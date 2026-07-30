from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
import asyncio
import time

from fastapi import FastAPI, HTTPException, Query, status
from pydantic import BaseModel, Field

from app.api.websocket import router as websocket_router
from app.core.config import get_settings
from app.core.logging import configure_logging
from app.domain.events import NewMessageEvent
from app.services.telegram_listener import TelegramListenerService
from app.services.push_notifications import FirebasePushService
from app.services.websocket_manager import WebSocketManager


class PushRegistrationRequest(BaseModel):
    installation_id: str = Field(min_length=10, max_length=4096)
    previous_installation_id: str | None = Field(default=None, max_length=4096)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = get_settings()
    configure_logging(settings.log_level)

    ws_manager = WebSocketManager(settings.heartbeat_interval_seconds)
    app.state.ws_manager = ws_manager
    push_service = FirebasePushService(settings)
    app.state.push_service = push_service

    async def publish(event: NewMessageEvent) -> None:
        await asyncio.gather(
            ws_manager.broadcast(event.to_payload()),
            push_service.send(event),
        )

    telegram_listener = TelegramListenerService(settings, publish)
    app.state.telegram_listener = telegram_listener
    app.state.publish_event = publish

    await ws_manager.start()
    await telegram_listener.start()
    try:
        yield
    finally:
        await telegram_listener.stop()
        await ws_manager.stop()


def create_app() -> FastAPI:
    app = FastAPI(title="Telegram Alarm Backend", version="1.0.0", lifespan=lifespan)
    app.include_router(websocket_router)

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/status")
    async def backend_status(token: str = Query(...)) -> dict[str, object]:
        settings = get_settings()
        if token != settings.ws_auth_token:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
        ws_manager: WebSocketManager = app.state.ws_manager
        telegram_listener: TelegramListenerService = app.state.telegram_listener
        push_service: FirebasePushService = app.state.push_service
        return {
            "status": "ok",
            "timestamp": int(time.time()),
            "telegram": telegram_listener.stats(),
            "websocket": await ws_manager.stats(),
            "push": await push_service.stats(),
        }

    @app.post("/test-event")
    async def test_event(token: str = Query(...)) -> dict[str, str]:
        settings = get_settings()
        if token != settings.ws_auth_token:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
        await app.state.publish_event(
            NewMessageEvent(
                chat_id="backend-test",
                sender_id="backend-test",
                sender_name="Backend Test",
                message="Backend test alarm event",
                timestamp=int(time.time()),
            )
        )
        return {"status": "sent"}

    @app.post("/push/register")
    async def register_push(
        request: PushRegistrationRequest,
        token: str = Query(...),
    ) -> dict[str, object]:
        settings = get_settings()
        if token != settings.ws_auth_token:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
        push_service: FirebasePushService = app.state.push_service
        count = await push_service.register(
            request.installation_id.strip(),
            request.previous_installation_id.strip()
            if request.previous_installation_id
            else None,
        )
        push_stats = await push_service.stats()
        if not push_stats["enabled"]:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Firebase push is not enabled on the backend",
            )
        return {"status": "registered", "registered_devices": count}

    @app.get("/people/recent")
    async def recent_people(token: str = Query(...), limit: int = Query(default=50, ge=1, le=100)) -> dict[str, object]:
        settings = get_settings()
        if token != settings.ws_auth_token:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
        telegram_listener: TelegramListenerService = app.state.telegram_listener
        return {"people": await telegram_listener.recent_people(limit)}

    @app.get("/groups/recent")
    async def recent_groups(token: str = Query(...), limit: int = Query(default=100, ge=1, le=200)) -> dict[str, object]:
        settings = get_settings()
        if token != settings.ws_auth_token:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
        telegram_listener: TelegramListenerService = app.state.telegram_listener
        return {"groups": await telegram_listener.recent_groups(limit)}

    return app


app = create_app()
