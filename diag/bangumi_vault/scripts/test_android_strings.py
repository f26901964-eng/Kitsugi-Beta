import unittest
from android_strings import escape, unescape


class AndroidStringsTest(unittest.TestCase):
    def test_leading_resource_markers(self):
        for value in ('@nifty', '@sel_ple', '?name'):
            self.assertTrue(escape(value).startswith('\\'))
            self.assertEqual(unescape(escape(value)), value)

    def test_xml_and_apostrophe_roundtrip(self):
        for value in ("Yuke's", 'A & B', '<studio>', '"Name"', '日本語', '100%'):
            self.assertEqual(unescape(escape(value)), value)

    def test_embedded_at_is_not_a_reference(self):
        self.assertEqual(escape('studio@example'), 'studio@example')

    def test_existing_unescaped_values_read_identically(self):
        self.assertEqual(unescape('@OZ'), unescape('\\@OZ'))


if __name__ == '__main__':
    unittest.main()
