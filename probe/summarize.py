#!/usr/bin/env python3
"""Bangumi API yanıtını tek satırlık özetlere indirger (probe workflow'u için).

Kullanım: python3 probe/summarize.py /tmp/b.json
"""
import json
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "/tmp/b.json"
try:
    data = json.load(open(path, encoding="utf-8"))
except Exception as error:  # noqa: BLE001 - probe çıktısı, hata ayrıntısı yeterli
    print("JSON ayrıştırılamadı:", error)
    sys.exit(0)

if isinstance(data, list):
    items = data
    print("dizi:", len(items), "öğe")
else:
    items = data.get("data") or []
    print("total:", data.get("total"), "limit:", data.get("limit"), "offset:", data.get("offset"))

for item in items[:5]:
    if not isinstance(item, dict):
        continue
    rating = item.get("rating") or {}
    name = (item.get("name_cn") or item.get("name") or "")[:36]
    print(
        " -",
        item.get("id"),
        "t%s" % item.get("type"),
        "|",
        name,
        "| score",
        rating.get("score"),
        "rank",
        rating.get("rank"),
        "votes",
        rating.get("total"),
        "| mt",
        ",".join((item.get("meta_tags") or [])[:3]),
        "| platform",
        item.get("platform"),
    )
