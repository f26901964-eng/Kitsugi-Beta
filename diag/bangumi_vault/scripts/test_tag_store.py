#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""build_tag_store.py — TSV sözlük deposunun kayıpsız olduğunu doğrular."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_tag_store as store
import tag_index


class TagStoreRoundTripTest(unittest.TestCase):
    def test_kotlin_tablosu_tsv_ile_birebir(self) -> None:
        rows = store.rows_from_kotlin()
        parse_back = store.parse_store(store.render(rows))
        self.assertEqual(rows, parse_back, "TSV yaz/oku turu kayıpsız olmalı")

    def test_grup_sirasi_ve_icerigi_korunur(self) -> None:
        groups = tag_index.load_dictionary()
        rows = store.rows_from_kotlin()
        self.assertEqual(len(groups), len(rows))
        for group, row in zip(groups, rows):
            self.assertEqual(group["id"], row[0])
            self.assertEqual(group["tr"], row[1])
            self.assertEqual(group["en"], row[2])
            self.assertEqual(group["labels"], row[3:])

    def test_dogrulama_hatalari(self) -> None:
        self.assertEqual(store.validate([["a", "A", "A", "x"]]), [])
        self.assertTrue(store.validate([["a", "A"]]))
        self.assertTrue(store.validate([["a", "", "A"]]))
        self.assertTrue(store.validate([["a", "A", "A"], ["a", "B", "B"]]))
        self.assertTrue(store.validate([["a", "A", "A", "x\ty"]]))

    def test_ilk_yazim_kazanir_sirasi_korunur(self) -> None:
        """Aynı normalize etiketi paylaşan iki grup varsa önce gelen kazanır."""
        groups = tag_index.load_dictionary()
        first: dict[str, str] = {}
        shadowed = 0
        for group in groups:
            for label in group["labels"]:
                key = tag_index.normalize(label)
                if not key:
                    continue
                if key in first:
                    if first[key] != group["id"]:
                        shadowed += 1
                    continue
                first[key] = group["id"]
        self.assertGreater(shadowed, 0, "gölgelenme vakası yoksa sıra testi anlamsız")
        rows = store.rows_from_kotlin()
        replay: dict[str, str] = {}
        for row in rows:
            for label in row[3:]:
                key = tag_index.normalize(label)
                if key and key not in replay:
                    replay[key] = row[0]
        self.assertEqual(first, replay, "TSV sırası Kotlin sırasıyla aynı sonucu vermeli")


if __name__ == "__main__":
    unittest.main()
