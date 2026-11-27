"""Fixture coverage of the PR and review workflow without GitHub writes."""

from argparse import Namespace
import importlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from urllib.parse import parse_qs, urlsplit
import sys


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts/harness.d"))
FLOW = importlib.import_module("pr-create")
REPO = "Example/shop"
BASE = "main"
HEAD = "feature/65"
SHA = "a" * 40
TITLE = "PR 제목"
BODY = "## 변경 사항\n\n본문에 `코드`와 줄바꿈을 유지합니다.\n"


def pr(commit=SHA, repository=REPO):
    return {"number": 65, "state": "open", "head": {"ref": HEAD, "sha": commit, "repo": {"full_name": repository}},
            "base": {"ref": BASE}}


def review(identifier=1001, association="OWNER", body="@codex review"):
    return {"id": identifier, "author_association": association, "body": body}


class PrCreateTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        path = self.root / "body.md"
        path.write_text(BODY, encoding="utf-8")
        self.args = Namespace(title=TITLE, body_file=path, base=BASE, head=None)
        self.remote_sha = SHA
        self.pull_requests = []
        self.comments = [[]]
        self.writes = []
        self.lose_create_response = False
        self.lose_review_response = False
        self.reject_review = False
        self.reject_review_lookup = False
        self.reject_pr_lookup = False

    def fake_command(self, args, *, body=None):
        if args[0] == "git":
            results = {
                "remote": f"https://github.com/{REPO}.git",
                "branch": HEAD,
                "check-ref-format": "",
                "rev-parse": SHA,
            }
            return results[args[1]]

        self.assertEqual(["gh", "api", "--hostname", "github.com"], args[:4])
        address = urlsplit(args[4])
        path = address.path
        self.assertTrue(path.startswith(f"repos/{REPO}/"))
        if "--method" in args:
            self.assertEqual(["--method", "POST", "--input", "-"], args[5:])
            payload = json.loads(body)
            self.writes.append((path.rsplit("/", 1)[-1], payload))
            if path.endswith("/pulls"):
                self.assertEqual({"title": TITLE, "body": BODY, "head": HEAD, "base": BASE}, payload)
                self.pull_requests.append(pr())
                if self.lose_create_response:
                    raise FLOW.CommandFailed()
                return json.dumps(self.pull_requests[-1])

            self.assertTrue(path.endswith("/issues/65/comments"))
            self.assertEqual({"body": "@codex review"}, payload)
            if self.reject_review:
                raise FLOW.CommandFailed("private-provider-error")
            self.comments[-1].append(review())
            if self.lose_review_response:
                raise FLOW.CommandFailed()
            return json.dumps(self.comments[-1][-1])

        self.assertIsNone(body)
        if "/git/ref/heads/" in path:
            self.assertEqual(f"repos/{REPO}/git/ref/heads/{HEAD}", path)
            if self.remote_sha is None:
                raise FLOW.CommandFailed()
            return json.dumps({"object": {"sha": self.remote_sha}})
        self.assertEqual(["--paginate", "--slurp"], args[5:])
        if path.endswith("/pulls"):
            query = parse_qs(address.query)
            self.assertEqual({"state": ["open"], "head": ["Example:" + HEAD], "base": [BASE],
                              "per_page": ["100"]}, query)
            if self.reject_pr_lookup:
                raise FLOW.CommandFailed()
            return json.dumps([self.pull_requests])
        self.assertTrue(path.endswith("/issues/65/comments"))
        if self.reject_review_lookup:
            raise FLOW.CommandFailed()
        return json.dumps(self.comments)

    def submit(self):
        with (patch.object(FLOW, "ROOT", self.root),
              patch.object(FLOW, "command", side_effect=self.fake_command)):
            return FLOW.run(self.args)

    def test_new_pr_posts_exactly_one_review_and_returns_links(self):
        result = self.submit()
        self.assertEqual(("PASS", "CREATED", "POSTED"),
                         (result["status"], result["prAction"], result["reviewAction"]))
        self.assertEqual(["pulls", "comments"], [name for name, _ in self.writes])
        self.assertEqual("https://github.com/Example/shop/pull/65", result["prUrl"])
        self.assertEqual(result["prUrl"] + "#issuecomment-1001", result["reviewUrl"])
        self.assertEqual("EXISTING", self.submit()["reviewAction"])
        self.assertEqual(2, len(self.writes))

    def test_reuses_trusted_comment_on_later_page_without_remote_writes(self):
        self.pull_requests = [pr(repository="Example/fork"), pr()]
        self.comments = [[review(900, association="NONE")], [review(999, association="COLLABORATOR")]]
        result = self.submit()
        self.assertEqual(("PASS", "REUSED", "EXISTING"),
                         (result["status"], result["prAction"], result["reviewAction"]))
        self.assertTrue(result["reviewUrl"].endswith("#issuecomment-999"))
        self.assertEqual([], self.writes)

    def test_external_comment_does_not_count_as_the_requested_review(self):
        self.pull_requests = [pr()]
        self.comments = [[review(900, association="NONE")]]
        result = self.submit()
        self.assertEqual(("PASS", "REUSED", "POSTED"),
                         (result["status"], result["prAction"], result["reviewAction"]))
        self.assertEqual(["comments"], [name for name, _ in self.writes])

    def test_lost_create_and_comment_responses_are_recovered_by_lookup(self):
        self.lose_create_response = True
        self.lose_review_response = True
        result = self.submit()
        self.assertEqual(("PASS", "RECOVERED", "RECOVERED"),
                         (result["status"], result["prAction"], result["reviewAction"]))
        self.assertEqual(["pulls", "comments"], [name for name, _ in self.writes])

    def test_partial_review_failure_keeps_pr_link_and_rerun_reuses_pr(self):
        self.reject_review = True
        result = self.submit()
        self.assertEqual(("BLOCKED", "REVIEW_REQUEST_UNCONFIRMED"), (result["status"], result["reason"]))
        self.assertEqual(65, result["prNumber"])
        self.assertNotIn("reviewUrl", result)
        self.assertNotIn("private-provider-error", json.dumps(result))
        self.reject_review = False
        self.assertEqual("REUSED", self.submit()["prAction"])
        self.assertEqual(["pulls", "comments", "comments"], [name for name, _ in self.writes])

    def test_unpushed_refs_never_create_a_pr(self):
        for value, reason in ((None, "REMOTE_HEAD_UNAVAILABLE"), ("b" * 40, "REMOTE_HEAD_MISMATCH")):
            self.remote_sha = value
            self.assertEqual(reason, self.submit()["reason"])
        self.assertEqual([], self.writes)

    def test_changed_pr_head_and_lookup_failure_do_not_mutate(self):
        self.pull_requests = [pr(commit="b" * 40)]
        self.assertEqual("PR_HEAD_MISMATCH", self.submit()["reason"])
        self.pull_requests = [pr()]
        self.reject_review_lookup = True
        self.assertEqual("REVIEW_LOOKUP_FAILED", self.submit()["reason"])
        self.reject_pr_lookup = True
        self.assertEqual("PR_LOOKUP_FAILED", self.submit()["reason"])
        self.assertEqual([], self.writes)

    def test_local_concurrent_invocation_cannot_make_duplicate_writes(self):
        with patch.object(FLOW, "ROOT", self.root), FLOW.workspace_lock():
            result = self.submit()
        self.assertEqual("PR_CREATE_IN_PROGRESS", result["reason"])
        self.assertEqual([], self.writes)


if __name__ == "__main__":
    unittest.main()
