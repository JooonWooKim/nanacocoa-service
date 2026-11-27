"""Open or reuse a GitHub PR and ensure it has a Codex review request."""

import argparse
from contextlib import contextmanager
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import quote, urlencode


ROOT = Path(__file__).resolve().parents[2]
REVIEW = "@codex review"
TRUSTED_AUTHORS = {"OWNER", "MEMBER", "COLLABORATOR"}
ORIGIN = re.compile(r"(?:https://github\.com/|git@github\.com:|ssh://git@github\.com/)"
                    r"([A-Za-z0-9-]+)/([A-Za-z0-9_.-]+?)(?:\.git)?/?")
EXIT_CODES = {"PASS": 0, "FAIL": 1, "BLOCKED": 2}


class CommandFailed(Exception):
    """Command output is not safe to include in the result."""


class RequestProblem(Exception):
    def __init__(self, status, reason):
        super().__init__(reason)
        self.status = status
        self.reason = reason


def command(args, *, body=None):
    try:
        completed = subprocess.run(
            args, input=body, cwd=ROOT, text=True, capture_output=True, timeout=40,
            env={**os.environ, "GH_PROMPT_DISABLED": "1"},
        )
    except (OSError, subprocess.TimeoutExpired, UnicodeError):
        raise CommandFailed() from None
    if completed.returncode:
        raise CommandFailed()
    return completed.stdout.strip()


def api(endpoint, *, payload=None, paginate=False):
    args = ["gh", "api", "--hostname", "github.com", endpoint]
    if payload is not None:
        args.extend(("--method", "POST", "--input", "-"))
    if paginate:
        args.extend(("--paginate", "--slurp"))
    output = command(args, body=json.dumps(payload) if payload is not None else None)
    try:
        data = json.loads(output)
    except (ValueError, RecursionError):
        raise CommandFailed() from None
    if paginate:
        if not isinstance(data, list) or not all(isinstance(page, list) for page in data):
            raise CommandFailed()
        return [entry for page in data for entry in page]
    if not isinstance(data, dict):
        raise CommandFailed()
    return data


@contextmanager
def workspace_lock():
    path = ROOT / "build/pr-create.lock"
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RequestProblem("BLOCKED", "PR_CREATE_IN_PROGRESS") from None
        yield


def target(args):
    match = ORIGIN.fullmatch(command(["git", "remote", "get-url", "origin"]))
    if not match:
        raise RequestProblem("BLOCKED", "GITHUB_ORIGIN_REQUIRED")
    owner, repo = match.groups()
    head = args.head or command(["git", "branch", "--show-current"])
    if not head or head == args.base or not args.title.strip():
        raise RequestProblem("FAIL", "INVALID_PR_ARGUMENTS")
    for branch in (head, args.base):
        try:
            command(["git", "check-ref-format", f"refs/heads/{branch}"])
        except CommandFailed:
            raise RequestProblem("FAIL", "INVALID_PR_BRANCH") from None
    try:
        current = command(["git", "rev-parse", "--verify", f"refs/heads/{head}"])
    except CommandFailed:
        raise RequestProblem("BLOCKED", "LOCAL_HEAD_UNAVAILABLE") from None
    repository = f"{owner}/{repo}"
    try:
        ref = api(f"repos/{repository}/git/ref/heads/{quote(head, safe='/')}")
    except CommandFailed:
        raise RequestProblem("BLOCKED", "REMOTE_HEAD_UNAVAILABLE") from None
    remote = ref.get("object")
    if not isinstance(remote, dict) or remote.get("sha") != current:
        raise RequestProblem("BLOCKED", "REMOTE_HEAD_MISMATCH")
    return repository, head, current


def pr_number(pr, repository, head, base):
    if not isinstance(pr, dict):
        return None
    source, destination, number = pr.get("head"), pr.get("base"), pr.get("number")
    if (not isinstance(source, dict) or not isinstance(destination, dict)
            or type(number) is not int or number <= 0):
        return None
    origin = source.get("repo")
    if (not isinstance(origin, dict) or not isinstance(origin.get("full_name"), str)
            or origin["full_name"].casefold() != repository.casefold()
            or source.get("ref") != head or destination.get("ref") != base
            or pr.get("state") != "open"):
        return None
    return number


