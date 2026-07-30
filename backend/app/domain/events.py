from dataclasses import asdict, dataclass
import hashlib


@dataclass(frozen=True)
class NewMessageEvent:
    chat_id: str
    sender_id: str
    sender_name: str
    message: str
    timestamp: int
    chat_title: str | None = None
    reason: str = "private_user"

    @property
    def event_id(self) -> str:
        source = "\0".join(
            [self.chat_id, self.sender_id, str(self.timestamp), self.message]
        )
        return hashlib.sha256(source.encode("utf-8")).hexdigest()[:32]

    def to_payload(self) -> dict[str, object]:
        return {"type": "new_message", "event_id": self.event_id, **asdict(self)}
