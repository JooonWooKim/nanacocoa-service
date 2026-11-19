import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('policy_archive', Path(__file__).resolve().parents[3] / 'scripts/policy-archive.py')
archive = importlib.util.module_from_spec(spec)
spec.loader.exec_module(archive)


class ArchiveTests(unittest.TestCase):
    def setUp(self):
        self.row = dict(documentCode='TERMS', version='v1', title='fixture', body='original', consentMode='REQUIRED',
                        effectiveAt='2020-01-01T09:00:00+09:00', approvedBy='fixture', approvedAt='2020-01-01T00:00:00Z', contentHash='a'*64)
        self.empty = dict(revision=0, documents=[])

    def test_export_preserves_body_and_legacy_digest_and_normalizes_only_dates(self):
        result, audit = archive.export([self.row], self.empty)
        self.assertEqual(result['archivedDocuments'][0]['body'], 'original')
        self.assertEqual(audit['TERMS/v1']['legacyContentHash'], 'a'*64)
        archive.verify([self.row], result, audit)
        # Equivalent offsets compare equal; no need to reconstruct the legacy JSON serialization.
        equivalent = {**self.row, 'effectiveAt': '2020-01-01T00:00:00Z'}
        archive.verify([equivalent], result, audit)

    def test_missing_altered_or_wrong_digest_prevents_transition(self):
        result, audit = archive.export([self.row], self.empty)
        with self.assertRaises(ValueError):
            archive.verify([self.row], self.empty, audit)
        altered = copy.deepcopy(result)
        altered['archivedDocuments'][0]['body'] = 'changed'
        with self.assertRaises(ValueError):
            archive.verify([self.row], altered, audit)
        with self.assertRaises(ValueError):
            archive.verify([self.row], result, {})
        with self.assertRaises(ValueError):
            archive.export([{**self.row, 'body': 'changed'}], result)

    def test_release_comparison_preserves_versions_and_requires_revision_increase(self):
        previous = dict(revision=1, documents=[archive.document(self.row)])
        replacement = {**archive.document(self.row), 'version': 'v2', 'body': 'next'}
        next_catalog = dict(revision=2, documents=[replacement], archivedDocuments=previous['documents'])
        archive.verify([], next_catalog, {}, previous)
        with self.assertRaises(ValueError):
            archive.verify([], {**next_catalog, 'revision': 1}, {}, previous)
        with self.assertRaises(ValueError):
            archive.verify([], {**next_catalog, 'archivedDocuments': []}, {}, previous)


if __name__ == '__main__':
    unittest.main()
