import asyncio
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from remote_auth import ApiKeyMiddleware, get_api_key
from remote_launcher import load_or_create_key


class RemoteAccessTests(unittest.TestCase):
    def request(self, path, key=b"", configured="a" * 43):
        messages = []
        async def app(scope, receive, send):
            await send({"type": "http.response.start", "status": 200, "headers": []})
            await send({"type": "http.response.body", "body": b"ok"})
        async def receive():
            return {"type": "http.request", "body": b""}
        async def send(message):
            messages.append(message)
        scope = {"type": "http", "path": path, "headers": [(b"x-hoshina-key", key)]}
        asyncio.run(ApiKeyMiddleware(app, configured)(scope, receive, send))
        return messages[0]["status"]

    def test_protected_endpoints_reject_missing_and_wrong_keys(self):
        for path in ("/chat", "/tts", "/agents", "/docs", "/openapi.json", "/other"):
            for key in (b"", b"wrong", b"\xff"):
                with self.subTest(path=path, key=key):
                    self.assertEqual(self.request(path, key), 401)

    def test_correct_key_passes(self):
        self.assertEqual(self.request("/chat", b"a" * 43), 200)

    def test_public_health_and_update_routes(self):
        for path in ("/health", "/app-version", "/download/app-debug.apk"):
            self.assertEqual(self.request(path), 200)

    def test_local_mode_compatibility(self):
        self.assertEqual(self.request("/chat", configured=""), 200)

    def test_remote_mode_requires_strong_key(self):
        for key in ("", "short"):
            with patch.dict(os.environ, {"HOSHINA_REQUIRE_API_KEY": "1", "HOSHINA_API_KEY": key}):
                with self.assertRaises(RuntimeError):
                    get_api_key()

    def test_remote_mode_accepts_key(self):
        with patch.dict(os.environ, {"HOSHINA_REQUIRE_API_KEY": "1", "HOSHINA_API_KEY": "a" * 43}):
            self.assertEqual(get_api_key(), "a" * 43)

    def test_generated_key_persists_across_restarts(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary) / ".remote"
            key = load_or_create_key(directory)
            self.assertGreaterEqual(len(key), 32)
            self.assertEqual(load_or_create_key(directory), key)
            if os.name != "nt":
                self.assertEqual((directory / "api-key.txt").stat().st_mode & 0o777, 0o600)

    def test_corrupt_saved_key_is_not_silently_replaced(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "api-key.txt").write_text("short")
            with self.assertRaises(RuntimeError):
                load_or_create_key(directory)


if __name__ == "__main__":
    unittest.main()
