#!/usr/bin/env python3
"""Yalnızca `total` alanını yazdırır (etiket kapsama testi için)."""
import json
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "/tmp/b.json"
try:
    data = json.load(open(path, encoding="utf-8"))
except Exception as error:  # noqa: BLE001
    print("JSON hatası:", error)
    sys.exit(0)
if isinstance(data, dict):
    print("total:", data.get("total"), "veri:", len(data.get("data") or []))
else:
    print("dizi:", len(data))
