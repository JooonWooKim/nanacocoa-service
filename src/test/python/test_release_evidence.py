"""Verify quality evidence provenance, fail-closed collection, and preflight compatibility."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import zipfile


ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("release_evidence", ROOT / "scripts/harness.d/release-evidence.py")
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)

SHA = "a" * 40
TIME = "2026-11-27T04:55:44Z"
RUN = f"{collector.API}/runs/19"
JOBS = f"{RUN}/attempts/2/jobs?per_page=100"
ARTIFACTS = f"{RUN}/artifacts?name=release-quality-2"
ZIP = f"{collector.API}/artifacts/23/zip"


def encode(value):
    return json.dumps(value).encode()


def run_response(**changes):
    repository = {"full_name": collector.REPO}
    return {"id": 19, "head_sha": SHA, "head_branch": "main", "event": "push", "name": "Quality",
            "path": ".github/workflows/quality.yml", "repository": repository, "head_repository": repository,
            "status": "completed", "conclusion": "success", "run_attempt": 2, "updated_at": TIME, **changes}


def job_response(**changes):
    jobs = [{"name": name, "head_sha": SHA, "run_id": 19, "run_attempt": 2,
             "status": "completed", "conclusion": "success", **changes}
            for name in sorted(collector.JOB_NAMES)]
    return {"total_count": len(jobs), "jobs": jobs}


def archive(status="PASS", **changes):
    summary = {"schemaVersion": 1, "candidateCommit": SHA, "runId": 19,
               "runAttempt": 2, "status": status, **changes}
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as bundle:
        bundle.writestr(collector.SUMMARY, encode(summary))
    return output.getvalue()


def rehearsal_report():
    return {"schemaVersion": 1, "result": "PASS", "exitCode": 0, "reason": "REHEARSAL_VERIFIED",
            "completed": collector.REHEARSAL_STAGES.copy(),
            "check": {"id": "REHEARSAL", "status": "PASS", "candidateCommit": SHA,
                      "environment": "isolated", "role": "release-rehearsal", "verifiedAt": TIME,
                      "evidenceId": "ev-" + "1" * 24}}


class ReleaseEvidenceTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.path = Path(tmp.name) / "evidence.json"
        self.path.write_bytes(encode(rehearsal_report()))
        self.responses = {
            RUN: encode(run_response()), JOBS: encode(job_response()),
            ARTIFACTS: encode({"total_count": 1, "artifacts": [
                {"id": 23, "name": "release-quality-2", "size_in_bytes": 512, "expired": False}
            ]}),
            ZIP: archive(),
        }

    def invoke(self):
        output, errors = io.StringIO(), io.StringIO()
        with mock.patch.object(collector, "github", side_effect=self.responses.__getitem__) as api, \
                contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            result = collector.main(["--candidate", SHA, "--quality-run", "19",
                                     "--rehearsal", str(self.path), "--target", "production"])
        return result, output.getvalue(), errors.getvalue(), [call.args[0] for call in api.call_args_list]

    def preflight_rows(self, output):
        stdout = io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(io.StringIO()):
            code = collector.preflight.validate_manifest(json.loads(output))
        return code, {row["id"]: row for row in json.loads(stdout.getvalue())["checks"]}

    def test_success_binds_attempt_and_keeps_operational_checks_pending(self):
        code, out, _, endpoints = self.invoke()
        self.assertEqual(code, 0)
        self.assertEqual(endpoints, [RUN, JOBS, ARTIFACTS, ZIP, RUN])
        code, rows = self.preflight_rows(out)
        self.assertEqual(code, 2)
        self.assertEqual(rows["QUALITY"]["status"], "PASS")
        self.assertEqual(rows["REHEARSAL"]["status"], "PASS")
        self.assertEqual(rows["ROLLOUT"]["status"], "BLOCKED")

    def test_baselined_requires_separate_approval(self):
        self.responses[ZIP] = archive(status="BASELINED")
        code, out, _, _ = self.invoke()
        self.assertEqual(code, 0)
        quality = json.loads(out)["checks"][0]
        self.assertEqual(quality["status"], "BASELINED")
        self.assertNotIn("baselineApproval", quality)
        self.assertEqual(self.preflight_rows(out)[1]["QUALITY"]["reason"], "MISSING_BASELINE_APPROVAL")

    def test_successful_older_run_without_summary_is_blocked(self):
        self.responses[ARTIFACTS] = encode({"total_count": 0, "artifacts": []})
        code, out, _, endpoints = self.invoke()
        self.assertEqual(code, 0)
        self.assertNotIn(ZIP, endpoints)
        self.assertEqual(json.loads(out)["checks"][0]["status"], "BLOCKED")

    def test_in_progress_cancelled_and_failed_runs_do_not_get_pass(self):
        for status, conclusion, result in (
            ("in_progress", None, "BLOCKED"),
            ("completed", "cancelled", "BLOCKED"),
            ("completed", "failure", "FAIL"),
        ):
            with self.subTest(conclusion=conclusion):
                self.responses[RUN] = encode(run_response(status=status, conclusion=conclusion))
                code, out, _, endpoints = self.invoke()
                self.assertEqual(code, 0)
                self.assertEqual(endpoints, [RUN])
                self.assertEqual(json.loads(out)["checks"][0]["status"], result)

    def test_mismatched_run_source_is_rejected(self):
        for change in ({"head_sha": "c" * 40}, {"head_branch": "feature"},
                       {"event": "pull_request"}, {"head_repository": {"full_name": "other/repo"}},
                       {"path": ".github/workflows/not-quality.yml"}):
            with self.subTest(change=change):
                self.responses[RUN] = encode(run_response(**change))
                code, out, err, endpoints = self.invoke()
                self.assertEqual((code, out, endpoints), (1, "", [RUN]))
                self.assertIn("QUALITY_RUN_MISMATCH", err)

    def test_jobs_must_be_from_the_selected_attempt_and_both_present(self):
        self.responses[JOBS] = encode(job_response(run_attempt=1))
        code, out, err, _ = self.invoke()
        self.assertEqual((code, out), (1, ""))
        self.assertIn("QUALITY_JOB_MISMATCH", err)
        self.responses[JOBS] = encode({"total_count": 1, "jobs": job_response()["jobs"][:1]})
        code, out, _, endpoints = self.invoke()
        self.assertEqual(code, 0)
        self.assertEqual(json.loads(out)["checks"][0]["status"], "BLOCKED")
        self.assertEqual(endpoints, [RUN, JOBS])

    def test_modified_archive_is_rejected_without_echoing_content(self):
        self.responses[ZIP] = archive(candidateCommit="b" * 40)
        code, out, err, _ = self.invoke()
        self.assertEqual((code, out), (1, ""))
        self.assertIn("QUALITY_ARTIFACT_MISMATCH", err)
        self.responses[ZIP] = archive(unknown="synthetic-sensitive-sentinel")
        code, out, err, _ = self.invoke()
        self.assertEqual((code, out), (1, ""))
        self.assertNotIn("synthetic-sensitive-sentinel", err)

    def test_new_attempt_starting_during_collection_blocks_the_old_result(self):
        responses = dict(self.responses)
        runs = iter([encode(run_response()), encode(run_response(run_attempt=3, status="queued", conclusion=None))])
        output = io.StringIO()
        with mock.patch.object(collector, "github", side_effect=lambda endpoint: (
                next(runs) if endpoint == RUN else responses[endpoint])), \
                contextlib.redirect_stdout(output), contextlib.redirect_stderr(io.StringIO()):
            code = collector.main(["--candidate", SHA, "--quality-run", "19",
                                   "--rehearsal", str(self.path), "--target", "production"])
        self.assertEqual(code, 0)
        self.assertEqual(json.loads(output.getvalue())["checks"][0]["status"], "BLOCKED")

    def test_rehearsal_sha_and_full_pass_stages_are_required(self):
        report = rehearsal_report()
        report["check"]["candidateCommit"] = "b" * 40
        self.path.write_bytes(encode(report))
        code, out, err, endpoints = self.invoke()
        self.assertEqual((code, out, endpoints), (1, "", []))
        self.assertIn("CANDIDATE_MISMATCH", err)
        report = rehearsal_report()
        report["completed"].pop()
        self.path.write_bytes(encode(report))
        code, out, err, endpoints = self.invoke()
        self.assertEqual((code, out, endpoints), (1, "", []))
        self.assertIn("REHEARSAL_REPORT_INVALID", err)

    def test_publisher_distinguishes_optional_skip_base_and_missing_gates(self):
        rows = {name: ("SKIPPED" if name in collector.OPTIONAL else "PASS") for name in collector.GATES}

        def summary():
            self.path.write_text("".join(f"{name}|{status}|private-path\n" for name, status in rows.items()))
            return collector.publish(self.path)

        with mock.patch.dict(collector.os.environ, GITHUB_SHA=SHA, GITHUB_RUN_ID="19", GITHUB_RUN_ATTEMPT="2"):
            evidence = summary()
            self.assertEqual(evidence["status"], "PASS")
            self.assertNotIn("private-path", json.dumps(evidence))
            rows["architecture"] = "BASELINED"
            self.assertEqual(summary()["status"], "BASELINED")
            rows["test-baseline"] = "SKIPPED"
            with self.assertRaisesRegex(collector.CollectionError, "HARNESS_SUMMARY_INVALID"):
                summary()
            del rows["test-baseline"]
            with self.assertRaisesRegex(collector.CollectionError, "HARNESS_SUMMARY_INCOMPLETE"):
                summary()


if __name__ == "__main__":
    unittest.main()
