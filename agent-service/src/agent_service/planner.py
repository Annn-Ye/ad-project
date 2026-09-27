import re
from threading import Lock
from typing import TypedDict

from langgraph.graph import END, START, StateGraph

from .deepseek import DeepSeekConfig, DeepSeekPlanner, DeepSeekPlannerError
from .models import ConversationMessage, PlanOperation, PlanRequest, PlanResponse


class PlannerState(TypedDict, total=False):
    instruction: str
    agent_type: str
    job_id: str | None
    server_date: str | None
    timezone: str | None
    history: list[dict[str, str]]
    response: PlanResponse


def _candidate_rule_plan(request: PlanRequest) -> PlanResponse | None:
    """Handle unambiguous, whitelisted resume requests without model interpretation.

    The backend still validates ownership and requires a preview confirmation before any
    write. Rules only make simple requests stable when a provider is slow or imprecise.
    """
    if request.agentType != "CANDIDATE":
        return None

    instruction = request.instruction.strip()
    normalized = instruction.lower()
    age_requested = "年龄" in instruction or re.search(r"\bage\b", normalized) is not None
    if age_requested:
        match = re.search(r"(?<!\d)(\d{1,3})(?!\d)", instruction)
        if match is None:
            return PlanResponse(
                status="NEEDS_CLARIFICATION", intent=None, target=None, operations=[],
                message="Please provide the age to set on your default resume (16–100).",
            )
        age = int(match.group(1))
        if not 16 <= age <= 100:
            return PlanResponse(
                status="NEEDS_CLARIFICATION", intent=None, target=None, operations=[],
                message="Age must be a whole number between 16 and 100.",
            )
        return PlanResponse(
            status="READY", intent="UPDATE_RESUME", target="DEFAULT_RESUME",
            operations=[
                PlanOperation(tool="get_my_resume", arguments={}),
                PlanOperation(tool="preview_resume_patch", arguments={"field": "age", "action": "set", "value": age}),
            ],
            message=f"I can prepare a preview to set your default resume age to {age}.",
        )

    requested_sections = (
        ("skills", ("skill", "技能")),
        ("summary", ("summary", "简介", "概述")),
        ("experiences", ("experience", "经历", "工作经验")),
    )
    if any(word in normalized or word in instruction for _, words in requested_sections for word in words):
        query_words = ("show", "view", "read", "list", "查看", "显示", "列出", "看看")
        if any(word in normalized or word in instruction for word in query_words):
            section = next(section for section, words in requested_sections
                           if any(word in normalized or word in instruction for word in words))
            return PlanResponse(
                status="READY", intent="QUERY_RESUME", target="DEFAULT_RESUME",
                operations=[
                    PlanOperation(tool="get_my_resume", arguments={}),
                    PlanOperation(tool="read_resume_section", arguments={"section": section}),
                ],
                message=f"I can show the {section} section of your default resume.",
            )

    generic_resume_words = ("resume", "简历")
    change_words = ("change", "edit", "modify", "update", "修改", "改", "编辑", "更新")
    if any(word in normalized or word in instruction for word in generic_resume_words) and any(
        word in normalized or word in instruction for word in change_words
    ):
        return PlanResponse(
            status="NEEDS_CLARIFICATION", intent=None, target=None, operations=[],
            message="Tell me which resume field to change: age, summary, skills, or work experience.",
        )
    return None


def parse_instruction(state: PlannerState) -> PlannerState:
    request = PlanRequest(
        instruction=state["instruction"],
        agentType=state.get("agent_type", "CANDIDATE"),
        jobId=state.get("job_id"),
        serverDate=state.get("server_date"),
        timezone=state.get("timezone"),
        history=[ConversationMessage.model_validate(message) for message in state.get("history", [])],
    )
    rule_plan = _candidate_rule_plan(request)
    if rule_plan is not None:
        return {"response": rule_plan}
    if not deepseek_planner:
        raise DeepSeekPlannerError("no_api_key")
    return {
        "response": deepseek_planner.create_plan(request)
    }


def build_graph():
    graph = StateGraph(PlannerState)
    graph.add_node("parse_instruction", parse_instruction)
    graph.add_edge(START, "parse_instruction")
    graph.add_edge("parse_instruction", END)
    return graph.compile()


planner_graph = build_graph()

_deepseek_config = DeepSeekConfig.from_env()
deepseek_planner = DeepSeekPlanner(_deepseek_config) if _deepseek_config else None
_diagnostics_lock = Lock()
_last_provider = "none"
_last_error = "none"


def _record_diagnostics(provider: str, error: str = "none") -> None:
    global _last_provider, _last_error
    with _diagnostics_lock:
        _last_provider = provider
        _last_error = error


def planner_diagnostics() -> dict[str, str]:
    with _diagnostics_lock:
        return {
            "plannerMode": "DEEPSEEK" if deepseek_planner else "NO_API_KEY",
            "model": deepseek_planner.config.model if deepseek_planner else "none",
            "lastPlanProvider": _last_provider,
            "lastError": _last_error,
        }


def create_plan(request: PlanRequest) -> PlanResponse:
    try:
        used_rules = _candidate_rule_plan(request) is not None
        result = planner_graph.invoke({
            "instruction": request.instruction,
            "agent_type": request.agentType,
            "job_id": request.jobId,
            "server_date": request.serverDate,
            "timezone": request.timezone,
            "history": [message.model_dump() for message in request.history],
        })
        _record_diagnostics("rules" if used_rules else "deepseek")
        return result["response"]
    except DeepSeekPlannerError as exc:
        _record_diagnostics("deepseek" if deepseek_planner else "none", exc.code)
        raise
