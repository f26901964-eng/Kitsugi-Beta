"""Conservative name matching for org drafts; a slug is never identity evidence."""
from __future__ import annotations

import hashlib
import re
import unicodedata


def normalize_name(value: str) -> str:
    # Retain semantic symbols (C++ is not C); fold width, case and spacing/punctuation.
    value = unicodedata.normalize('NFKC', value).casefold()
    return ''.join(c for c in value if c.isalnum() or c in '+&#')


class NameResolver:
    def __init__(self, strings, canon):
        self.strings = dict(strings)
        self.canon = dict(canon)
        self.by_name = {}
        self.bindings = {}
        self.used = {k for k in strings}
        for (role, cid), row in canon.items():
            self.used.add(f'bangumi_{role}_{cid}')
        self.used_cids = {k.split("_", 2)[2] for k in self.used}
        for key, values in sorted(strings.items()):
            for value in values:
                norm = normalize_name(value)
                if norm:
                    self.by_name.setdefault(norm, key)

    def resolve(self, role, cid, latin, origin, note, raw_name=""):
        norm = normalize_name(latin)
        target = (self.by_name.get(norm) if norm else None) or self.by_name.get(normalize_name(raw_name))
        if target:
            target_cid = target.split('_', 2)[2]
            # An existing (role,cid) may refer to a DIFFERENT string even if the
            # target cid belongs to the right name in another role.
            row = self.canon.get((role, target_cid))
            row_target = (row[7] if len(row) > 7 and row[7] else
                          f'bangumi_{role}_{target_cid}') if row else target
            if row and row_target != target:
                binding = (role, target)
                if binding not in self.bindings:
                    fresh = self.fresh_id(role, latin, cid)
                    self.bindings[binding] = fresh
                    self.used.add(f'bangumi_{role}_{fresh}')
                    self.used_cids.add(fresh)
                target_cid = self.bindings[binding]
            values = self.strings[target]
            return target_cid, (*values, 'existing',
                f'değer eşleşmesi: {latin} → {target}', target)

        key = f'bangumi_{role}_{cid}'
        # Never bind just because a short or CJK-hash suffix happens to exist.
        if key in self.used or cid in self.used_cids:
            cid = self.fresh_id(role, latin, cid)
            key = f'bangumi_{role}_{cid}'
            note += ' · cid çakışması önlendi'
        self.used.add(key)
        self.used_cids.add(cid)
        self.strings[key] = (latin, latin)
        if norm:
            self.by_name[norm] = key
        return cid, (latin, latin, origin, note, '')

    def fresh_id(self, role, name, fallback):
        base = re.sub(r'[^a-z0-9]+', '_',
            unicodedata.normalize('NFKC', name).casefold()).strip('_')
        digest = hashlib.sha256(name.encode()).hexdigest()[:10]
        # Do not invent a Latin reading for CJK names.
        base = base if base and len(base) > 2 else f'{fallback}_{digest}'
        cid = base
        i = 0
        while cid in self.used_cids:
            i += 1
            cid = f'{base}_{digest}' + (f'_{i}' if i > 1 else '')
        return cid
