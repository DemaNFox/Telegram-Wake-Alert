import asyncio
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from app.domain.events import NewMessageEvent
from app.services.push_notifications import PushInstallationStore


class NewMessageEventTests(unittest.TestCase):
    def test_event_id_is_stable_and_in_payload(self) -> None:
        event = NewMessageEvent(
            chat_id="chat",
            sender_id="sender",
            sender_name="Sender",
            message="Important",
            timestamp=123,
        )

        self.assertEqual(event.event_id, event.event_id)
        self.assertEqual(event.event_id, event.to_payload()["event_id"])
        self.assertEqual(32, len(event.event_id))

    def test_event_id_changes_with_message(self) -> None:
        first = NewMessageEvent("chat", "sender", "Sender", "One", 123)
        second = NewMessageEvent("chat", "sender", "Sender", "Two", 123)

        self.assertNotEqual(first.event_id, second.event_id)


class PushInstallationStoreTests(unittest.TestCase):
    def test_registration_is_persistent_and_replaces_previous_id(self) -> None:
        with TemporaryDirectory() as directory:
            path = Path(directory) / "fcm_installations.json"
            store = PushInstallationStore(str(path))

            count = asyncio.run(store.register("installation-new", "installation-old"))
            reloaded = PushInstallationStore(str(path))

            self.assertEqual(1, count)
            self.assertEqual(["installation-new"], asyncio.run(reloaded.all()))


if __name__ == "__main__":
    unittest.main()
