#!/usr/bin/env python3
"""Turkish manga source inventory + capability audit.

Reproduces the numbers in docs/audits/MANGA_KAYNAK_UCTAN_UCA_DOGRULAMA_2026-10-10.md
from the *upstream sources of truth*, so the inventory can be re-run instead of
being copied by hand:

  1. keiyoushi/extensions  -> repo/index.json      (which sources exist, lang, nsfw, jar)
  2. keiyoushi/extensions-source -> src/tr/*, lib-multisrc/*, core/*
     (extensionLib version, base class chain, Cloudflare / WebView-login / novel markers)

This is an INVENTORY tool. It does not prove a source works end to end: it never
contacts a manga site. Runtime verification (search -> details -> chapters -> pages
-> image) must be done on a device via SourceHealthService.

Requires: git + network access to github.com.

Usage:
    python3 scripts/audit_manga_sources.py [--workdir /tmp/kei-audit] [--json OUT]
"""
from __future__ import annotations

import argparse
import collections
import glob
import json
import os
import re
import subprocess
import sys

EXTENSIONS_REPO = "https://github.com/keiyoushi/extensions"
EXTENSIONS_SOURCE_REPO = "https://github.com/keiyoushi/extensions-source"

# Base classes that are themselves KeiSource subclasses (verified in lib-multisrc).
KNOWN_KEI_BASES = {
    "Madara": "MadaraBase",
    "MadaraBase": "KeiSource",
    "MangaThemesia": "KeiSource",
    "MangaThemesiaAlt": "MangaThemesia",
    "ZeistManga": "KeiSource",
}

CLASS_RE = re.compile(
    r"(?:abstract |open |)class\s+([A-Za-z0-9_]+)\s*(?:\([^)]*\))?\s*:\s*([A-Za-z0-9_<>.,\s()]+?)[{(]",
    re.S,
)


