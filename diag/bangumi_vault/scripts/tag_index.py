"""Shared helpers for Bangumi tag chunk work (chunk 006+).

Builds the three comparison sources the handoff requires before a tag decision:

  1. `BangumiTagDictionary` groups (canon id -> TR/EN + every known label),
  2. `decisions/canon.tsv` rows whose role is `tag`,
  3. the merged TR/EN resource catalog (app + resource-only shards).

Matching is normalised the same way the runtime dictionary matches labels
(NFKC, lower case, wrapper/ponctuation stripped), so `「BL漫画】` and `bl` collapse.
"""
from __future__ import annotations

import re
import unicodedata
from pathlib import Path

VAULT = Path(__file__).resolve().parents[1]
ROOT = VAULT.parents[1]
TAG_DICT = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt"
CANON = VAULT / "decisions" / "canon.tsv"

STRIP_CHARS = set(" \t\r\n\u3000.·・〜~!?'\"★☆✘✔「」『』《》（）()【】[]{}<>、,，:：;；/\\|+-_*#@&%^=…—–")


def normalize(value: str) -> str:
    text = unicodedata.normalize("NFKC", value or "").lower()
    return "".join(ch for ch in text if ch not in STRIP_CHARS)


def _read_call(source: str, start: int) -> str:
    """Return the argument text of the g(...) call beginning at `start`."""
    depth = 0
    quote = False
    escape = False
    i = start + 1  # skip 'g'
    while i < len(source):
        ch = source[i]
        if escape:
            escape = False
        elif ch == "\\":
            escape = True
        elif ch == '"':
            quote = not quote
        elif not quote:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    return source[start + 2:i]
        i += 1
    raise SystemExit("unterminated g(...) call")


def _split_args(body: str) -> list[str]:
    out, buf, quote, escape = [], [], False, False
    for ch in body:
        if escape:
            buf.append(ch)
            escape = False
            continue
        if ch == "\\":
            escape = True
            buf.append(ch)
            continue
        if ch == '"':
            quote = not quote
            buf.append(ch)
            continue
        if ch == "," and not quote:
            out.append("".join(buf))
            buf = []
            continue
        buf.append(ch)
    if "".join(buf).strip():
        out.append("".join(buf))
    return out


def _unquote(arg: str) -> str:
    arg = arg.strip()
    if not (arg.startswith('"') and arg.endswith('"')):
        return arg
    body = arg[1:-1]
    return body.replace("${'$'}", "$").replace('\\"', '"').replace("\\'", "'")


def load_dictionary() -> list[dict]:
    """[(key, tr, en, [labels...])] for every g(...) group."""
    source = TAG_DICT.read_text(encoding="utf-8")
    groups = []
    for match in re.finditer(r'\bg\("', source):
        body = _read_call(source, match.start())
        args = [_unquote(a) for a in _split_args(body)]
        groups.append({"id": args[0], "tr": args[1], "en": args[2], "labels": args[3:]})
    return groups


def load_canon() -> dict[tuple[str, str], dict]:
    out = {}
    for raw in CANON.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("\t")
        if len(parts) < 6:
            continue
        cid, role, tr, en, origin, aliases = parts[:6]
        out[(role, cid)] = {
            "id": cid, "role": role, "tr": tr, "en": en, "origin": origin,
            "aliases": [a for a in aliases.split(";") if a],
            "note": parts[6] if len(parts) > 6 else "",
            "string_key": parts[7] if len(parts) > 7 and parts[7] else None,
        }
    return out


def load_resource_values() -> tuple[dict[str, str], dict[str, str]]:
    import resource_catalog

    tr_file = ROOT / "app/src/main/res/values/strings.xml"
    en_file = ROOT / "app/src/main/res/values-en/strings.xml"
    tr = dict(re.findall(r'<string name="([^"]+)">(.*?)</string>', resource_catalog.catalog_text(tr_file), re.S))
    en = dict(re.findall(r'<string name="([^"]+)">(.*?)</string>', resource_catalog.catalog_text(en_file), re.S))
    return tr, en


def build_label_index(groups: list[dict], canon: dict[tuple[str, str], dict]) -> dict[str, list[tuple[str, str]]]:
    """normalized label -> [(canon_id, source)] — source is `dict` or `canon`."""
    index: dict[str, list[tuple[str, str]]] = {}
    for group in groups:
        for label in group["labels"]:
            key = normalize(label)
            if key:
                index.setdefault(key, []).append((group["id"], "dict"))
    for (role, cid), row in canon.items():
        if role != "tag":
            continue
        for label in [row["tr"], row["en"], *row["aliases"]]:
            key = normalize(label)
            if key:
                index.setdefault(key, []).append((cid, "canon"))
    return index
