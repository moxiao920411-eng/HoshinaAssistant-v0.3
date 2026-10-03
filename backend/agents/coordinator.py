import asyncio
from dataclasses import dataclass

from ollama_service import OllamaService

from .base import AgentContext, AgentName, AgentOutput
from .registry import build_agent_registry
from .router import AgentRouter, RouteDecision


@dataclass(frozen=True)
class CoordinatedReply:
    response: str
    agents_used: list[AgentName]
    mode: str
    route_reason: str


class AgentCoordinator:
    def __init__(self, model_service: OllamaService) -> None:
        self._model_service = model_service
        self._router = AgentRouter()
        self._agents = build_agent_registry(model_service)

    async def chat(self, context: AgentContext) -> CoordinatedReply:
        decision = self._router.route(context)
        outputs = await self._run_agents(decision, context)

        if len(outputs) == 1:
            final_response = outputs[0].content
        else:
            final_response = await self._merge_outputs(context, decision, outputs)

        return CoordinatedReply(
            response=final_response,
            agents_used=[output.name for output in outputs],
            mode=decision.mode,
            route_reason=decision.reason,
        )

    async def _run_agents(
        self,
        decision: RouteDecision,
        context: AgentContext,
    ) -> list[AgentOutput]:
        tasks = [self._agents[name].reply(context) for name in decision.agents]
        return list(await asyncio.gather(*tasks))

    async def _merge_outputs(
        self,
        context: AgentContext,
        decision: RouteDecision,
        outputs: list[AgentOutput],
    ) -> str:
        agent_notes = "\n\n".join(
            f"Agent: {output.name}\n{output.content}" for output in outputs
        )
        voice_rules = (
            "\nPhone call mode is active. Reply as spoken conversation: "
            "1 to 3 short sentences, no bullet lists, natural follow-up when useful."
        ) if context.voice_mode else ""
        merge_prompt = f"""You are Hoshina's final response coordinator.

You will receive multiple agent outputs. Merge them into one natural final reply.

Rules:
- Do not expose agent names, routing details, model names, local model details, or internal settings.
- Preserve the useful conclusion, emotional support, actionable next step, and reminder tags.
- Be proactive: ask one natural follow-up question or offer a next step when it fits.
- Keep sentences compact for chat display.
- If any agent includes [REMINDER:HH:mm:message], keep that hidden tag at the very end.
- Reply in the user's current language. If uncertain, use Traditional Chinese.
{voice_rules}
"""
        merge_message = (
            f"User message:\n{context.message}\n\n"
            f"Route mode: {decision.mode}\n"
            f"Route reason: {decision.reason}\n\n"
            f"Agent outputs:\n{agent_notes}"
        )
        return await self._model_service.chat(
            merge_message,
            history=context.history,
            user_profile=context.user_profile,
            client_time=context.client_time,
            ai_name=context.ai_name,
            ai_model=context.ai_model,
            role_prompt=merge_prompt,
        )
