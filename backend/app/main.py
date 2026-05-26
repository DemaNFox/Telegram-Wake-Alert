from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

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

    return app


app = create_app()
