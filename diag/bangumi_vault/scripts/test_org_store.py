#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""build_org_store.py — kurum TSV sözlük deposunun kayıpsız olduğunu doğrular."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_org_store as store


class OrgStoreRoundTripTest(unittest.TestCase):
    def test_store_parse_render_birebir(self) -> None:
        raw_text = store.STORE.read_text(encoding="utf-8")
        rows = store.parse_store(raw_text)
        self.assertGreaterEqual(len(rows), 52_176)
        self.assertEqual(store.validate(rows), [])
        self.assertEqual(raw_text, store.render(rows))

    def test_tr_en_farki_korunur(self) -> None:
        sample = [
            ["BONES", "bangumi_studio_bones", "BONES"],
            ["木曜ドラマ", "bangumi_network_thursday_drama", "Thursday Drama", "Perşembe Draması"],
        ]
        self.assertEqual(store.validate(sample), [])
        parsed = store.parse_store(store.render(sample))
        self.assertEqual(parsed, sample)

    def test_dogrulama_hatalari(self) -> None:
        self.assertTrue(store.validate([["tek", "iki"]]))
        self.assertTrue(store.validate([["", "bangumi_studio_a", "A"]]))
        self.assertTrue(store.validate([["A", "bangumi_studio_a", "A"], ["A", "bangumi_studio_b", "B"]]))


if __name__ == "__main__":
    unittest.main()
