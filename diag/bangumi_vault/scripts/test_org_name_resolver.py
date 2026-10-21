import unittest
from org_name_resolver import NameResolver, normalize_name


class ResolverTest(unittest.TestCase):
    def test_normalize(self):
        self.assertEqual(normalize_name('Ｄ.Ｃ. Comics'), normalize_name('dc_comics'))
        self.assertNotEqual(normalize_name('C++'), normalize_name('C'))

    def test_short_cid_is_not_identity(self):
        r = NameResolver({'bangumi_magazine_d': ('Dモーニング', 'Dモーニング')}, {})
        cid, row = r.resolve('studio', 'd', 'アトリエD', 'as_is', 'raw')
        self.assertNotEqual(cid, 'd')
        self.assertEqual(row[:2], ('アトリエD', 'アトリエD'))
        self.assertEqual(row[4], '')

    def test_old_value_match_and_preservation(self):
        r = NameResolver({'bangumi_publisher_dc_comics': ('DC Comics', 'DC Comics')}, {})
        cid, row = r.resolve('studio', 'dc_2', 'D.C. Comics', 'as_is', 'raw')
        self.assertEqual(cid, 'dc_comics')
        self.assertEqual(row, ('DC Comics', 'DC Comics', 'existing',
            'değer eşleşmesi: D.C. Comics → bangumi_publisher_dc_comics',
            'bangumi_publisher_dc_comics'))

    def test_new_twins_share_one_string(self):
        r = NameResolver({}, {})
        first, row = r.resolve('studio', 'tecmo_2', 'Tecmo', 'as_is', 'raw')
        second, other = r.resolve('publisher', 'tecmo_3', 'TECMO', 'as_is', 'raw')
        self.assertEqual(first, second)
        self.assertEqual(other[4], 'bangumi_studio_tecmo_2')
        self.assertEqual(other[:2], row[:2])

    def test_role_canon_collision(self):
        r = NameResolver({'bangumi_studio_x': ('X Studio', 'X Studio')},
            {('publisher', 'x'): ['x', 'publisher', 'Other', 'Other', 'as_is', '', '']})
        cid, row = r.resolve('publisher', 'x', 'X Studio', 'global', 'known')
        self.assertNotEqual(cid, 'x')
        self.assertEqual(row[4], 'bangumi_studio_x')

    def test_unknown_cjk_is_not_romanized(self):
        r = NameResolver({}, {})
        cid, row = r.resolve('studio', 'cjk_123', '未知工房', 'as_is', 'raw')
        self.assertEqual(cid, 'cjk_123')
        self.assertEqual(row[:2], ('未知工房', '未知工房'))

    def test_raw_official_alias_preserves_existing_value(self):
        r = NameResolver({'bangumi_studio_klab': ('KLab株式会社', 'KLab株式会社')}, {})
        cid, row = r.resolve('publisher', 'klab', 'KLab', 'global', 'known',
                             raw_name='KLab株式会社')
        self.assertEqual(row[4], 'bangumi_studio_klab')
        self.assertEqual(row[:2], ('KLab株式会社', 'KLab株式会社'))

    def test_existing_tag_name_is_reused(self):
        r = NameResolver({'bangumi_tag_aki_hata': ('Aki Hata', 'Aki Hata')}, {})
        cid, row = r.resolve('producer', 'aki_hata', 'Aki Hata', 'global', 'known')
        self.assertEqual(cid, 'aki_hata')
        self.assertEqual(row[4], 'bangumi_tag_aki_hata')

    def test_cjk_hash_is_not_identity(self):
        r = NameResolver({'bangumi_producer_cjk_f58aaf': ('風麟', '風麟')}, {})
        cid, row = r.resolve('studio', 'cjk_f58aaf', "Yuke's", 'global', 'known')
        self.assertNotEqual(cid, 'cjk_f58aaf')
        self.assertEqual(row[0], "Yuke's")
        self.assertEqual(row[4], '')


if __name__ == '__main__':
    unittest.main()
