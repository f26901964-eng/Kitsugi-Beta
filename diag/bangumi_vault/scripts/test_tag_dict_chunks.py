#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""BangumiTagDictionary groups parçalama yardımcılarının birim testleri."""
from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import vault_to_strings as vts


def g(idx: int) -> str:
    return f'        g("tag_{idx}", "TR {idx}", "EN {idx}", "etiket {idx}"),'


class GroupChunkTest(unittest.TestCase):
    def test_single_list_is_chunked(self):
        body = "\n".join(g(i) for i in range(7)).rstrip(",")
        source = ("    private val groups: List<Group> = listOf(\n"
                  + body + "\n    )\n\n    /** sonrası */\n")
        out = vts.ensure_group_chunks(source)
        self.assertEqual(len(re.findall(r"private fun groupChunk\d+\(\)", out)), 1)
        self.assertIn("private val groups: List<Group> =\n        groupChunk01()", out)
        self.assertIn("/** sonrası */", out)
        self.assertEqual(out.count('g("tag_'), 7)

    def test_chunk_lines_split(self):
        lines = [g(i).rstrip(",") for i in range(7)]
        chunks = vts.chunk_group_lines(lines, 4)
        self.assertEqual([len(c) for c in chunks], [4, 3])

    def test_rerender_balances_overflow(self):
        body = "\n".join(g(i) for i in range(9)).rstrip(",")
        source = ("    private val groups: List<Group> =\n        groupChunk01()\n\n"
                  "    private fun groupChunk01(): List<Group> = listOf(\n"
                  + body + "\n    )\n")
        out = vts.rerender_group_chunks(source, 4)
        self.assertEqual(len(re.findall(r"private fun groupChunk\d+\(\)", out)), 3)
        order = re.findall(r'g\("(tag_\d+)"', out)
        self.assertEqual(order, [f"tag_{i}" for i in range(9)])

    def test_ensure_is_idempotent(self):
        body = "\n".join(g(i) for i in range(3)).rstrip(",")
        source = ("    private val groups: List<Group> = listOf(\n"
                  + body + "\n    )\n")
        once = vts.ensure_group_chunks(source)
        self.assertEqual(once, vts.ensure_group_chunks(once))

    def test_no_trailing_comma_before_close(self):
        body = "\n".join(g(i) for i in range(5))
        source = ("    private val groups: List<Group> = listOf(\n"
                  + body.rstrip(",") + "\n    )\n")
        out = vts.ensure_group_chunks(source)
        for block in re.findall(r"listOf\(\n(.*?)\n    \)", out, re.S):
            self.assertFalse(block.rstrip().endswith(","))


if __name__ == "__main__":
    unittest.main()