def find_pr(repository, head, base):
    owner = repository.split("/")[0]
    query = urlencode({"state": "open", "head": f"{owner}:{head}", "base": base, "per_page": 100})
    try:
        candidates = api(f"repos/{repository}/pulls?{query}", paginate=True)
    except CommandFailed:
        raise RequestProblem("BLOCKED", "PR_LOOKUP_FAILED") from None
    candidates = [item for item in candidates if pr_number(item, repository, head, base) is not None]
    if len(candidates) > 1:
        raise RequestProblem("BLOCKED", "MULTIPLE_MATCHING_PRS")
    return candidates[0] if candidates else None


def open_pr(repository, head, base, title, body):
    pr = find_pr(repository, head, base)
    if pr is not None:
        return pr, "REUSED"
    try:
        pr = api(f"repos/{repository}/pulls", payload={"title": title, "body": body, "head": head, "base": base})
        if pr_number(pr, repository, head, base) is not None:
            return pr, "CREATED"
    except CommandFailed:
        pass
    # A lost response or a concurrent creation may have left a PR in GitHub.
    pr = find_pr(repository, head, base)
    if pr is None:
        raise RequestProblem("FAIL", "PR_CREATE_UNCONFIRMED")
    return pr, "RECOVERED"


def review_id(comment):
    if not isinstance(comment, dict):
        return None
    body, comment_id = comment.get("body"), comment.get("id")
    if isinstance(body, str) and body.strip() == REVIEW and type(comment_id) is int and comment_id > 0:
        return comment_id
    return None


def existing_review(endpoint):
    for comment in api(f"{endpoint}?per_page=100", paginate=True):
        association = comment.get("author_association") if isinstance(comment, dict) else None
        if isinstance(association, str) and association in TRUSTED_AUTHORS:
            identifier = review_id(comment)
            if identifier is not None:
                return identifier
    return None


def request_review(repository, number):
    endpoint = f"repos/{repository}/issues/{number}/comments"
    try:
        existing = existing_review(endpoint)
    except CommandFailed:
        raise RequestProblem("BLOCKED", "REVIEW_LOOKUP_FAILED") from None
    if existing is not None:
        return existing, "EXISTING"

    try:
        identifier = review_id(api(endpoint, payload={"body": REVIEW}))
        if identifier is not None:
            return identifier, "POSTED"
    except CommandFailed:
        pass
    try:
        existing = existing_review(endpoint)
    except CommandFailed:
        existing = None
    if existing is None:
        raise RequestProblem("BLOCKED", "REVIEW_REQUEST_UNCONFIRMED")
    return existing, "RECOVERED"


def run(args):
    result = {}
    try:
        body = args.body_file.read_text(encoding="utf-8")
    except (OSError, UnicodeError):
        return {"status": "FAIL", "reason": "PR_BODY_UNAVAILABLE"}
    try:
        with workspace_lock():
            repository, head, commit = target(args)
            pr, action = open_pr(repository, head, args.base, args.title, body)
            number = pr["number"]
            url = f"https://github.com/{repository}/pull/{number}"
            result.update(prNumber=number, prUrl=url, prAction=action)
            if pr["head"].get("sha") != commit:
                raise RequestProblem("BLOCKED", "PR_HEAD_MISMATCH")
            comment_id, review_action = request_review(repository, number)
            result.update(status="PASS", reviewUrl=f"{url}#issuecomment-{comment_id}", reviewAction=review_action)
    except RequestProblem as error:
        result.update(status=error.status, reason=error.reason)
    except (CommandFailed, OSError):
        result.update(status="BLOCKED", reason="PR_WORKFLOW_UNAVAILABLE")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--title", required=True)
    parser.add_argument("--body-file", type=Path, required=True)
    parser.add_argument("--base", default="main")
    parser.add_argument("--head", help="pushed local branch, defaults to the current branch")
    result = run(parser.parse_args())
    print(json.dumps(result, ensure_ascii=False))
    return EXIT_CODES[result["status"]]


if __name__ == "__main__":
    raise SystemExit(main())
