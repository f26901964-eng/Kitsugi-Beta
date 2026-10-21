#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""BangumiTagDictionary groups parçalama yardımcılarının birim testleri.

`vault_to_strings.py` içe aktarıldığında dosya yazar; bu yüzden yalnız ilgili
yardımcı bloğu kaynaktan çıkarılıp yalıtılmış bir ad alanında çalıştırılır.
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path

SOURCE = Path(__file__).with_name("vault_to_strings.py").read_text(encoding="utf-8")
START = SOURCE.index("# ── groups listesi")
END = SOURCE.index("def patch_tag_dict(")
BLOCK = SOURCE[START:END]

ns: dict = {"re": re, "__name__": "tag_dict_chunks"}
exec(compile(BLOCK, "vault_to_strings.groups", "exec"), ns)


def g(idx: int) -> str:
    return f'        g("tag_{idx}", "TR {idx}", "EN {idx}", "etiket {idx}"),'


class GroupChunkTest(unittest.TestCase):
    def test_single_list_is_chunked(self):
        body = "\n".join(g(i) for i in range(7)).rstrip(",")
        source = ("    private val groups: List<Group> = listOf(\n"
                  + body + "\n    )\n\n    /** sonrası */\n")
        out = ns["ensure_group_chunks"](source)
        self.assertEqual(len(re.findall(r"private fun groupChunk\d+\(\)", out)), 1)
        self.assertIn("private val groups: List<Group> =\n        groupChunk01()", out)
        self.assertIn("/** sonrası */", out)
        self.assertEqual(out.count('g("tag_'), 7)

    def test_chunk_lines_split(self):
        lines = [g(i).rstrip(",") for i in range(7)]
        chunks = ns["chunk_group_lines"](lines, 4)
        self.assertEqual([len(c) for c in chunks], [4, 3])

    def test_rerender_balances_overflow(self):
        body = "\n".join(g(i) for i in range(9)).rstrip(",")
        source = ("    private val groups: List<Group> =\n        groupChunk01()\n\n"
                  "    private fun groupChunk01(): List<Group> = listOf(\n"
                  + body + "\n    )\n")
        out = ns["rerender_group_chunks"](source, 4)
        self.assertEqual(len(re.findall(r"private fun groupChunk\d+\(\)", out)), 3)
        order = re.findall(r'g\("(tag_\d+)"', out)
        self.assertEqual(order, [f"tag_{i}" for i in range(9)])

    def test_ensure_is_idempotent(self):
        body = "\n".join(g(i) for i in range(3)).rstrip(",")
        source = ("    private val groups: List<Group> = listOf(\n"
                  + body + "\n    )\n")
        once = ns["ensure_group_chunks"](source)
        self.assertEqual(once, ns["ensure_group_chunks"](once))

    def test_no_trailing_comma_before_close(self):
        body = "\n".join(g(i) for i in range(5))
        source = ("    private val groups: List<Group> = listOf(\n"
                  + body.rstrip(",") + "\n    )\n")
        out = ns["ensure_group_chunks"](source)
        for block in re.findall(r"listOf\(\n(.*?)\n    \)", out, re.S):
            self.assertFalse(block.rstrip().endswith(","))


if __name__ == "__main__":
    unittest.main()
