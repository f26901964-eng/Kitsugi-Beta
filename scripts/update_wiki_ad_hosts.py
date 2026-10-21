#!/usr/bin/env python3
"""Explicit maintenance command; no automatic list download in the Android app.

python3 scripts/update_wiki_ad_hosts.py --commit <reviewed 40-character upstream commit>
python3 scripts/update_wiki_ad_hosts.py --check

Only the StevenBlack-contributed list is vendored, NOT the aggregate hosts file with
other projects' licenses. Inspect changes and upstream licensing before updating.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/wiki"
MANIFEST = ASSETS / "hosts-source.json"
FILES = {"data/StevenBlack/hosts": "stevenblack-hosts.txt", "license.txt": "STEVENBLACK-LICENSE.txt"}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def check():
    metadata = json.loads(MANIFEST.read_text())
    for name, expected in metadata["sha256"].items():
        assert digest((ASSETS / name).read_bytes()) == expected, f"Checksum mismatch: {name}"
    assert re.fullmatch(r"[0-9a-f]{40}", metadata["commit"])
    print("PASS pinned wiki blocklist and license checksums:", metadata["commit"])


def update(commit):
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise SystemExit("Use an explicit reviewed 40-character commit, not a moving branch/tag.")
    payloads = {}
    for upstream, local in FILES.items():
        url = f"https://api.github.com/repos/StevenBlack/hosts/contents/{upstream}?ref={commit}"
        request = urllib.request.Request(url, headers={"User-Agent": "Kitsugi-wiki-filter-maintenance", "Accept": "application/vnd.github+json"})
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read(500_001)
        if len(raw) > 500_000:
            raise SystemExit("Unexpectedly large response; inspect upstream before updating.")
        document = json.loads(raw)
        if document.get("encoding") != "base64":
            raise SystemExit("Expected a small, base64 GitHub contents response.")
        payloads[local] = base64.b64decode(document["content"])
    hosts = payloads["stevenblack-hosts.txt"].decode("utf-8")
    license_text = payloads["STEVENBLACK-LICENSE.txt"].decode("utf-8")
    if not 10_000 <= len(hosts) <= 300_000 or hosts.count("0.0.0.0 ") < 1000:
        raise SystemExit("Unexpected list format/size; review manually.")
    if "The MIT License (MIT)" not in license_text or "Steven Black" not in license_text:
        raise SystemExit("Upstream licensing changed; do not auto-import.")
    # All validations complete before replacing any bundled resource.
    ASSETS.mkdir(parents=True, exist_ok=True)
    for name, data in payloads.items():
        (ASSETS / name).write_bytes(data)
    MANIFEST.write_text(json.dumps({
        "repository": "https://github.com/StevenBlack/hosts",
        "path": "data/StevenBlack/hosts", "commit": commit, "license": "MIT",
        "sha256": {name: digest(data) for name, data in payloads.items()},
    }, indent=2) + "\n")
    check()
    print("Review the diff and rerun WikiPageToolsChecks; the UI reads its version from hosts-source.json.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--check", action="store_true")
    group.add_argument("--commit")
    args = parser.parse_args()
    if args.check:
        check()
    else:
        update(args.commit)
