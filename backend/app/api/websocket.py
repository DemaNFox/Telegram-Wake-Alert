from uuid import uuid4

from fastapi import APIRouter, Depends, Query, WebSocket, WebSocketException, status

from app.core.config import Settings, get_settings
from app.services.websocket_manager import WebSocketManager

router = APIRouter()


def get_ws_manager(websocket: WebSocket) -> WebSocketManager:
    return websocket.app.state.ws_manager


@router.websocket("/ws")
async def websocket_endpoint(
    websocket: WebSocket,
    token: str = Query(...),
    client_id: str | None = Query(default=None),
    settings: Settings = Depends(get_settings),
    manager: WebSocketManager = Depends(get_ws_manager),
) -> None:
    if token != settings.ws_auth_token:
        raise WebSocketException(code=status.WS_1008_POLICY_VIOLATION)
    connection_id = client_id or str(uuid4())
    await manager.connect(websocket, connection_id)
    await manager.receive_loop(connection_id)
