# Hoshina Assistant

Android AI voice assistant (Kotlin + Jetpack Compose) with a FastAPI + Ollama backend.

## Architecture

- **UI**: Jetpack Compose (`ChatScreen`, `SettingsScreen`)
- **MVVM**: `ChatViewModel`, `SettingsViewModel`
- **Data**: `ChatRepository` → Retrofit → `POST /chat`
- **Voice**: `SpeechRecognizer` (STT), `TextToSpeech` (TTS)
- **Settings**: DataStore API base URL
- **Local memory**: Room chat history + DataStore personal profile (device only)

## Prerequisites

- Android Studio Ladybug (2024.2+) or newer
- JDK 17
- Python 3.10+
- [Ollama](https://ollama.com/) with model `deepseek-r1:8b`

## 1. Start backend

Windows 第一次使用時，先雙擊專案根目錄的 `首次設定.bat` 安裝 backend 依賴；之後雙擊 `快速啟動.bat`，它會自動啟動 Ollama 與 FastAPI backend。

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
ollama pull deepseek-r1:8b
ollama serve
```

In another terminal:

```powershell
cd backend
.\.venv\Scripts\Activate.ps1
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

API: `POST http://localhost:8000/chat`

```json
{
  "user_id": 1,
  "message": "hello",
  "history": [
    { "role": "user", "content": "早安" },
    { "role": "assistant", "content": "早安，今天過得好嗎？" }
  ]
}
```

`history` is built from **on-device** chat history; the app sends prior turns automatically (up to 40 messages on the server).

Response:

```json
{ "response": "AI reply" }
```

## 2. Run Android app

1. Open the project root in **Android Studio**.
2. Let Gradle sync. The project now includes a Gradle 8.9 wrapper, so you can also build from PowerShell with `.\gradlew.bat :app:assembleDebug`.
3. Ensure Android Studio has an Android SDK and NDK installed, and that `local.properties` points to that SDK.
4. Use an **emulator** or a **physical device** on the same Wi‑Fi as your PC.

### API URL

| Device | Default URL in app |
|--------|-------------------|
| Emulator | `http://10.0.2.2:8000/` |
| Physical phone | `http://YOUR_PC_LAN_IP:8000/` |

Change it in the app: **Settings** (gear icon) → save your base URL.

Find your PC IP: `ipconfig` (Windows) → IPv4 address.

## 3. Features

- **Text chat**: type → Send → AI reply (multi-turn memory, persisted on device)
- **Voice**: hold mic → speak → release → STT → API → TTS reads reply
- **Settings**: API URL + **本機個人記憶** (nickname, preferences)
- **Chat history**: auto-saved on device, restored after app restart

## Project layout

```
app/src/main/kotlin/com/hoshina/assistant/
├── MainActivity.kt
├── HoshinaApplication.kt
├── data/
│   ├── local/SettingsRepository.kt
│   ├── remote/ApiService.kt, RetrofitProvider.kt
│   └── repository/ChatRepository*.kt
├── domain/model/ChatMessage.kt
├── voice/SpeechToTextManager.kt, TextToSpeechManager.kt
└── ui/chat, ui/settings, ui/navigation, ui/theme
backend/
├── main.py
└── ollama_service.py
```

## Troubleshooting

- **Connection refused**: backend not running or wrong URL in Settings.
- **503 from API**: Ollama not running (`ollama serve`).
- **No speech**: grant microphone permission; use a device/emulator with Google speech services.
- **Cleartext HTTP**: enabled for local dev in `network_security_config.xml`.

## API inspection

The browser version is not included in this Android repository. Use the Android app or inspect the backend API directly.

To inspect the API:

1. Start the FastAPI backend.
2. Open `http://localhost:8000/docs` in a browser.
