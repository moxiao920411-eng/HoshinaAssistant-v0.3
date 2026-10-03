from dataclasses import dataclass
from typing import Literal

from ollama_service import OllamaService

AgentName = Literal[
    "daily_chat",
    "deep_reasoning",
    "emotion_analysis",
    "work_handler",
    "multimodal",
]


@dataclass(frozen=True)
class AgentContext:
    message: str
    history: list[dict[str, str]]
    user_profile: str | None = None
    client_time: str | None = None
    ai_name: str | None = None
    ai_model: str | None = None
    role_prompt: str | None = None
    voice_mode: bool = False


@dataclass(frozen=True)
class AgentOutput:
    name: AgentName
    content: str


class BaseAgent:
    name: AgentName
    display_name: str
    prompt: str

    def __init__(self, model_service: OllamaService) -> None:
        self._model_service = model_service
        self._translated_prompt_cache: dict[str, str] = {}

    async def reply(self, context: AgentContext) -> AgentOutput:
        role_prompt = await self._build_role_prompt(context.role_prompt, context.voice_mode)
        content = await self._model_service.chat(
            context.message,
            history=context.history,
            user_profile=context.user_profile,
            client_time=context.client_time,
            ai_name=context.ai_name,
            ai_model=context.ai_model,
            role_prompt=role_prompt,
        )
        return AgentOutput(name=self.name, content=content)

    async def _build_role_prompt(
        self,
        custom_role_prompt: str | None,
        voice_mode: bool,
    ) -> str:
        custom = (custom_role_prompt or "").strip()
        common_rules = (
            "\n\nGlobal response rules:\n"
            "- Be proactive: when useful, ask one natural follow-up question or offer a next step.\n"
            "- Keep sentences compact so the app can display replies as short chat bubbles.\n"
            "- Prefer roughly 15 to 20 CJK characters per idea when replying in Chinese or Japanese.\n"
            "- Do not mention model names, local model details, routing internals, or agent internals.\n"
        )
        voice_rules = (
            "\n\nPhone call mode rules:\n"
            "- The user is speaking by voice. Reply like a live phone conversation.\n"
            "- Use 1 to 3 short spoken sentences, unless the user explicitly asks for detail.\n"
            "- Avoid bullet lists, long explanations, markdown, and formal structure.\n"
            "- Sound present and responsive. Ask one gentle follow-up when it fits.\n"
            "- Keep the reply easy to speak aloud with TTS.\n"
        ) if voice_mode else ""
        if not custom:
            return self.prompt + common_rules + voice_rules

        english_custom = await self._translate_role_prompt_to_english(custom)
        return (
            f"{self.prompt}{common_rules}{voice_rules}\n\n"
            "User configured personality and rules, translated to English:\n"
            f"{english_custom}"
        )

    async def _translate_role_prompt_to_english(self, prompt: str) -> str:
        if not _needs_translation(prompt):
            return prompt
        cached = self._translated_prompt_cache.get(prompt)
        if cached:
            return cached

        translated = await self._model_service.chat(
            prompt,
            history=[],
            ai_model=None,
            role_prompt=(
                "Translate the user's role/personality prompt into clear English. "
                "Return only the translated instruction text. Do not answer the prompt."
            ),
        )
        self._translated_prompt_cache[prompt] = translated
        return translated


def _needs_translation(text: str) -> bool:
    # The local model understands CJK instructions directly. Translating the
    # app's Chinese role prompt first adds a second model call and can return
    # only hidden thinking tokens, which used to surface as a chat 502.
    cjk_ranges = (
        (0x3040, 0x30FF),  # Hiragana/Katakana
        (0x3400, 0x9FFF),  # CJK Unified Ideographs
        (0xAC00, 0xD7AF),  # Hangul
    )
    return any(
        ord(char) > 127
        and not any(start <= ord(char) <= end for start, end in cjk_ranges)
        for char in text
    )
