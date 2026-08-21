import pytest
from fastapi.testclient import TestClient

from agent_service import planner
from agent_service.deepseek import DeepSeekPlannerError
from agent_service.main import app
from agent_service.models import PlanRequest, PlanResponse


client = TestClient(app)


class StubPlanner:
    config = type("Config", (), {"model": "deepseek-v4-flash"})()

    def __init__(self, response: PlanResponse | None = None, error: str | None = None) -> None:
        self._response = response
        self._error = error
        self.requests: list[PlanRequest] = []

    def create_plan(self, request: PlanRequest) -> PlanResponse:
        self.requests.append(request)
        if self._error:
            raise DeepSeekPlannerError(self._error)
        assert self._response is not None
        return self._response


def chat_response(message: str = "Hello! How can I help with your resume?") -> PlanResponse:
    return PlanResponse(status="CHAT", intent="CHAT", target=None, operations=[], message=message)


def test_without_api_key_returns_503_with_safe_code() -> None:
    response = client.post(
        "/internal/v1/agent/plan",
        json={"instruction": "hello"},
    )

    assert response.status_code == 503
    assert response.json() == {"detail": "no_api_key"}


def test_explicit_age_change_uses_stable_rules_without_calling_provider(monkeypatch: pytest.MonkeyPatch) -> None:
    stub = StubPlanner(response=chat_response())
    monkeypatch.setattr(planner, "deepseek_planner", stub)

    response = client.post(
        "/internal/v1/agent/plan",
        json={
            "instruction": "把年龄改成 28",
            "history": [
                {"role": "user", "content": "帮我修改年龄"},
                {"role": "assistant", "content": "What age should be set on your default resume?"},
            ],
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "status": "READY",
        "intent": "UPDATE_RESUME",
        "target": "DEFAULT_RESUME",
        "operations": [
            {"tool": "get_my_resume", "arguments": {}},
            {"tool": "preview_resume_patch", "arguments": {"field": "age", "action": "set", "value": 28}},
        ],
        "message": "I can prepare a preview to set your default resume age to 28.",
    }
    assert stub.requests == []


def test_generic_resume_change_is_consistently_clarified_without_provider(monkeypatch: pytest.MonkeyPatch) -> None:
    stub = StubPlanner(response=chat_response())
    monkeypatch.setattr(planner, "deepseek_planner", stub)

    response = client.post("/internal/v1/agent/plan", json={"instruction": "帮我修改简历"})

    assert response.status_code == 200
    assert response.json()["status"] == "NEEDS_CLARIFICATION"
    assert stub.requests == []


def test_planner_failure_returns_503_with_provider_code(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(planner, "deepseek_planner", StubPlanner(error="timeout"))

    response = client.post(
        "/internal/v1/agent/plan",
        json={"instruction": "provider failure"},
    )

    assert response.status_code == 503
    assert response.json() == {"detail": "timeout"}


def test_health_reports_no_api_key_mode() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "plannerMode": "NO_API_KEY",
        "model": "none",
        "lastPlanProvider": "none",
        "lastError": "none",
    }


def test_health_reports_deepseek_mode_after_successful_plan(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(planner, "deepseek_planner", StubPlanner(response=chat_response()))

    assert client.post("/internal/v1/agent/plan", json={"instruction": "hello"}).status_code == 200

    health = client.get("/health").json()
    assert health["plannerMode"] == "DEEPSEEK"
    assert health["model"] == "deepseek-v4-flash"
    assert health["lastPlanProvider"] == "deepseek"
    assert health["lastError"] == "none"


def test_provider_failure_is_recorded_in_health(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(planner, "deepseek_planner", StubPlanner(error="http_401"))

    client.post("/internal/v1/agent/plan", json={"instruction": "hello"})

    health = client.get("/health").json()
    assert health["plannerMode"] == "DEEPSEEK"
    assert health["lastError"] == "http_401"


def test_unknown_request_fields_are_rejected() -> None:
    response = client.post(
        "/internal/v1/agent/plan",
        json={"instruction": "年龄改成28", "accessToken": "must-not-be-accepted"},
    )

    assert response.status_code == 422


def test_history_is_limited_and_rejects_unknown_fields() -> None:
    response = client.post(
        "/internal/v1/agent/plan",
        json={"instruction": "hello", "history": [{"role": "user", "content": "hi", "token": "no"}]},
    )
    assert response.status_code == 422


def test_recruiter_fields_are_forwarded_to_planner(monkeypatch: pytest.MonkeyPatch) -> None:
    stub = StubPlanner(response=chat_response())
    monkeypatch.setattr(planner, "deepseek_planner", stub)

    response = client.post(
        "/internal/v1/agent/plan",
        json={
            "instruction": "帮我筛选这个岗位",
            "agentType": "RECRUITER",
            "serverDate": "2026-08-20",
            "timezone": "Asia/Shanghai",
        },
    )

    assert response.status_code == 200
    request = stub.requests[0]
    assert request.agentType == "RECRUITER"
    assert request.serverDate == "2026-08-20"
    assert request.timezone == "Asia/Shanghai"


def test_unknown_agent_type_is_rejected() -> None:
    response = client.post(
        "/internal/v1/agent/plan",
        json={"instruction": "hello", "agentType": "ADMIN"},
    )
    assert response.status_code == 422


def test_job_id_is_forwarded_for_recruiter(monkeypatch: pytest.MonkeyPatch) -> None:
    stub = StubPlanner(response=chat_response())
    monkeypatch.setattr(planner, "deepseek_planner", stub)

    response = client.post(
        "/internal/v1/agent/plan",
        json={
            "instruction": "帮我筛选这个岗位",
            "agentType": "RECRUITER",
            "jobId": "b1f2c3d4-0000-0000-0000-000000000001",
        },
    )

    assert response.status_code == 200
    assert stub.requests[0].jobId == "b1f2c3d4-0000-0000-0000-000000000001"


def test_job_id_is_rejected_for_candidate() -> None:
    response = client.post(
        "/internal/v1/agent/plan",
        json={"instruction": "hello", "jobId": "b1f2c3d4-0000-0000-0000-000000000001"},
    )
    assert response.status_code == 422
