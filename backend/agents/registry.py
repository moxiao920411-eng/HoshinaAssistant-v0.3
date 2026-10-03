from ollama_service import OllamaService

from .base import AgentName, BaseAgent
from .daily_chat import DailyChatAgent
from .deep_reasoning import DeepReasoningAgent
from .emotion_analysis import EmotionAnalysisAgent
from .multimodal import MultimodalAgent
from .work_handler import WorkHandlerAgent


def build_agent_registry(model_service: OllamaService) -> dict[AgentName, BaseAgent]:
    agents: list[BaseAgent] = [
        DailyChatAgent(model_service),
        DeepReasoningAgent(model_service),
        EmotionAnalysisAgent(model_service),
        WorkHandlerAgent(model_service),
        MultimodalAgent(model_service),
    ]
    return {agent.name: agent for agent in agents}
