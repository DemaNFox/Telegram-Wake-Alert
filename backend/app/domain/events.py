from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class NewMessageEvent:
    chat_id: str
    sender_id: str
    sender_name: str
    message: str
    timestamp: int

    def to_payload(self) -> dict[str, object]:
        return {"type": "new_message", **asdict(self)}
