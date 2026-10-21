#!/usr/bin/env python3
"""Read-only post-application audit: coverage, targets, old values and new twins.

The default baseline is HEAD (the previous committed chunk set). Run before
committing; after committing pass --baseline <previous-commit> for reproducibility.
"""
import argparse
import collections
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from org_name_resolver import normalize_name
from android_strings import unescape

ROOT = Path(__file__).resolve().parents[3]
VAULT = ROOT / 'diag/bangumi_vault'
ROLES = ('studio', 'producer', 'publisher', 'magazine', 'network')
FILES = ('studios', 'producers', 'publishers', 'magazines', 'networks')


def strings(text):
    return {e.attrib['name']: unescape(''.join(e.itertext()))
            for e in ET.fromstring(text).findall('string')}


def audit(chunks, baseline):
    errors = []
    langs = {}
    old_langs = {}
    for lang in ('values', 'values-en'):
        path = f'app/src/main/res/{lang}/strings.xml'
        current = strings((ROOT / path).read_text())
        old = strings(subprocess.check_output(['git', 'show', f'{baseline}:{path}'],
                                              cwd=ROOT, text=True))
        for k, v in old.items():
            if current.get(k) != v:
                errors.append(f'old {lang} value changed/deleted: {k}')
        langs[lang], old_langs[lang] = current, old
    tr, en = langs['values'], langs['values-en']
    added = set(tr) - set(old_langs['values'])
    if added != set(en) - set(old_langs['values-en']):
        errors.append('new TR/EN keys differ')
    owners = {}
    for k, v in old_langs['values'].items():
        if re.match(r'bangumi_(?:org|tag|studio|producer|network|publisher|magazine)_', k) and (not k.startswith('bangumi_tag_') or
                v == old_langs['values-en'].get(k)):
            for val in (v, old_langs['values-en'].get(k, v)):
                if normalize_name(val):
                    owners.setdefault(normalize_name(val), k)
    for k in sorted(added):
        if not tr[k].strip() or not en[k].strip():
            errors.append(f'empty translation: {k}')
        norm = normalize_name(tr[k])
        if norm and norm in owners:
            errors.append(f'duplicate new value: {k} vs {owners[norm]}')
        if norm:
            owners[norm] = k
    records = {r['key']: r for fn in FILES
               for r in json.loads((VAULT / f'{fn}.json').read_text())}
    for chunk in chunks:
        mapped = {}
        counts = collections.Counter()
        for line in (VAULT / f'decisions/map_chunk_{chunk}_orgs.tsv').read_text().splitlines():
            if not line.strip() or line.startswith('#'):
                continue
            key, cid, *_ = line.split('\t')
            if key in mapped:
                errors.append(f'duplicate map key: {key}')
            mapped[key] = cid
            r = records[key]
            counts[r['role'], 'skip' if cid == 'SKIP' else 'canon'] += 1
            if r.get('chunk') != f'{chunk}_orgs':
                errors.append(f'wrong/missing chunk stamp: {key}')
            if cid == 'SKIP':
                if r.get('decision') != 'skip' or r.get('string_key'):
                    errors.append(f'SKIP leaked into resources: {key}')
            else:
                target = r.get('string_key')
                if target not in tr or target not in en:
                    errors.append(f'missing TR/EN target: {key} -> {target}')
                elif (r['tr'], r['en']) != (tr[target], en[target]):
                    errors.append(f'TR/EN target value mismatch: {key} -> {target}')
        band = {'005c_a': (3, 4), '005c_b': (2, 2), '005c_c': (1, 1)}[chunk]
        pending = [k for k, r in records.items() if band[0] <= (r.get('count') or 0)
                   <= band[1] and not r.get('decision')]
        if pending:
            errors.append(f'{chunk}: {len(pending)} undecided records remain')
        print(chunk, 'map', len(mapped), 'roles', dict(counts), 'pending', len(pending))
    print('New TR/EN keys:', len(added), 'old values preserved:', len(old_langs['values']))
    if errors:
        raise SystemExit('\n'.join(errors[:50]) + f'\n{len(errors)} audit errors')
    print('✅ Coverage / target / SKIP / old-value / normalized-twin audit passed')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--chunk', action='append', required=True)
    p.add_argument('--baseline', default='HEAD')
    args = p.parse_args()
    audit(args.chunk, args.baseline)
