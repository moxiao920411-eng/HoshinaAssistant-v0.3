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
4. For LAN use, connect the phone to the same router as the PC. For mobile data or another Wi-Fi, follow the remote-access section below.

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

## 外網連入電腦後端（Windows）

手機可使用行動網路或其他 Wi-Fi。路徑為：Android → HTTPS Cloudflare Tunnel → 電腦的 127.0.0.1:8001 → Ollama。
不需要開路由器連接埠，也不需要把 Ollama 的 11434 埠公開。

1. 更新此分支的程式，先執行「首次設定.bat」安裝 Python 依賴，確認 Ollama 已安裝且模型已下載。
2. 在 PowerShell 執行 `winget install --id Cloudflare.cloudflared --exact`，安裝後重新開啟終端機。
   亦可依 [Cloudflare 官方下載說明](https://developers.cloudflare.com/tunnel/downloads/) 安裝並將 cloudflared 加到 PATH。
3. 雙擊專案根目錄 `start-hoshina-remote.bat`。它會啟動 Ollama 監控、受金鑰保護的後端與外網通道。
   使用獨立的 8001 埠，因此不會把原本 8000 埠的未驗證後端公開。
4. 視窗會顯示「Backend connection key」及 Cloudflare 的 `https://…trycloudflare.com` 網址。
5. 重新編譯並安裝本分支的 APK（`.\gradlew.bat :app:assembleDebug`），開啟 App → 設定：
   - API 網址：填入該 HTTPS 網址（不加 :8000 或 :8001）。
   - 後端連線金鑰：填入視窗顯示的金鑰。
   - 按儲存。關閉手機 Wi-Fi，使用行動網路測試聊天與語音。
   後端連線失敗時仍可進入設定；公開網址斷線時不會被區網搜尋覆寫。

電腦必須保持開機、連網，後端與通道視窗也必須保持開啟。Ctrl+C 會停止此啟動器建立的後端與通道。
Ollama 監控視窗另外關閉。金鑰保存在 `backend/.remote/api-key.txt`（Git 忽略），不要公開這個檔案。
啟動器的金鑰保護與 App 的金鑰欄位需要同一版本配合；舊 APK 沒有金鑰欄位，會收到 401。

### 暫時網址與固定網址

[Quick Tunnel](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/) 用於測試，不需網域，但每次重新啟動網址會變動，需在 App 更新。
區網掃描無法從外網自動得知這個新網址。

要長期使用固定網址，需建立 Cloudflare Named Tunnel 並綁定自己的網域：
依 [官方設定指南](https://developers.cloudflare.com/tunnel/get-started/) 設定公開主機名稱，
其服務指向 `http://127.0.0.1:8001`。以同一金鑰啟動後端：
先在 PowerShell 設定 `$env:HOSHINA_API_KEY` 與 `$env:HOSHINA_REQUIRE_API_KEY="1"`，
再於 backend 目錄執行 `python -m uvicorn main:app --host 127.0.0.1 --port 8001`。
啟動 Named Tunnel 後，App 填入固定 HTTPS 網址與該金鑰；不要同時使用 Quick Tunnel 啟動器。
網域與 Cloudflare 帳號需由電腦擁有者設定。

### 驗證與排錯

- 外網網址加 `/health` 應回傳 `{"service":"hoshina","status":"ok"}`。此檢查不代表 Ollama 模型已就緒。
- 不帶金鑰請求 `/agents` 應得到 401；App 正確填入金鑰後應能聊天。
- 401：金鑰未填或不一致。
- 502／503：檢查電腦上的通道、後端與 Ollama 視窗；長時間模型推理也可能遇到通道逾時。
- cloudflared 未找到：安裝後重開終端機，或將 cloudflared 所在目錄加入 PATH。
- 8001 被占用：關閉上一次外網啟動器再重試。
- Quick Tunnel 無法啟動：檢查使用者 .cloudflared 目錄是否有 config.yaml／config.yml；既有設定請使用 Named Tunnel，或依官方說明處理。
- 本機模式未設定 `HOSHINA_API_KEY` 時，原本區網使用方式保留。外網啟動器會強制要求金鑰。
- `/health`、`/app-version` 與 `/download/app-debug.apk` 是公開路徑，以支援連線檢查與瀏覽器更新；
  聊天、TTS、agents、API 文件及其他路徑皆受金鑰保護（啟用金鑰時）。
