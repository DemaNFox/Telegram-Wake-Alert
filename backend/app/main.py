from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
import time

from fastapi import FastAPI, HTTPException, Query, status

from app.api.websocket import router as websocket_router
from app.core.config import get_settings
from app.core.logging import configure_logging
from app.domain.events import NewMessageEvent
from app.services.telegram_listener import TelegramListenerService
from app.services.websocket_manager import WebSocketManager


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = get_settings()
    configure_logging(settings.log_level)

    ws_manager = WebSocketManager(settings.heartbeat_interval_seconds)
    app.state.ws_manager = ws_manager

    async def publish(event: NewMessageEvent) -> None:
        await ws_manager.broadcast(event.to_payload())

    telegram_listener = TelegramListenerService(settings, publish)
    app.state.telegram_listener = telegram_listener

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
        return {
            "status": "ok",
            "timestamp": int(time.time()),
            "telegram": telegram_listener.stats(),
            "websocket": await ws_manager.stats(),
        }

    @app.post("/test-event")
    async def test_event(token: str = Query(...)) -> dict[str, str]:
        settings = get_settings()
        if token != settings.ws_auth_token:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
        ws_manager: WebSocketManager = app.state.ws_manager
        await ws_manager.broadcast(
            NewMessageEvent(
                chat_id="backend-test",
                sender_id="backend-test",
                sender_name="Backend Test",
                message="Backend test alarm event",
                timestamp=int(time.time()),
            ).to_payload()
        )
        return {"status": "sent"}

    return app


app = create_app()
