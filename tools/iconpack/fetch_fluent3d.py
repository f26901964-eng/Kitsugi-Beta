#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Microsoft Fluent Emoji (3D) ikonlarını GitHub API üzerinden indirir.

Kaynak : https://github.com/microsoft/fluentui-emoji
Lisans : MIT (Copyright (c) Microsoft Corporation)

Kullanım: python3 tools/iconpack/fetch_fluent3d.py
İndirilen dosyalar: tools/iconpack/raw/<anahtar>.png
"""
import json
import os
import urllib.parse
import urllib.request

API = "https://api.github.com/repos/microsoft/fluentui-emoji/contents/assets/"

# anahtar -> Fluent emoji varlık klasörü (3D render alınır)
MANIFEST = {
    # ── Ayarlar ana ekranı ────────────────────────────────────────────────
    "account": "Identification card",
    "connections": "Link",
    "palette": "Artist palette",
    "player": "Film projector",
    "extensions": "Puzzle piece",
    "integrations": "Globe with meridians",
    "database": "Floppy disk",
    "storage": "Broom",
    "tune": "Control knobs",
    "download": "Inbox tray",
    "history": "Stopwatch",
    "info": "Information",
    "feedback": "Megaphone",
    "warning": "Warning",
    # ── Oynatıcı ayarları sekmeleri ───────────────────────────────────────
    "play": "Play button",
    "gestures": "Hand with fingers splayed",
    "decoder": "Film frames",
    "subtitles": "Left speech bubble",
    "audio": "Speaker high volume",
    "buttons": "Video game",
    "code": "Laptop",
    "advanced": "Gear",
    # ── Alt/yan gezinme çubuğu ────────────────────────────────────────────
    "nav_explore": "Compass",
    "nav_list": "Bookmark tabs",
    "nav_search": "Magnifying glass tilted left",
    "nav_profile": "Person",
    # ── Yardımcı eşlemeler (satır/seçenek ikonları) ───────────────────────
    "network": "Satellite antenna",
    "sync": "Repeat button",
    "key": "Key",
    "star": "Star",
    "image": "Framed picture",
    "delete": "Wastebasket",
    "eye": "Eye",
    "heart": "Red heart",
    "clipboard": "Clipboard",
    "chart": "Bar chart",
    "check_circle": "Check mark button",
    "pause": "Pause button",
    "lock": "Locked",
    "folder": "Open file folder",
    "bell": "Bell",
    "calendar": "Spiral calendar",
    "sparkles": "Sparkles",
    "thumbup": "Thumbs up",
    "house": "House",
    "mobile": "Mobile phone",
    "pin": "Round pushpin",
    "rewind": "Fast reverse button",
    "moon": "Crescent moon",
    "speed": "Rocket",
    "timer": "Hourglass not done",
    "sun": "Sun",
    "next": "Next track button",
    "tv": "Television",
    "keyboard": "Keyboard",
    "help": "Red question mark",
    "memo": "Memo",
    "wrench": "Wrench",
}


def api_json(url):
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json",
                                               "User-Agent": "kitsugi-icon-fetch"})
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def api_raw(url):
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github.raw",
                                               "User-Agent": "kitsugi-icon-fetch"})
    with urllib.request.urlopen(req) as r:
        return r.read()


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.join(here, "raw")
    os.makedirs(out_dir, exist_ok=True)
    ok, bad = [], []
    for key, name in MANIFEST.items():
        try:
            folder = api_json(API + urllib.parse.quote(name))
            names = [x["name"] for x in folder]
            if "3D" in names:
                base = name + "/3D"
            elif "Default" in names:  # ten rengi varyantlı emojiler
                base = name + "/Default/3D"
            else:
                raise RuntimeError("3D klasörü yok")
            files = api_json(API + urllib.parse.quote(base))
            pngs = [f for f in files if f["name"].lower().endswith(".png")]
            if not pngs:
                raise RuntimeError("3D PNG yok")
            target = pngs[0]
            data = api_raw(target["url"])
            path = os.path.join(out_dir, key + ".png")
            with open(path, "wb") as f:
                f.write(data)
            ok.append((key, name, target["name"], len(data)))
            print("OK  %-12s %-34s %8d B  <- %s" % (key, name, len(data), target["name"]))
        except Exception as exc:  # noqa: BLE001
            bad.append((key, name, str(exc)))
            print("FAIL %-12s %-34s %s" % (key, name, exc))

    print("\n%d indirildi, %d hata" % (len(ok), len(bad)))
    with open(os.path.join(here, "raw", "_manifest.json"), "w") as f:
        json.dump({"ok": ok, "failed": bad}, f, ensure_ascii=False, indent=2)
    for key, name, err in bad:
        print("  eksik:", key, "/", name, "/", err)


if __name__ == "__main__":
    main()
