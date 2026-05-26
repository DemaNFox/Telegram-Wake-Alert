# Telegram Alarm Notifier

Production-oriented two-part system for instant Android alarm alerts when a Telegram personal account receives a new private incoming message.

## Architecture

- `backend/`: Python 3.12, FastAPI, Telethon MTProto user session, WebSocket fanout, token auth, heartbeat, reconnect loop, graceful shutdown.
- `android/`: Native Kotlin app, Jetpack Compose, Hilt, MVVM, DataStore, OkHttp WebSocket, foreground sticky service, boot receiver, full-screen alarm activity, WakeLock, alarm audio stream.

The backend uses MTProto user login only. It does not use Telegram Bot API.

## Backend Setup

1. Create Telegram API credentials at `https://my.telegram.org`.
2. Configure environment:

```powershell
cd backend
Copy-Item .env.example .env
notepad .env
```

3. Install and authorize the user session:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python -m app.telegram_login
```

4. Run:

```powershell
uvicorn app.main:create_app --factory --host 0.0.0.0 --port 8000
```

Health check: `http://localhost:8000/health`

WebSocket endpoint:

```text
ws://<backend-host>:8000/ws?token=<WS_AUTH_TOKEN>&client_id=<android-device-id>
```

Docker:

```powershell
cd backend
docker compose up --build
```

Authorize the Telegram session locally before running the container, or mount an already authorized `backend/sessions` directory.

## Android Setup

Open `android/` in Android Studio. The project targets SDK 36 and requires Android 10+.

In the app settings screen:

- Backend URL: `ws://<backend-ip>:8000/ws`
- Auth token: same value as `WS_AUTH_TOKEN`
- Enable alerts: on
- Auto reconnect: on

For emulator against local backend use:

```text
ws://10.0.2.2:8000/ws
```

For a real device, use the machine LAN IP. For production, put the backend behind TLS and use `wss://`.

## Android Reliability Checklist

Grant these runtime/system permissions or settings where Android asks:

- Notifications
- Full-screen alarms
- Battery optimization exemption
- Autostart/background execution permissions on OEM Android builds where available

The foreground service shows `Telegram Alarm Bot Active`, reconnects WebSocket automatically, restarts after process death via `START_STICKY`, and starts after reboot through `BOOT_COMPLETED`.

## Security Notes

- Telegram credentials stay on the backend only in `.env`.
- Telegram authorization is stored as a Telethon session file under `backend/sessions/`; protect that directory.
- Android authenticates to backend through a long random WebSocket token.
- Use `wss://` and a reverse proxy with TLS outside trusted local networks.

## Message Event

Backend broadcasts:

```json
{
  "type": "new_message",
  "chat_id": "...",
  "sender_id": "...",
  "sender_name": "...",
  "message": "...",
  "timestamp": 123456789
}
```

Only incoming private Telegram user messages received after listener startup are emitted. Groups, channels, bots, and old messages are ignored.
