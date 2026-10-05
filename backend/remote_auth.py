"""Optional API-key protection; mandatory in the remote launcher."""
import hmac
import os


def get_api_key() -> str:
    key = os.getenv("HOSHINA_API_KEY", "").strip()
    if os.getenv("HOSHINA_REQUIRE_API_KEY") == "1" and len(key) < 32:
        raise RuntimeError("Remote access requires HOSHINA_API_KEY (at least 32 characters).")
    return key


class ApiKeyMiddleware:
    # Version metadata and the release APK remain public for the Android browser updater.
    PUBLIC_PATHS = {"/health", "/app-version", "/download/app-debug.apk"}

    def __init__(self, app, api_key: str):
        self.app = app
        self.api_key = api_key

    async def __call__(self, scope, receive, send):
        if scope["type"] == "http" and self.api_key and scope["path"] not in self.PUBLIC_PATHS:
            headers = dict(scope.get("headers", []))
            supplied = headers.get(b"x-hoshina-key", b"")
            if not hmac.compare_digest(supplied, self.api_key.encode("utf-8")):
                body = b'{"detail":"Invalid or missing backend API key."}'
                await send({"type": "http.response.start", "status": 401,
                            "headers": [(b"content-type", b"application/json"),
                                        (b"content-length", str(len(body)).encode())]})
                await send({"type": "http.response.body", "body": body})
                return
        await self.app(scope, receive, send)
