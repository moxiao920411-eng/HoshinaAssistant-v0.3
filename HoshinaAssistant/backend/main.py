import os
import re
import asyncio
import unicodedata
from pathlib import Path
from typing import Literal

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, Response
from pydantic import BaseModel, Field

from agents import AgentCoordinator
from agents.base import AgentContext
from omnivoice_service import OmniVoiceError, OmniVoiceService
from ollama_service import (
    OllamaConnectionError,
    OllamaEmptyResponseError,
    OllamaHttpError,
    OllamaService,
    OllamaTimeoutError,
)

app = FastAPI(title="Hoshina Assistant API")
PROJECT_DIR = Path(__file__).resolve().parent.parent
DEFAULT_APK_PATH = PROJECT_DIR / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
cors_origins = [
    origin.strip()
    for origin in os.getenv("CORS_ALLOW_ORIGINS", "*").split(",")
    if origin.strip()
]
app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins or ["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

ollama_service = OllamaService()
agent_coordinator = AgentCoordinator(ollama_service)
omnivoice_service = OmniVoiceService()


class HistoryMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1)


class ChatRequest(BaseModel):
    user_id: int
    message: str
    history: list[HistoryMessage] = Field(default_factory=list)
    user_profile: str | None = None
    client_time: str | None = None
    ai_name: str | None = None
    ai_model: str | None = None
    role_prompt: str | None = None
    voice_mode: bool = False


class ChatResponse(BaseModel):
    response: str
    agents_used: list[str] = Field(default_factory=list)
    mode: str = "single"
    route_reason: str | None = None


class AgentInfo(BaseModel):
    key: str
    name: str
    description: str


class AppVersionInfo(BaseModel):
    latest_version_code: int
    latest_version_name: str
    apk_url: str | None = None
    release_notes: str | None = None


class TtsRequest(BaseModel):
    text: str = Field(min_length=1)


def _has_speakable_text(text: str) -> bool:
    for char in text:
        codepoint = ord(char)
        if char.isalnum():
            return True
        if 0x3400 <= codepoint <= 0x9FFF:
            return True
        if 0x3040 <= codepoint <= 0x30FF:
            return True
        if 0xAC00 <= codepoint <= 0xD7AF:
            return True
    return False


def _clean_tts_text(text: str) -> str:
    normalized = unicodedata.normalize("NFKC", text)
    normalized = normalized.replace("…", "。").replace("～", "，").replace("~", "，")
    chars: list[str] = []
    for char in normalized:
        category = unicodedata.category(char)
        if category.startswith("C"):
            chars.append(" ")
        elif category in {"So", "Sk"}:
            chars.append(" ")
        else:
            chars.append(char)
    cleaned = re.sub(r"\s+", " ", "".join(chars)).strip()
    return cleaned if _has_speakable_text(cleaned) else ""


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest) -> ChatResponse:
    text = request.message.strip()
    if not text:
        raise HTTPException(status_code=400, detail="Message must not be empty.")

    history = [
        {"role": item.role, "content": item.content.strip()}
        for item in request.history
        if item.content.strip()
    ]

    try:
        reply = await agent_coordinator.chat(
            AgentContext(
                message=text,
                history=history,
                user_profile=(request.user_profile or "").strip() or None,
                client_time=(request.client_time or "").strip() or None,
                ai_name=(request.ai_name or "").strip() or None,
                ai_model=(request.ai_model or "").strip() or None,
                role_prompt=(request.role_prompt or "").strip() or None,
                voice_mode=request.voice_mode,
            ),
        )
    except OllamaConnectionError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except OllamaHttpError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except OllamaTimeoutError as exc:
        raise HTTPException(status_code=504, detail=str(exc)) from exc
    except OllamaEmptyResponseError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return ChatResponse(
        response=reply.response,
        agents_used=reply.agents_used,
        mode=reply.mode,
        route_reason=reply.route_reason,
    )


@app.post("/tts")
async def tts(request: TtsRequest) -> Response:
    text = request.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="Text must not be empty.")
    cleaned_text = _clean_tts_text(text)
    if not cleaned_text:
        raise HTTPException(status_code=400, detail="Text has no speakable content.")

    try:
        audio = await asyncio.to_thread(omnivoice_service.synthesize, cleaned_text)
    except OmniVoiceError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return Response(content=audio, media_type="audio/wav")


@app.get("/app-version", response_model=AppVersionInfo)
async def app_version() -> AppVersionInfo:
    return AppVersionInfo(
        latest_version_code=int(os.getenv("HOSHINA_LATEST_VERSION_CODE", "9")),
        latest_version_name=os.getenv("HOSHINA_LATEST_VERSION_NAME", "0.2.5-beta1"),
        apk_url=os.getenv("HOSHINA_APK_URL", "/download/app-debug.apk").strip() or None,
        release_notes=os.getenv("HOSHINA_RELEASE_NOTES", "").strip() or None,
    )


@app.get("/download/app-debug.apk")
async def download_app_debug_apk() -> FileResponse:
    apk_path = Path(os.getenv("HOSHINA_APK_PATH", str(DEFAULT_APK_PATH)))
    if not apk_path.is_file():
        raise HTTPException(status_code=404, detail="APK file not found.")
    return FileResponse(
        apk_path,
        media_type="application/vnd.android.package-archive",
        filename="HoshinaAssistant.apk",
    )


@app.get("/agents", response_model=list[AgentInfo])
async def agents() -> list[AgentInfo]:
    return [
        AgentInfo(
            key="daily_chat",
            name="\u65e5\u5e38\u5c0d\u8a71",
            description="\u9592\u804a\u3001\u751f\u6d3b\u554f\u7b54\u3001\u966a\u4f34\u5f0f\u56de\u8986\u3002",
        ),
        AgentInfo(
            key="deep_reasoning",
            name="\u6df1\u5ea6\u63a8\u7406",
            description="\u8907\u96dc\u5206\u6790\u3001\u7a0b\u5f0f\u9664\u932f\u3001\u67b6\u69cb\u8a2d\u8a08\u3001\u898f\u5283\u8207\u6bd4\u8f03\u3002",
        ),
        AgentInfo(
            key="emotion_analysis",
            name="\u60c5\u611f\u5206\u6790",
            description="\u8fa8\u8b58\u60c5\u7dd2\u3001\u8abf\u6574\u56de\u8986\u8a9e\u6c23\u3001\u8655\u7406\u58d3\u529b\u6216\u4f4e\u843d\u8a0a\u606f\u3002",
        ),
        AgentInfo(
            key="work_handler",
            name="\u5de5\u4f5c\u8655\u7406",
            description="\u5f85\u8fa6\u3001\u63d0\u9192\u3001\u6458\u8981\u3001\u884c\u7a0b\u3001\u4efb\u52d9\u62c6\u89e3\u8207\u5de5\u4f5c\u6d41\u7a0b\u3002",
        ),
        AgentInfo(
            key="multimodal",
            name="\u591a\u6a21\u614b\u5316",
            description="\u5716\u7247\u3001\u8a9e\u97f3\u3001\u5f71\u7247\u3001\u6a94\u6848\u8207\u672a\u4f86\u591a\u6a21\u614b\u80fd\u529b\u5165\u53e3\u3002",
        ),
    ]
