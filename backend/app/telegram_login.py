import asyncio

from app.core.config import get_settings
from telethon import TelegramClient


async def main() -> None:
    settings = get_settings()
    client = TelegramClient(
        f"sessions/{settings.telegram_session_name}",
        settings.telegram_api_id,
        settings.telegram_api_hash,
    )
    await client.start()
    me = await client.get_me()
    print(f"Authorized Telegram user session for {me.id}")
    await client.disconnect()


if __name__ == "__main__":
    asyncio.run(main())
