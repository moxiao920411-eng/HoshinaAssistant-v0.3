"""Run a protected loopback backend and an outbound HTTPS tunnel."""
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request

BACKEND_DIR = Path(__file__).resolve().parent
PROJECT_DIR = BACKEND_DIR.parent
PORT = 8001
BASE_URL = f"http://127.0.0.1:{PORT}"


def load_or_create_key(directory: Path) -> str:
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / "api-key.txt"
    if path.exists():
        key = path.read_text(encoding="utf-8").strip()
        if len(key) < 32:
            raise RuntimeError("Invalid saved API key. Delete backend/.remote/api-key.txt and retry.")
        return key
    key = secrets.token_urlsafe(32)
    # Exclusive creation avoids changing the key if two launchers start together.
    with path.open("x", encoding="utf-8") as output:
        output.write(key)
    if os.name != "nt":
        path.chmod(0o600)
    return key


def backend_ready(key: str) -> bool:
    try:
        request = urllib.request.Request(BASE_URL + "/agents", headers={"X-Hoshina-Key": key})
        with urllib.request.urlopen(request, timeout=1) as response:
            return response.status == 200 and isinstance(json.load(response), list)
    except (OSError, ValueError):
        return False


def stop_process(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def main() -> int:
    cloudflared = shutil.which("cloudflared")
    if not cloudflared:
        print("Install cloudflared first, then rerun this launcher:")
        print("winget install --id Cloudflare.cloudflared --exact")
        print("Reopen the terminal after installation.")
        return 1

    # Never publish an existing process whose authentication is unknown.
    with socket.socket() as check:
        try:
            check.bind(("127.0.0.1", PORT))
        except OSError:
            print(f"Port {PORT} is occupied. Close the previous remote launcher and retry.")
            return 1

    key = load_or_create_key(BACKEND_DIR / ".remote")
    environment = os.environ.copy()
    environment["HOSHINA_API_KEY"] = key
    environment["HOSHINA_REQUIRE_API_KEY"] = "1"
    if os.name == "nt":
        monitor = PROJECT_DIR / "monitor-ollama.bat"
        if monitor.exists():
            # A separate console maintains the existing Ollama monitoring behavior.
            subprocess.Popen(["cmd.exe", "/d", "/c", "call", str(monitor)],
                             cwd=PROJECT_DIR, creationflags=subprocess.CREATE_NEW_CONSOLE)

    backend = subprocess.Popen(
        [sys.executable, "-m", "uvicorn", "main:app", "--host", "127.0.0.1", "--port", str(PORT)],
        cwd=BACKEND_DIR, env=environment,
    )
    tunnel = None
    try:
        deadline = time.monotonic() + 60
        while not backend_ready(key):
            if backend.poll() is not None or time.monotonic() >= deadline:
                raise RuntimeError("Protected backend did not become ready. Check the error above.")
            time.sleep(1)

        # Prove that the new process actually rejects unauthenticated requests.
        try:
            urllib.request.urlopen(BASE_URL + "/agents", timeout=2).close()
        except urllib.error.HTTPError as error:
            if error.code != 401:
                raise RuntimeError("Unexpected authentication response.") from error
        else:
            raise RuntimeError("Backend has no API-key protection; tunnel was not started.")

        print("\nAndroid Settings: paste the HTTPS URL shown by cloudflared below.")
        print(f"Backend connection key: {key}")
        print("Save both fields. Test with phone Wi-Fi OFF.")
        print("Keep this window open. Ctrl+C stops the backend and tunnel.")
        print("Quick Tunnel URLs change after restart; update the URL in the app.\n", flush=True)
        tunnel = subprocess.Popen([cloudflared, "tunnel", "--url", BASE_URL])
        while tunnel.poll() is None:
            if backend.poll() is not None:
                raise RuntimeError("Backend exited. Closing tunnel.")
            time.sleep(1)
        return tunnel.returncode or 0
    finally:
        if tunnel is not None:
            stop_process(tunnel)
        stop_process(backend)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nRemote access stopped.")
    except Exception as error:
        print(f"Remote startup failed: {error}", file=sys.stderr)
        sys.exit(1)
