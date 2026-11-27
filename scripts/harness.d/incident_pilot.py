"""Responses API transport and result contract for the synthetic incident pilot."""

from contextlib import closing
import http.client
import importlib
import json


evidence = importlib.import_module("incident-evidence")
MODEL = "gpt-5.6-luna"
API_KEY_ENV = "INCIDENT_ANALYSIS_OPENAI_API_KEY"
CASE_TIMEOUT = 15
MAX_OUTPUT_TOKENS = 256
MAX_RESPONSE_BYTES = 64 * 1024
MAX_SELECTION_BYTES = 4 * 1024
RESPONSES_HOST = "api.openai.com"
RESPONSES_PATH = "/v1/responses"


class PilotProblem(Exception):
    """A pilot failure with a fixed, safe public reason."""

    def __init__(self, status, reason):
        super().__init__(reason)
        self.status = status
        self.reason = reason


def malformed():
    return PilotProblem("FAIL", "INVALID_MODEL_OUTPUT")


def load_json(data):
    try:
        return json.loads(data, object_pairs_hook=evidence.object_with_unique_keys)
    except (ValueError, RecursionError):
        raise malformed() from None


def request_body(payload):
    return {
        "model": MODEL, "store": False, "reasoning": {"effort": "none"},
        "max_output_tokens": MAX_OUTPUT_TOKENS,
        "instructions": (
            "Analyze this synthetic incident. Select one allowed candidate ID and cite the evidence number "
            "only if the recorded operation and result support the candidate as a possible cause. "
            "When the evidence does not support a candidate, choose INSUFFICIENT_EVIDENCE and set both "
            "reference fields to null. Do not confirm a root cause or return prose."
        ),
        "input": json.dumps(payload, separators=(",", ":")),
        "text": {
            "format": {
                "type": "json_schema", "name": "synthetic_incident_selection", "strict": True,
                "schema": {
                    "type": "object", "additionalProperties": False,
                    "properties": {
                        "decision": {"type": "string", "enum": ["SELECTED", "INSUFFICIENT_EVIDENCE"]},
                        "candidateId": {"type": ["string", "null"], "enum": [*payload["candidateIds"], None]},
                        "evidenceNumber": {
                            "type": ["integer", "null"], "enum": [payload["evidence"]["number"], None],
                        },
                    },
                    "required": ["decision", "candidateId", "evidenceNumber"],
                },
            },
        },
    }


def check_http_status(status):
    if status == 200:
        return
    if status in (401, 403, 404):
        raise PilotProblem("BLOCKED", "MODEL_ACCESS_UNAVAILABLE")
    if status == 408:
        raise PilotProblem("BLOCKED", "TIME_LIMIT_EXCEEDED")
    if status == 429:
        raise PilotProblem("BLOCKED", "MODEL_RATE_LIMITED")
    if status >= 500:
        raise PilotProblem("BLOCKED", "MODEL_UNAVAILABLE")
    raise PilotProblem("FAIL", "MODEL_HTTP_ERROR")


def fetch_response(api_key, payload):
    # A direct HTTPS connection neither follows redirects nor retries requests.
    body = json.dumps(request_body(payload)).encode("utf-8")
    try:
        with closing(http.client.HTTPSConnection(RESPONSES_HOST, timeout=CASE_TIMEOUT)) as connection:
            connection.request("POST", RESPONSES_PATH, body=body, headers={
                "Authorization": f"Bearer {api_key}", "Content-Type": "application/json",
            })
            with connection.getresponse() as response:
                check_http_status(response.status)
                raw = response.read(MAX_RESPONSE_BYTES + 1)
    except TimeoutError:
        raise PilotProblem("BLOCKED", "TIME_LIMIT_EXCEEDED") from None
    except (OSError, http.client.HTTPException):
        raise PilotProblem("BLOCKED", "MODEL_UNAVAILABLE") from None
    if len(raw) > MAX_RESPONSE_BYTES:
        raise malformed()
    return load_json(raw)


def usage_tokens(response):
    usage = response.get("usage") if isinstance(response, dict) else None
    if not isinstance(usage, dict):
        return None
    tokens = {
        public: usage[field]
        for field, public in (("input_tokens", "inputTokens"), ("output_tokens", "outputTokens"))
        if type(usage.get(field)) is int and 0 <= usage[field] < 2**53
    }
    return tokens or None


def selected_candidate(response, payload):
    if not isinstance(response, dict):
        raise malformed()
    status = response.get("status")
    if status == "incomplete":
        raise PilotProblem("BLOCKED", "MODEL_INCOMPLETE")
    if status in ("failed", "cancelled", "queued", "in_progress"):
        raise PilotProblem("BLOCKED", "MODEL_UNAVAILABLE")
    output = response.get("output")
    if status != "completed" or not isinstance(output, list):
        raise malformed()

    selections = []
    for item in output:
        if not isinstance(item, dict):
            raise malformed()
        if item.get("type") == "reasoning":
            continue
        content = item.get("content")
        if item.get("type") != "message" or item.get("role") != "assistant" or not isinstance(content, list):
            raise malformed()
        for part in content:
            if not isinstance(part, dict):
                raise malformed()
            if part.get("type") == "refusal":
                raise PilotProblem("BLOCKED", "MODEL_REFUSED")
            if part.get("type") != "output_text" or not isinstance(part.get("text"), str):
                raise malformed()
            selections.append(part["text"])
    if len(selections) != 1 or len(selections[0].encode("utf-8", errors="surrogatepass")) > MAX_SELECTION_BYTES:
        raise malformed()

    selection = load_json(selections[0])
    if not isinstance(selection, dict) or selection.keys() != {"decision", "candidateId", "evidenceNumber"}:
        raise malformed()
    candidate, number = selection["candidateId"], selection["evidenceNumber"]
    if selection["decision"] == "INSUFFICIENT_EVIDENCE" and candidate is None and number is None:
        raise PilotProblem("BLOCKED", "MODEL_INSUFFICIENT_EVIDENCE")
    if (selection["decision"] != "SELECTED" or not isinstance(candidate, str)
            or candidate not in payload["candidateIds"] or type(number) is not int
            or number != payload["evidence"]["number"]):
        raise malformed()
    return candidate