def run(cmd: list[str], cwd: str | None = None, timeout: int = 600) -> None:
    subprocess.run(cmd, cwd=cwd, check=True, timeout=timeout,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def sparse_clone(url: str, dest: str, patterns: list[str]) -> None:
    """Blobless clone + non-cone sparse checkout (keeps the download small)."""
    if not os.path.isdir(os.path.join(dest, ".git")):
        if os.path.exists(dest):
            raise SystemExit(f"workdir exists and is not a git repo: {dest}")
        run(["git", "clone", "--depth", "1", "--filter=blob:none", "--no-checkout", url, dest])
    run(["git", "sparse-checkout", "init", "--no-cone"], cwd=dest)
    run(["git", "sparse-checkout", "set", *patterns], cwd=dest)
    # --no-checkout ile index bos baslar; `git checkout` sparse kumeyi materialize eder.
    # Tekrar calistirmada no-op olur (idempotent).
    result = subprocess.run(["git", "checkout"], cwd=dest,
                            stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
    if result.returncode != 0:
        run(["git", "read-tree", "-mu", "HEAD"], cwd=dest)


def head_sha(dest: str) -> str:
    out = subprocess.run(["git", "rev-parse", "HEAD"], cwd=dest, check=True,
                         capture_output=True, text=True)
    return out.stdout.strip()


def read(path: str) -> str:
    with open(path, encoding="utf-8", errors="replace") as fh:
        return fh.read()


def collect_index(ext_repo: str) -> tuple[dict, int, int]:
    path = os.path.join(ext_repo, "index.json")
    if not os.path.isfile(path):
        raise SystemExit(f"index.json not found at {path} (sparse checkout failed?)")
    data = json.load(open(path, encoding="utf-8"))
    extensions = data["extensionList"]["extensions"]
    total_sources = sum(len(e.get("sources", [])) for e in extensions)
    return data, len(extensions), total_sources


def turkish_sources(data: dict) -> list[dict]:
    rows = []
    for ext in data["extensionList"]["extensions"]:
        res = ext.get("resources") or {}
        nsfw = ext.get("contentWarning") == "CONTENT_WARNING_NSFW"
        for src in ext.get("sources", []):
            if (src.get("language") or "").lower() != "tr":
                continue
            rows.append({
                "source": src.get("name"),
                "pkg": ext.get("packageName"),
                "extName": ext.get("name"),
                "homeUrl": src.get("homeUrl"),
                "id": src.get("id"),
                "nsfw": nsfw,
                "jar": bool(res.get("jarUrl")),
                "apk": bool(res.get("apkUrl")),
                "extVersion": ext.get("versionName"),
                "langSegment": (ext.get("packageName") or "")
                    .replace("eu.kanade.tachiyomi.extension.", "").split(".")[0],
            })
    return sorted(rows, key=lambda r: (r["pkg"] or "", r["source"] or ""))


def base_class_map(src_repo: str) -> dict[str, str]:
    mapping = dict(KNOWN_KEI_BASES)
    mapping.setdefault("KeiSource", "HttpSource")
    for path in glob.glob(f"{src_repo}/lib-multisrc/**/*.kt", recursive=True):
        for match in CLASS_RE.finditer(read(path)):
            mapping.setdefault(match.group(1), match.group(2).split("<")[0].strip())
    return mapping


def root_base(cls: str, mapping: dict[str, str]) -> str:
    current, seen = cls, set()
    while current and current not in seen:
        seen.add(current)
        if current == "KeiSource":
            return "KeiSource"
        nxt = mapping.get(current)
        if not nxt:
            return current
        current = nxt
    return current


def analyse_extensions(src_repo: str) -> list[dict]:
    mapping = base_class_map(src_repo)
    rows = []
    tr_root = os.path.join(src_repo, "src", "tr")
    for name in sorted(os.listdir(tr_root)):
        ext_dir = os.path.join(tr_root, name)
        if not os.path.isdir(ext_dir):
            continue
        text = "\n".join(read(p) for p in glob.glob(f"{ext_dir}/**/*.kt", recursive=True))

        supers = [re.sub(r"\(.*", "", m.group(2)).strip() for m in CLASS_RE.finditer(text)]
        roots = [root_base(s, mapping) for s in supers]
        root = "KeiSource" if "KeiSource" in roots else (roots[0] if roots else "?")

        build = os.path.join(ext_dir, "build.gradle.kts")
        lib = ""
        if os.path.isfile(build):
            m = re.search(r'libVersion\s*=\s*"([^"]+)"', read(build))
            lib = m.group(1) if m else ""

        rows.append({
            "dir": name,
            "libVersion": lib,
            "rootBase": root,
            "keiSource": root == "KeiSource",
            # OkHttp 5.x-only APIs used by KeiSource -> missing on OkHttp 4.x hosts.
            "needsOkHttp5": root == "KeiSource",
            "cloudflareMention": "cloudflare" in text.lower(),
            "cloudflareClassUse": re.search(r"(?<![\w.])CloudflareInterceptor\s*\(", text) is not None,
            "webViewLogin": "WebView" in text,
            "novelMarker": "novel" in text.lower(),
        })
    return rows


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--workdir", default="/tmp/kei-audit",
                        help="directory for the sparse clones (default: /tmp/kei-audit)")
    parser.add_argument("--json", dest="json_out", default=None,
                        help="also write the full inventory to this JSON file")
    args = parser.parse_args()

    ext_repo = os.path.join(args.workdir, "extensions")
    src_repo = os.path.join(args.workdir, "extensions-source")
    os.makedirs(args.workdir, exist_ok=True)

    print(f"cloning (blobless) into {args.workdir} ...", file=sys.stderr)
    sparse_clone(EXTENSIONS_REPO, ext_repo, ["/index.json"])
    sparse_clone(EXTENSIONS_SOURCE_REPO, src_repo,
                 ["/src/tr/*", "/lib-multisrc/*", "/core/src/main/kotlin/keiyoushi/*"])

    data, total_ext, total_src = collect_index(ext_repo)
    tr = turkish_sources(data)
    exts = analyse_extensions(src_repo)

    kei = [e for e in exts if e["keiSource"]]
    legacy = [e for e in exts if not e["keiSource"]]

    print()
    print("=== Keiyoushi index.json @", head_sha(ext_repo)[:12], "===")
    print(f"extensions                 : {total_ext}")
    print(f"sources (all languages)    : {total_src}")
    print(f"Turkish (lang=tr) sources  : {len(tr)}")
    by_seg = collections.Counter(r["langSegment"] for r in tr)
    print(f"  by pkg segment           : {dict(by_seg)}")
    print(f"  inside NSFW extensions   : {sum(1 for r in tr if r['nsfw'])}")
    print(f"  missing jarUrl           : {sum(1 for r in tr if not r['jar'])}")
    print(f"  missing homeUrl          : {sum(1 for r in tr if not r['homeUrl'])}")

    print()
    print("=== keiyoushi/extensions-source src/tr @", head_sha(src_repo)[:12], "===")
    print(f"extension dirs             : {len(exts)}")
    print(f"  extensionLib 1.6 (KeiSource) : {len(kei)}")
    print(f"  older (HttpSource)           : {len(legacy)} -> {[e['dir'] for e in legacy]}")
    print(f"  libVersion mix               : {dict(collections.Counter(e['libVersion'] for e in exts))}")
    # Ayrim onemli: 'cloudflare' kelimesi yalnizca YORUM'da gecebilir (mangawt/toontaku
    # boyle). Gercek sinif kullanimi ile karistirmamak icin iki ayri sayac:
    print(f"  CloudflareInterceptor kullanani : {[e['dir'] for e in exts if e['cloudflareClassUse']]}")
    print(f"  'cloudflare' sadece yorumda     : {[e['dir'] for e in exts if e['cloudflareMention'] and not e['cloudflareClassUse']]}")
    print(f"  WebView/login references     : {sum(1 for e in exts if e['webViewLogin'])}")
    print(f"  'novel' references           : {[e['dir'] for e in exts if e['novelMarker']]}")

    print()
    print("NOT: Bu bir ENVANTER. Hicbir manga sitesine istek atilmadi;")
    print("     calisma dogrulamasi cihazda SourceHealthService ile yapilmali.")

    if args.json_out:
        payload = {
            "extensionsRepoSha": head_sha(ext_repo),
            "extensionsSourceSha": head_sha(src_repo),
            "totals": {"extensions": total_ext, "sources": total_src, "trSources": len(tr)},
            "turkishSources": tr,
            "trExtensions": exts,
        }
        with open(args.json_out, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, ensure_ascii=False, indent=1)
        print(f"\nwrote {args.json_out}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
