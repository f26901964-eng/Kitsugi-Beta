#!/usr/bin/env python3
"""Inventory the pinned Kotatsu TR parser source, NOT a live website health test.
Usage: python3 scripts/audit_manga_parsers.py /path/to/kotatsu-parsers-redo > docs/MANGA_TR_PARSER_INVENTORY.md
"""
import re
import sys
from pathlib import Path

root = Path(sys.argv[1]) / 'src/main/kotlin/org/koitharu/kotatsu/parsers'
if not root.is_dir():
    raise SystemExit('Expected an extracted kotatsu-parsers-redo source tree')
rows = []
for path in sorted((root / 'site').rglob('*.kt')):
    text = path.read_text()
    declarations = re.findall(r'@MangaSourceParser\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"tr"', text)
    for source_id, name in declarations:
        rel = path.relative_to(root).as_posix()
        family = rel.split('/')[1]
        if family == 'tr':
            family = 'custom / ' + path.stem
        domains = re.findall(r'"((?:[a-z0-9-]+\.)+[a-z]{2,}(?:/[a-z0-9/_-]*)?)"', text)
        rows.append((source_id, name, family, ', '.join(dict.fromkeys(domains)) or 'inherited/config', rel))
print('# Türkçe Kotatsu parser envanteri\n')
print('Pin: `f287c414a6` (app/build.gradle.kts). Otomatik statik envanter; **canlı çalışma onayı değildir**.\n')
print(f'Türkçe parser bildirimi: **{len(rows)}**. Bunların tümü kullanıcı cihazında etkin/yüklü olmak zorunda değildir.\n')
print('Alan adları yalnızca ilgili Kotlin dosyasındaki sabitlerden çıkarılır; güncel DNS veya ayna doğrulaması değildir. Ortak motor kodları ayrıca incelenmelidir.\n')
print('| ID | Kaynak | Motor ailesi | Dosyadaki alan adları | Parser kodu |')
print('|---|---|---|---|---|')
for sid, name, family, domains, rel in rows:
    url = f'https://github.com/Kotatsu-Redo/kotatsu-parsers-redo/blob/f287c414a6/src/main/kotlin/org/koitharu/kotatsu/parsers/{rel}'
    print(f'| {sid} | {name} | {family} | {domains} | [kod]({url}) |')
