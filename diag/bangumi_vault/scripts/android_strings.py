"""Android resource text escaping shared by the VAULT writer and readers."""
import html
import re


def escape(value: str) -> str:
    out = value.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    out = out.replace("'", "\\'").replace('"', '\\"')
    # Without the escape aapt treats a leading @ or ? as a resource/theme ref.
    if out.startswith(('@', '?')):
        out = '\\' + out
    return out


def unescape(value: str) -> str:
    value = re.sub(r'^\\([@?])', r'\1', value)
    return html.unescape(value.replace("\\'", "'").replace('\\"', '"'))
