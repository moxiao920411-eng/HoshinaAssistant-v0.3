import os
import re
import logging

import httpx

from hoshina_system_prompt import HOSHINA_SYSTEM_PROMPT

AI_PROVIDER = os.getenv("AI_PROVIDER", "ollama").strip().lower()
OLLAMA_CHAT_URL = os.getenv("OLLAMA_CHAT_URL", "http://localhost:11434/api/chat")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "deepseek-r1:8b")
OPENAI_COMPATIBLE_BASE_URL = os.getenv("OPENAI_COMPATIBLE_BASE_URL", "").rstrip("/")
OPENAI_COMPATIBLE_API_KEY = os.getenv("OPENAI_COMPATIBLE_API_KEY", "")
OPENAI_COMPATIBLE_MODEL = os.getenv("OPENAI_COMPATIBLE_MODEL", "openai/gpt-4.1-mini")
MODEL_TIMEOUT_SECONDS = float(os.getenv("MODEL_TIMEOUT_SECONDS", "120"))
OLLAMA_NUM_PREDICT = int(os.getenv("OLLAMA_NUM_PREDICT", "256"))
MAX_HISTORY_MESSAGES = 40
LOGGER = logging.getLogger(__name__)


def _build_thinking_pattern() -> re.Pattern[str]:
    open_tag = "<" + "think" + ">"
    close_tag = "</" + "think" + ">"
    return re.compile(
        re.escape(open_tag) + r"[\s\S]*?" + re.escape(close_tag),
        re.IGNORECASE,
    )


_THINKING_PATTERN = _build_thinking_pattern()


class OllamaError(Exception):
    """Base error for model provider failures."""


class OllamaConnectionError(OllamaError):
    pass


class OllamaHttpError(OllamaError):
    def __init__(self, status_code: int, body: str) -> None:
        self.status_code = status_code
        self.body = body
        super().__init__(f"Model provider returned {status_code}: {body}")


class OllamaTimeoutError(OllamaError):
    pass


class OllamaEmptyResponseError(OllamaError):
    pass


class OllamaService:
    def __init__(
        self,
        timeout_seconds: float = MODEL_TIMEOUT_SECONDS,
        system_prompt: str = HOSHINA_SYSTEM_PROMPT,
    ) -> None:
        self._timeout_seconds = timeout_seconds
        self._system_prompt = system_prompt
        self._provider = AI_PROVIDER

    async def chat(
        self,
        user_message: str,
        history: list[dict[str, str]] | None = None,
        user_profile: str | None = None,
        client_time: str | None = None,
        ai_name: str | None = None,
        ai_model: str | None = None,
        role_prompt: str | None = None,
    ) -> str:
        text = user_message.strip()
        if not text:
            raise ValueError("Prompt must not be empty.")

        messages = self._build_messages(
            text,
            history or [],
            user_profile=user_profile,
            client_time=client_time,
            ai_name=ai_name,
            role_prompt=role_prompt,
        )

        try:
            if self._provider == "ollama":
                raw_reply = await self._chat_ollama(messages, ai_model)
            else:
                raw_reply = await self._chat_openai_compatible(messages, ai_model)
        except httpx.ConnectError as exc:
            raise OllamaConnectionError(
                "The model provider is not reachable. Check the provider URL."
            ) from exc
        except httpx.HTTPStatusError as exc:
            raise OllamaHttpError(exc.response.status_code, exc.response.text) from exc
        except httpx.TimeoutException as exc:
            raise OllamaTimeoutError("Model provider request timed out.") from exc

        reply = _strip_thinking(raw_reply).strip()
        if not reply:
            raise OllamaEmptyResponseError("Model provider returned an empty response.")

        return reply

    async def _chat_ollama(
        self,
        messages: list[dict[str, str]],
        model_override: str | None = None,
    ) -> str:
        model = (model_override or "").strip() or OLLAMA_MODEL
        async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
            response = await client.post(
                OLLAMA_CHAT_URL,
                json={
                    "model": model,
                    "messages": messages,
                    "stream": False,
                    "think": False,
                    "options": {"num_predict": OLLAMA_NUM_PREDICT},
                },
            )

            # Android clients can retain an old model name in local settings.
            # Recover from Ollama's model-not-found response by using the
            # configured local default instead of surfacing a chat 502.
            if response.status_code == 404 and model != OLLAMA_MODEL:
                LOGGER.warning(
                    "Ollama model %r was not found; retrying with default model %r",
                    model,
                    OLLAMA_MODEL,
                )
                response = await client.post(
                    OLLAMA_CHAT_URL,
                    json={
                        "model": OLLAMA_MODEL,
                        "messages": messages,
                        "stream": False,
                        "think": False,
                        "options": {"num_predict": OLLAMA_NUM_PREDICT},
                    },
                )

            if response.is_error:
                LOGGER.error(
                    "Ollama chat failed: status=%s model=%r body=%s",
                    response.status_code,
                    model,
                    response.text[:1000],
                )
            response.raise_for_status()

        data = response.json()
        message = data.get("message") or {}
        return (message.get("content") or data.get("response") or "").strip()

    async def _chat_openai_compatible(
        self,
        messages: list[dict[str, str]],
        model_override: str | None = None,
    ) -> str:
        if not OPENAI_COMPATIBLE_BASE_URL:
            raise OllamaConnectionError("OPENAI_COMPATIBLE_BASE_URL is not configured.")

        headers = {"Content-Type": "application/json"}
        if OPENAI_COMPATIBLE_API_KEY:
            headers["Authorization"] = f"Bearer {OPENAI_COMPATIBLE_API_KEY}"

        async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
            response = await client.post(
                f"{OPENAI_COMPATIBLE_BASE_URL}/v1/chat/completions",
                headers=headers,
                json={
                    "model": (model_override or "").strip() or OPENAI_COMPATIBLE_MODEL,
                    "messages": messages,
                    "stream": False,
                },
            )
            response.raise_for_status()

        data = response.json()
        choices = data.get("choices") or []
        if not choices:
            return ""
        message = choices[0].get("message") or {}
        return (message.get("content") or "").strip()

    def _build_messages(
        self,
        user_message: str,
        history: list[dict[str, str]],
        user_profile: str | None = None,
        client_time: str | None = None,
        ai_name: str | None = None,
        role_prompt: str | None = None,
    ) -> list[dict[str, str]]:
        system_content = (role_prompt or "").strip() or self._system_prompt

        name = (ai_name or "").strip()
        if name:
            system_content += (
                f"\n\nYour assistant name is {name}. "
                "Use this name consistently when referring to yourself."
            )

        profile = (user_profile or "").strip()
        if profile:
            system_content += (
                "\n\nKnown user facts and preferences. Use them only when helpful:\n"
                + profile
            )

        device_time = (client_time or "").strip()
        if device_time:
            system_content += f"\n\nClient time:\n{device_time}"

        system_content += (
            "\n\nIf the user asks for a reminder and you can infer a time, include a hidden tag "
            "at the end of the reply in this exact format: [REMINDER:HH:mm:message]. "
            "Use 24-hour time."
        )

        messages: list[dict[str, str]] = [{"role": "system", "content": system_content}]

        for item in history[-MAX_HISTORY_MESSAGES:]:
            role = item.get("role", "").strip()
            content = item.get("content", "").strip()
            if role in ("user", "assistant") and content:
                messages.append({"role": role, "content": content})

        messages.append({"role": "user", "content": user_message})
        return messages


def _strip_thinking(text: str) -> str:
    """Remove model thinking blocks from model output."""
    return _THINKING_PATTERN.sub("", text).strip()
