import re
from dataclasses import dataclass
from typing import Literal

from .base import AgentContext, AgentName

RouteMode = Literal["single", "collaborate"]


@dataclass(frozen=True)
class RouteDecision:
    agents: list[AgentName]
    mode: RouteMode
    reason: str


class AgentRouter:
    """Fast deterministic router for choosing one or more specialist agents."""

    _EMOTION_RE = re.compile(
        r"(難過|傷心|焦慮|壓力|崩潰|生氣|失落|寂寞|害怕|煩|累|不安|想哭|"
        r"sad|anxious|angry|stress|lonely|depressed|upset|"
        r"悲しい|不安|怒り|寂しい|つらい|疲れた)",
        re.IGNORECASE,
    )
    _REASONING_RE = re.compile(
        r"(分析|推理|比較|架構|設計|原因|為什麼|除錯|錯誤|bug|程式|邏輯|策略|規劃|"
        r"analy[sz]e|reason|compare|architecture|debug|error|code|strategy|plan|"
        r"分析|推論|比較|設計|原因|デバッグ|エラー|コード)",
        re.IGNORECASE,
    )
    _WORK_RE = re.compile(
        r"(待辦|提醒|行程|摘要|整理|任務|工作|會議|排程|清單|報告|文件|"
        r"todo|remind|schedule|summary|task|meeting|workflow|report|document|"
        r"リマインド|予定|要約|整理|タスク|会議|資料)",
        re.IGNORECASE,
    )
    _MULTIMODAL_RE = re.compile(
        r"(圖片|照片|截圖|影像|語音|聲音|影片|檔案|上傳|頭像|TTS|STT|多模態|"
        r"image|photo|screenshot|voice|audio|video|file|upload|avatar|multimodal|"
        r"画像|写真|スクショ|音声|動画|ファイル|アップロード)",
        re.IGNORECASE,
    )

    def route(self, context: AgentContext) -> RouteDecision:
        text = context.message.strip()
        agents: list[AgentName] = []
        reasons: list[str] = []

        if self._MULTIMODAL_RE.search(text):
            agents.append("multimodal")
            reasons.append("message references image, audio, file, or multimodal work")
        if self._EMOTION_RE.search(text):
            agents.append("emotion_analysis")
            reasons.append("message includes emotional signals")
        if self._WORK_RE.search(text):
            agents.append("work_handler")
            reasons.append("message asks for task, reminder, summary, or workflow handling")
        if self._REASONING_RE.search(text) or len(text) >= 180:
            agents.append("deep_reasoning")
            reasons.append("message needs reasoning, debugging, architecture, or a longer analysis")

        if not agents:
            agents.append("daily_chat")
            reasons.append("message is best handled as daily conversation")
        elif agents == ["emotion_analysis"]:
            agents.append("daily_chat")
            reasons.append("emotional messages benefit from a conversational final response")

        agents = self._dedupe(agents)
        mode: RouteMode = "collaborate" if len(agents) > 1 else "single"
        return RouteDecision(
            agents=agents,
            mode=mode,
            reason="; ".join(reasons),
        )

    @staticmethod
    def _dedupe(agents: list[AgentName]) -> list[AgentName]:
        seen: set[AgentName] = set()
        result: list[AgentName] = []
        for agent in agents:
            if agent not in seen:
                seen.add(agent)
                result.append(agent)
        return result
