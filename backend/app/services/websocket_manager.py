import asyncio
from dataclasses import dataclass

import structlog
from fastapi import WebSocket
from starlette.websockets import WebSocketState

log = structlog.get_logger(__name__)


@dataclass
class ClientConnection:
    websocket: WebSocket
    client_id: str


class WebSocketManager:
    def __init__(self, heartbeat_interval_seconds: int) -> None:
        self._clients: dict[str, ClientConnection] = {}
        self._lock = asyncio.Lock()
        self._heartbeat_interval_seconds = heartbeat_interval_seconds
        self._heartbeat_task: asyncio.Task[None] | None = None
        self._stopping = asyncio.Event()

    async def start(self) -> None:
        self._stopping.clear()
        self._heartbeat_task = asyncio.create_task(self._heartbeat_loop(), name="websocket-heartbeat")

    async def stop(self) -> None:
        self._stopping.set()
        if self._heartbeat_task:
            self._heartbeat_task.cancel()
            await asyncio.gather(self._heartbeat_task, return_exceptions=True)
        async with self._lock:
            clients = list(self._clients.values())
            self._clients.clear()
        await asyncio.gather(*(self._close_client(client) for client in clients), return_exceptions=True)

    async def connect(self, websocket: WebSocket, client_id: str) -> None:
        await websocket.accept()
        async with self._lock:
            old_client = self._clients.pop(client_id, None)
            self._clients[client_id] = ClientConnection(websocket=websocket, client_id=client_id)
        if old_client:
            await self._close_client(old_client)
        await websocket.send_json({"type": "connected", "client_id": client_id})
        log.info("websocket_client_connected", client_id=client_id)

    async def disconnect(self, client_id: str) -> None:
        async with self._lock:
            client = self._clients.pop(client_id, None)
        if client:
            await self._close_client(client)
            log.info("websocket_client_disconnected", client_id=client_id)

    async def broadcast(self, payload: dict[str, object]) -> None:
        async with self._lock:
            clients = list(self._clients.values())
        failed: list[str] = []
        for client in clients:
            try:
                await client.websocket.send_json(payload)
            except Exception as exc:
                log.warning("websocket_send_failed", client_id=client.client_id, error=str(exc))
                failed.append(client.client_id)
        for client_id in failed:
            await self.disconnect(client_id)

    async def receive_loop(self, client_id: str) -> None:
        async with self._lock:
            client = self._clients.get(client_id)
        if not client:
            return
        try:
            while not self._stopping.is_set():
                data = await client.websocket.receive_json()
                if data.get("type") == "ping":
                    await client.websocket.send_json({"type": "pong"})
        except Exception as exc:
            log.info("websocket_receive_loop_closed", client_id=client_id, error=str(exc))
        finally:
            await self.disconnect(client_id)

    async def _heartbeat_loop(self) -> None:
        while not self._stopping.is_set():
            await asyncio.sleep(self._heartbeat_interval_seconds)
            await self.broadcast({"type": "heartbeat"})

    @staticmethod
    async def _close_client(client: ClientConnection) -> None:
        if client.websocket.client_state == WebSocketState.CONNECTED:
            await client.websocket.close()
