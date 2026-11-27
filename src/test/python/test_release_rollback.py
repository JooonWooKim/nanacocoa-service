"""Regression tests for isolated upgrade, rollback, and cleanup."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("release_rollback", ROOT / "scripts/harness.d/release-rollback.py")
rollback = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rollback)
rehearsal = rollback.rehearsal
PREVIOUS = "a" * 40
CANDIDATE = "b" * 40


class ReleaseRollbackTest(unittest.TestCase):
    def cli(self, previous=PREVIOUS, candidate=CANDIDATE):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = rollback.main(["--previous", previous, "--candidate", candidate])
        return code, json.loads(output.getvalue())

    def test_two_distinct_full_shas_are_required_before_any_command(self):
        with mock.patch.object(rehearsal, "command") as command:
            self.assertEqual(self.cli(previous="aaaaaaa")[1]["reason"], "EXPECTED_FULL_COMMIT_SHAS")
            self.assertEqual(self.cli(previous=CANDIDATE)[1]["reason"], "COMMITS_MUST_DIFFER")
        command.assert_not_called()

    def test_candidate_must_match_head_before_export(self):
        with mock.patch.object(rehearsal, "command",
                               side_effect=[PREVIOUS.encode(), CANDIDATE.encode(), PREVIOUS.encode()]), \
                mock.patch.object(rehearsal, "export_candidate") as export:
            code, result = self.cli()
        self.assertEqual((code, result["reason"]), (1, "CANDIDATE_NOT_HEAD"))
        export.assert_not_called()

    def test_import_requires_an_empty_database(self):
        stack = mock.Mock()
        stack.mysql.return_value = b"2\n"
        with self.assertRaisesRegex(rollback.RehearsalError, "IMPORT_DATABASE_NOT_EMPTY"):
            rollback.import_backup(stack, b"synthetic dump", [b"row"], "ROLLBACK_RESTORE_FAILED")
        stack.mysql.assert_called_once()

    def test_corrupted_import_or_data_mismatch_is_reported_by_phase(self):
        stack = mock.Mock()
        stack.mysql.return_value = b"0\n"
        with mock.patch.object(rehearsal, "fingerprint", return_value=[b"different"]):
            with self.assertRaisesRegex(rollback.RehearsalError, "ROLLBACK_RESTORE_FAILED"):
                rollback.import_backup(stack, b"synthetic dump", [b"row"], "ROLLBACK_RESTORE_FAILED")

    def test_order_read_requires_paid_fixture_and_uses_new_session(self):
        stack = mock.Mock()
        with mock.patch.object(rehearsal, "check_app"), mock.patch.object(rehearsal, "check_schema"), \
                mock.patch.object(rehearsal, "check_payment_guard", return_value="fixture-session"), \
                mock.patch.object(rehearsal, "http_json", return_value=(200, {"data": {
                    "orderId": "REHEARSAL-ORDER", "totalAmount": 42000, "status": "PAID", "paymentResult": "FAILED",
                }}, None)) as http:
            with self.assertRaisesRegex(rollback.RehearsalError, "ROLLBACK_START_OR_ORDER_READ_FAILED"):
                rollback.application_ready(stack, "ROLLBACK_START_OR_ORDER_READ_FAILED")
        http.assert_called_once_with(stack.port.return_value, "/api/orders/REHEARSAL-ORDER",
                                     "ROLLBACK_START_OR_ORDER_READ_FAILED", headers={"Cookie": "fixture-session"})

    def rehearsed(self, candidate_error=None, final_rows=None, cleanup_error=None):
        stacks = [mock.Mock(image=f"synthetic-image-{i}", owned=False, docker=["docker"]) for i in range(3)]
        stacks[0].mysql_tool.return_value = b"private fixture dump"
        stacks[-1].close.side_effect = cleanup_error

        def ready(_stack, reason):
            if reason.startswith("CANDIDATE_") and candidate_error:
                raise candidate_error

        rows = [b"same synthetic transactions"]
        with mock.patch.object(rollback, "verify_candidates"), \
                mock.patch.object(rehearsal, "export_candidate", side_effect=[Path("old"), Path("new")]), \
                mock.patch.object(rehearsal, "Stack", side_effect=stacks) as stack_factory, \
                mock.patch.object(rehearsal, "command") as command, \
                mock.patch.object(rehearsal, "fingerprint", side_effect=[rows, final_rows or rows]), \
                mock.patch.object(rollback, "transaction_state", return_value=rows), \
                mock.patch.object(rollback, "import_backup") as importer, \
                mock.patch.object(rollback, "application_ready", side_effect=ready):
            code, result = self.cli()
        return code, result, stacks, stack_factory, command, importer

    def test_rehearsal_uses_separate_upgrade_and_restore_stacks(self):
        code, result, stacks, factory, command, importer = self.rehearsed()
        self.assertEqual((code, result["reason"]), (0, "ROLLBACK_VERIFIED"))
        self.assertEqual([call.args[0] for call in factory.call_args_list], [Path("old"), Path("new"), Path("old")])
        self.assertEqual([call.args[0] for call in importer.call_args_list], stacks[1:])
        command.assert_called_once_with(["docker", "image", "tag", stacks[0].image, stacks[2].image], mock.ANY,
                                        env=mock.ANY)
        self.assertEqual(result["completed"][-2:], ["rollback-application", "cleanup"])
        self.assertNotIn("private fixture dump", json.dumps(result))
        self.assertNotIn("check", result)
        for stack in stacks:
            stack.close.assert_called_once()

    def test_candidate_failure_still_exercises_rollback_and_remains_fail(self):
        error = rollback.RehearsalError("CANDIDATE_START_OR_ORDER_READ_FAILED")
        code, result, *_ = self.rehearsed(candidate_error=error)
        self.assertEqual((code, result["reason"]), (1, str(error)))
        self.assertNotIn("candidate-upgrade", result["completed"])
        self.assertIn("rollback-application", result["completed"])

    def test_interrupt_never_starts_a_new_restore_stack(self):
        code, result, stacks, factory, *_ = self.rehearsed(candidate_error=rollback.RehearsalError("INTERRUPTED"))
        self.assertEqual((code, result["reason"]), (1, "INTERRUPTED"))
        self.assertEqual(factory.call_count, 2)
        self.assertEqual(result["completed"][-1], "cleanup")
        stacks[2].close.assert_not_called()

    def test_bad_restored_rows_and_cleanup_failure_cannot_pass(self):
        code, result, *_ = self.rehearsed(final_rows=[b"changed"])
        self.assertEqual((code, result["reason"]), (1, "ROLLBACK_DATA_MISMATCH"))
        code, result, stacks, *_ = self.rehearsed(cleanup_error=rollback.RehearsalError("CLEANUP_FAILED"))
        self.assertEqual((code, result["reason"]), (1, "CLEANUP_FAILED"))
        self.assertNotIn("cleanup", result["completed"])
        for stack in stacks:
            stack.close.assert_called_once()


if __name__ == "__main__":
    unittest.main()
