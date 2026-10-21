import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import xml.etree.ElementTree as ET
from resource_catalog import (MODULES, resource_paths, new_resource_path,
                              catalog_text, validate_catalog)


class ResourceCatalogTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.app = self.root / 'app/src/main/res/values/strings.xml'
        for lang in ('values', 'values-en'):
            app = self.root / f'app/src/main/res/{lang}/strings.xml'
            for i, p in enumerate(resource_paths(app)):
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(f'<resources><string name="name_{i}">Value {i}</string></resources>')
        (self.root / 'settings.gradle.kts').write_text(''.join(f'include(":{m}")\n' for m in MODULES))
        (self.root / 'app/build.gradle.kts').write_text(''.join(f'implementation(project(":{m}"))\n' for m in MODULES))
        (self.root / 'gradle.properties').write_text('android.nonTransitiveRClass=true\n')
        keep = self.root / 'app/src/main/res/raw/bangumi_vault_keep.xml'
        keep.parent.mkdir(parents=True)
        keep.write_text('<resources tools:keep="@string/bangumi_*" />')

    def test_combined_catalog_and_gradle_wiring(self):
        self.assertEqual(len(ET.fromstring(catalog_text(self.app)).findall('string')), 4)
        self.assertEqual(validate_catalog(self.app), [])

    def test_language_independent_stable_shard(self):
        tr = new_resource_path(self.app, 'bangumi_studio_example')
        en = new_resource_path(self.app.parent.parent / 'values-en/strings.xml',
                               'bangumi_studio_example')
        self.assertEqual(tr.parents[4], en.parents[4])
        self.assertIn(tr, resource_paths(self.app)[1:])

    def test_duplicate_and_mismatched_translation_detected(self):
        resource_paths(self.app)[1].write_text('<resources><string name="name_0">Duplicate</string></resources>')
        errors = '\n'.join(validate_catalog(self.app))
        self.assertIn('duplicate resource across modules', errors)
        self.assertIn('TR/EN shard keys differ', errors)

    def test_r_class_capacity_guard(self):
        with patch('resource_catalog.MAX_SHARD_STRINGS', 0):
            self.assertTrue(any('R$string class size guard' in e for e in validate_catalog(self.app)))

    def test_nontransitive_r_is_mandatory(self):
        (self.root / 'gradle.properties').write_text('android.nonTransitiveRClass=false\n')
        self.assertTrue(any('requires android.nonTransitiveRClass=true' in e
                            for e in validate_catalog(self.app)))


if __name__ == '__main__':
    unittest.main()
