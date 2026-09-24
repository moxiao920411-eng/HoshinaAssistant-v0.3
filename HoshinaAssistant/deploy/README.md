# Hoshina Assistant Deployment

The current default model provider is Ollama.

## Local Android Testing

Start Ollama:

```bash
ollama serve
```

Pull the default model:

```bash
ollama pull deepseek-r1:8b
```

Start the FastAPI backend:

```bash
cd backend
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Android backend URL:

- Emulator: `http://10.0.2.2:8000/`
- Physical phone: `http://YOUR_PC_LAN_IP:8000/`

The backend calls Ollama at:

```text
http://localhost:11434/api/chat
```

## Docker Ollama Stack

Use this when you want Docker to run both backend and Ollama:

```bash
cd deploy
docker compose -f docker-compose.ollama.yml up -d --build
```

The backend is available at `http://localhost:8000`. The Android emulator uses
`http://10.0.2.2:8000/`.

## Optional Cloud Provider

`docker-compose.openai-compatible.yml` is kept only as an optional alternative. Do not use it for the current Ollama setup unless you intentionally switch back to cloud models.
