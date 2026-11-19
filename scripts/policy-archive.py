#!/usr/bin/env python3
"""Offline policy archive conversion/verification; never connects to a database."""
import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

FIELDS = ('documentCode', 'version', 'title', 'body', 'consentMode', 'effectiveAt', 'approvedBy', 'approvedAt')


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def document(row):
    result = {field: row[field] for field in FIELDS}
    for field in ('effectiveAt', 'approvedAt'):
        instant = datetime.fromisoformat(result[field].replace('Z', '+00:00'))
        if instant.utcoffset() is None:
            raise ValueError('Policy dates must have an offset')
        result[field] = instant.astimezone(timezone.utc).isoformat()
    return result


def key(row):
    return row['documentCode'] + '/' + row['version']


def digest(row):
    payload = json.dumps(document(row), ensure_ascii=False, sort_keys=True, separators=(',', ':'))
    return hashlib.sha256(payload.encode('utf-8')).hexdigest()


def versions(catalog):
    result = {}
    for row in catalog['documents'] + catalog.get('archivedDocuments', []):
        identity = key(row)
        if identity in result:
            raise ValueError('Duplicate policy version')
        result[identity] = document(row)
    return result


def export(rows, catalog):
    entries = versions(catalog)
    archived = list(catalog.get('archivedDocuments', []))
    audit = {}
    for row in rows:
        identity = key(row)
        if identity in audit or not re.fullmatch('[a-f0-9]{64}', row['contentHash']):
            raise ValueError('Invalid legacy digest or duplicate version')
        normalized = document(row)
        if identity in entries and entries[identity] != normalized:
            raise ValueError('Legacy version contents differ from candidate files')
        if identity not in entries:
            archived.append(normalized)
            entries[identity] = normalized
        # Legacy DB digest includes the original JSON serialization, which may use a different offset.
        # Preserve it unchanged alongside a canonical digest of the exported document.
        audit[identity] = {'legacyContentHash': row['contentHash'], 'fileDigest': digest(row)}
    return {**catalog, 'archivedDocuments': archived}, audit


def verify(rows, catalog, audit, previous=None):
    entries = versions(catalog)
    seen = set()
    for row in rows:
        identity = key(row)
        if identity in seen:
            raise ValueError('Duplicate legacy version')
        seen.add(identity)
        if entries.get(identity) != document(row):
            raise ValueError('Missing or altered legacy document')
        expected = {'legacyContentHash': row['contentHash'], 'fileDigest': digest(row)}
        if audit.get(identity) != expected:
            raise ValueError('Legacy digest or exported file digest differs')
    if previous is not None:
        old = versions(previous)
        if any(entries.get(identity) != row for identity, row in old.items()):
            raise ValueError('Published versions must be preserved unchanged')
        if catalog['revision'] < previous['revision']:
            raise ValueError('Revision cannot move backwards')
        old_current = {key(row): document(row) for row in previous['documents']}
        current = {key(row): document(row) for row in catalog['documents']}
        if current != old_current and catalog['revision'] <= previous['revision']:
            raise ValueError('Changed current policies require a higher revision')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('export', 'verify'))
    parser.add_argument('--input', required=True, help='JSON result of policy-archive-export.sql')
    parser.add_argument('--catalog', required=True)
    parser.add_argument('--audit', required=True, help='Legacy digest audit JSON (output for export)')
    parser.add_argument('--output', help='New catalog file; export never overwrites files')
    parser.add_argument('--previous', help='Previous released catalog for immutable-version comparison')
    args = parser.parse_args()
    try:
        rows, catalog = read(args.input), read(args.catalog)
        if args.action == 'export':
            if not args.output or Path(args.output).exists() or Path(args.audit).exists():
                raise ValueError('Export requires unused output/audit paths')
            result, audit = export(rows, catalog)
            verify(rows, result, audit, read(args.previous) if args.previous else None)
            for path, value in ((args.output, result), (args.audit, audit)):
                with Path(path).open('x', encoding='utf-8') as target:
                    json.dump(value, target, ensure_ascii=False, indent=2)
                    target.write('\n')
        else:
            verify(rows, catalog, read(args.audit), read(args.previous) if args.previous else None)
        print('PASS: policy versions and archive digests verified')
    except (OSError, ValueError, KeyError, TypeError):
        parser.exit(1, 'FAIL: invalid/missing policy archive or version/digest mismatch; review input files locally\n')


if __name__ == '__main__':
    main()
