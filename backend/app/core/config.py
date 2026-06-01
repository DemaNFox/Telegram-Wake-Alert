from functools import lru_cache

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_env: str = Field(default="production", alias="APP_ENV")
    log_level: str = Field(default="INFO", alias="LOG_LEVEL")
    host: str = Field(default="0.0.0.0", alias="HOST")
    port: int = Field(default=8000, alias="PORT")

    telegram_api_id: int = Field(alias="TELEGRAM_API_ID")
    telegram_api_hash: str = Field(alias="TELEGRAM_API_HASH")
    telegram_session_name: str = Field(default="telegram_alarm", alias="TELEGRAM_SESSION_NAME")
    telegram_allowed_sender_ids: set[int] = Field(default_factory=set, alias="TELEGRAM_ALLOWED_SENDER_IDS")
    telegram_blocked_sender_ids: set[int] = Field(default_factory=set, alias="TELEGRAM_BLOCKED_SENDER_IDS")

    ws_auth_token: str = Field(alias="WS_AUTH_TOKEN")
    heartbeat_interval_seconds: int = Field(default=20, alias="HEARTBEAT_INTERVAL_SECONDS")
    telegram_reconnect_min_seconds: int = Field(default=2, alias="TELEGRAM_RECONNECT_MIN_SECONDS")
    telegram_reconnect_max_seconds: int = Field(default=60, alias="TELEGRAM_RECONNECT_MAX_SECONDS")

    @field_validator("telegram_allowed_sender_ids", "telegram_blocked_sender_ids", mode="before")
    @classmethod
    def parse_sender_ids(cls, value: object) -> set[int]:
        if value is None or value == "":
            return set()
        if isinstance(value, set):
            return {int(item) for item in value}
        if isinstance(value, list | tuple):
            return {int(item) for item in value}
        if isinstance(value, str):
            return {int(item.strip()) for item in value.split(",") if item.strip()}
        raise ValueError("Sender IDs must be a comma-separated string")


@lru_cache
def get_settings() -> Settings:
    return Settings()
