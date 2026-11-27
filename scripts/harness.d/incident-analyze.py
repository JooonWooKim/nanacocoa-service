"""Build a local mock analysis or run an explicit synthetic model pilot."""

from contextlib import contextmanager
import importlib
import json
import os
import signal
import subprocess
import sys
import time
import uuid

import incident_pilot


evidence = importlib.import_module("incident-evidence")
MAX_INPUT_BYTES = 16 * 1024
MAX_OUTPUT_BYTES = 4 * 1024
TIME_LIMIT_SECONDS = 5
EXIT_CODES = {"PASS": 0, "FAIL": 1, "BLOCKED": 2}
REQUIRED_KEYS = {"stage", "cause", "nextCheck", "eventIndex", "sources"}
STOCK = "STOCK_RESERVATION"
APPROVAL = "APPROVAL_UNCERTAIN"
RECOVERY_FAILURES = {"FAILED", "INTERNAL_ERROR"}

ADVICE = {
    STOCK: (
        "판매 가능 수량이 부족했거나 재고 예약 중 경합이 발생했을 수 있습니다.",
        "주문 시점의 판매 가능 수량, 재고 잠금과 전체 품목 롤백 여부를 대조합니다.",
    ),
    APPROVAL: (
        "제공자 응답이 늦었거나 내부 승인 확정 단계에서 처리가 중단됐을 수 있습니다.",
        "저장된 거래 상태를 먼저 조회합니다. 미확정이면 기존 승인 복구 절차에서 확인합니다.",
    ),
    **{
        operation: (
            "해당 자동 복구 단계에서 필요한 결과를 얻거나 반영하지 못했을 수 있습니다.",
            "같은 실행 번호의 실패 작업과 저장된 거래 상태를 조회하고 기존 복구 절차를 대조합니다.",
        )
        for operation in evidence.RECOVERY_OPERATIONS
    },
}

PILOT_CASES = (
    ("ORDER_CREATE", "STOCK_CONFLICT", 409, {
        "INSUFFICIENT_AVAILABLE_STOCK": "주문 시점의 판매 가능 수량이 부족했을 수 있습니다.",
        "RESERVATION_CONTENTION": "동시 주문이 재고 예약 단계에서 경합했을 수 있습니다.",
    }),
    ("PAYMENT_CONFIRM", "PENDING", 202, {
        "APPROVAL_CONFIRMATION_PENDING": "승인 결과 확정이 지연됐을 수 있습니다.",
    }),
    ("RECOVERY_CANCELLATION", "FAILED", None, {
        "CANCELLATION_RECOVERY_FAILED": "취소 복구 단계에서 결과를 얻거나 반영하지 못했을 수 있습니다.",
    }),
)


class DeadlineExceeded(Exception):
    """The local analysis exceeded its time budget."""


class InvalidModelOutput(Exception):
    """The adapter returned a result outside the local output contract."""


def deadline_expired(_signum, _frame):
    raise DeadlineExceeded()


@contextmanager
def analysis_deadline(seconds):
    previous_handler = signal.signal(signal.SIGALRM, deadline_expired)
    try:
        signal.setitimer(signal.ITIMER_REAL, seconds)
        yield
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        signal.signal(signal.SIGALRM, previous_handler)


def as_bytes(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def supported_cases(report):
    cases = {}
    for index, event in enumerate(report["timeline"]):
        stage, _, sources, _ = evidence.guidance(event)
        if stage not in {STOCK, APPROVAL} and not (
            event["kind"] == "RECOVERY" and event["result"] in RECOVERY_FAILURES
        ):
            continue
        case = cases.setdefault(stage, {
            "eventIndexes": [],
            "sources": [path for path in sources if path in report["sources"]],
        })
        case["eventIndexes"].append(index)
    return cases


def mock_model(payload):
    """Simulate hypotheses using explicit event references and fixed advice."""
    data = json.loads(payload)
    hypotheses = []
    for stage, case in data["cases"].items():
        cause, next_check = ADVICE[stage]
        hypotheses.append({
            "stage": stage, "cause": cause, "nextCheck": next_check,
            "eventIndex": case["eventIndexes"][0], "sources": case["sources"],
        })
    return as_bytes({"hypotheses": hypotheses})


def validate_model_output(raw, timeline, cases):
    try:
        response = json.loads(raw.decode("utf-8"), object_pairs_hook=evidence.object_with_unique_keys)
    except (ValueError, RecursionError):
        raise InvalidModelOutput() from None
    if not isinstance(response, dict) or response.keys() != {"hypotheses"}:
        raise InvalidModelOutput()
    hypotheses = response["hypotheses"]
    if not isinstance(hypotheses, list) or len(hypotheses) != len(cases):
        raise InvalidModelOutput()

    verified = []
    seen = set()
    for row in hypotheses:
        if not isinstance(row, dict) or row.keys() != REQUIRED_KEYS:
            raise InvalidModelOutput()
        stage = row["stage"]
        if not isinstance(stage, str) or stage not in cases or stage in seen:
            raise InvalidModelOutput()
        seen.add(stage)
        case = cases[stage]
        index, sources = row["eventIndex"], row["sources"]
        if (type(index) is not int or index not in case["eventIndexes"]
                or not isinstance(sources, list) or not sources
                or not all(isinstance(path, str) and path in case["sources"] for path in sources)
                or len(sources) != len(set(sources))
                or (row["cause"], row["nextCheck"]) != ADVICE[stage]):
            raise InvalidModelOutput()
        verified.append({**row, "event": timeline[index]})
    return verified


def analyze(args, result, model):
    report = evidence.evaluate(args, max_bytes=MAX_INPUT_BYTES)
    result.update({key: report[key] for key in ("candidateCommit", "kind", "id") if key in report})
    if report["status"] != "PASS":
        result.update(status=report["status"], reason=report["reason"])
        if "neededEvidence" in report:
            result["neededEvidence"] = report["neededEvidence"]
        return
    cases = supported_cases(report)
    if not cases:
        result.update(status="BLOCKED", reason="UNSUPPORTED_INCIDENT")
        return

    payload = as_bytes({
        "candidateCommit": report["candidateCommit"], "timeline": report["timeline"], "cases": cases,
        "recommendedTests": report["recommendedTests"],
    })
    if len(payload) > MAX_INPUT_BYTES:
        result.update(status="BLOCKED", reason="MODEL_INPUT_TOO_LARGE")
        return
    result["modelCalls"] = 1
    try:
        raw = model(payload)
    except (DeadlineExceeded, subprocess.TimeoutExpired):
        raise
    except Exception:
        raise InvalidModelOutput() from None
    if not isinstance(raw, bytes):
        raise InvalidModelOutput()
    if len(raw) > MAX_OUTPUT_BYTES:
        result.update(status="BLOCKED", reason="MODEL_OUTPUT_TOO_LARGE")
        return

    hypotheses = validate_model_output(raw, report["timeline"], cases)
    result.update(status="PASS", reason="ANALYSIS_PREPARED", hypotheses=hypotheses,
                  recommendedTests=report["recommendedTests"])


def run(args, *, model=mock_model, time_limit=TIME_LIMIT_SECONDS):
    started = time.monotonic()
    result = {"modelCalls": 0, "modelCost": 0}
    try:
        with analysis_deadline(time_limit):
            analyze(args, result, model)
    except (DeadlineExceeded, subprocess.TimeoutExpired):
        result.update(status="BLOCKED", reason="TIME_LIMIT_EXCEEDED")
    except evidence.InvalidInput as error:
        reason = str(error)
        result.update(status="BLOCKED" if reason == "INPUT_TOO_LARGE" else "FAIL", reason=reason)
    except InvalidModelOutput:
        result.update(status="FAIL", reason="INVALID_MODEL_OUTPUT")
    except OSError:
        result.update(status="FAIL", reason="INPUT_UNAVAILABLE")
    except (ValueError, RecursionError):
        result.update(status="FAIL", reason="INVALID_JSON")
    if result["status"] != "PASS":
        result.pop("hypotheses", None)
        result.pop("recommendedTests", None)
    result["durationMs"] = int((time.monotonic() - started) * 1000)
    return result


def pilot_case(candidate, number, fixture, api_key, transport, result):
    operation, outcome, http_status, choices = fixture
    event = {
        "kind": "HTTP" if http_status is not None else "RECOVERY",
        "id": str(uuid.UUID(int=number, version=4)),
        "occurredAt": "2026-11-27T00:00:00Z",
        "operation": operation, "result": outcome, "durationMs": 0,
    }
    if http_status is not None:
        event["httpStatus"] = http_status
    evidence.validate_event(event)
    stage, _, sources, tests = evidence.guidance(event)
    if not evidence.candidate_has_references(candidate, [*sources, *(evidence.TEST_PATHS[test] for test in tests)]):
        raise incident_pilot.PilotProblem("BLOCKED", "CANDIDATE_REFERENCES_MISSING")
    payload = {
        "stage": stage,
        "evidence": {"number": number, "operation": operation, "result": outcome},
        "candidateIds": list(choices),
    }
    result["modelCalls"] = 1
    response = transport(api_key, payload)
    result["usage"] = incident_pilot.usage_tokens(response)
    choice = incident_pilot.selected_candidate(response, payload)
    result.update(status="PASS", reason="ANALYSIS_PREPARED", hypothesis={
        "candidateId": choice, "stage": stage, "cause": choices[choice], "nextCheck": ADVICE[stage][1],
        "evidenceNumber": number, "event": event, "sources": list(sources),
    }, recommendedTests=list(tests))


def run_live_pilot(api_key, *, transport=incident_pilot.fetch_response, time_limit=incident_pilot.CASE_TIMEOUT):
    started = time.monotonic()
    result = {"mode": "live-pilot", "model": incident_pilot.MODEL, "modelCalls": 0, "cases": []}
    if not api_key or not all("!" <= character <= "~" for character in api_key):
        return {**result, "status": "BLOCKED", "reason": "PILOT_KEY_UNAVAILABLE", "durationMs": 0}

    candidate = None
    for number, fixture in enumerate(PILOT_CASES, start=1):
        case_started = time.monotonic()
        case = {"case": fixture[0], "modelCalls": 0, "usage": None}
        try:
            with analysis_deadline(time_limit):
                if candidate is None:
                    candidate = evidence.head_commit()
                    if not evidence.matches(evidence.SHA, candidate):
                        raise incident_pilot.PilotProblem("BLOCKED", "CANDIDATE_UNAVAILABLE")
                    result["candidateCommit"] = candidate
                pilot_case(candidate, number, fixture, api_key, transport, case)
        except incident_pilot.PilotProblem as error:
            case.update(status=error.status, reason=error.reason)
        except (DeadlineExceeded, subprocess.TimeoutExpired):
            case.update(status="BLOCKED", reason="TIME_LIMIT_EXCEEDED")
        except OSError:
            case.update(status="BLOCKED", reason="PILOT_UNAVAILABLE")
        if case["status"] != "PASS":
            case.pop("hypothesis", None)
            case.pop("recommendedTests", None)
        case["durationMs"] = int((time.monotonic() - case_started) * 1000)
        result["cases"].append(case)
        result["modelCalls"] += case["modelCalls"]
        if case["status"] != "PASS":
            break
    result.update(status=case["status"], reason=case["reason"] if case["status"] != "PASS" else "PILOT_PREPARED",
                  durationMs=int((time.monotonic() - started) * 1000))
    return result


def main(args):
    if args == ["--live-pilot"]:
        result = run_live_pilot(os.environ.get(incident_pilot.API_KEY_ENV))
    else:
        result = run(args)
    print(json.dumps(result, ensure_ascii=False))
    return EXIT_CODES[result["status"]]


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
