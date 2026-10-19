#!/usr/bin/env python3
"""Reproducible source inventory, NOT a functional/player parity test.

Usage: python3 scripts/audit_player_comparison.py --reference /path/to/aniyomi
The reference checkout stays outside this repository. No network or build required.
"""
import argparse
import hashlib
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SYMBOL = re.compile(r"\bfun\s+(?:<[^>]+>\s*)?(\w+)\s*\(")


def git_head(path):
    return subprocess.check_output(["git", "-C", str(path), "rev-parse", "HEAD"], text=True).strip()


def collect(root, directories):
    paths = set()
    for directory in directories:
        path = root / directory
        if not path.exists():
            raise ValueError(f"Missing inventory input: {path}")
        if path.is_file():
            paths.add(path)
        else:
            paths.update(path.rglob("*.kt"))
    result = []
    for path in sorted(paths):
        data = path.read_bytes()
        lines = data.decode("utf-8-sig").splitlines()
        symbols = [(i, match.group(1)) for i, line in enumerate(lines, 1)
                   if not line.strip().startswith(("//", "*"))
                   for match in SYMBOL.finditer(line)]
        result.append((path.relative_to(root).as_posix(), len(lines), hashlib.sha256(data).hexdigest(), symbols))
    return result


def render_section(title, records, base):
    output = [f"## {title}", "", "Dosya SHA-256 değerleri incelenen anı sabitler; sembol taraması davranış doğrulaması değildir.", ""]
    for path, count, digest, symbols in records:
        output.extend([f"### `{path}`", "", f"{count} satır · SHA-256 `{digest}`", ""])
        if symbols:
            output.append(" · ".join(f"[`{name}` L{line}]({base}{path}#L{line})" for line, name in symbols))
        else:
            output.append("Fonksiyon bildirimi yok (model / enum / sabitler olabilir).")
        output.append("")
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "docs/ANIYOMI_PLAYER_SOURCE_INVENTORY_2026-10-19.md")
    args = parser.parse_args()
    reference = args.reference.resolve()
    ar = "app/src/main/java/eu/kanade/tachiyomi/ui/player"
    kr = "app/src/main/java/com/kitsugi/animelist"
    upstream = collect(reference, [ar, "app/src/main/java/eu/kanade/presentation/more/settings/screen/player"])
    local = collect(ROOT, [f"{kr}/core/player", f"{kr}/ui/screens/fullscreen", f"{kr}/ui/components/player", f"{kr}/ui/tv/player", f"{kr}/ui/screens/offline", f"{kr}/core/p2p", f"{kr}/data/settings/AppSettings.kt", f"{kr}/data/settings/SettingsDataStore.kt", f"{kr}/ui/components/KitsugiPlayerSettingsDialog.kt", f"{kr}/ui/app/PlayerSettingsViewModel.kt"])
    preferences = []
    for path in sorted((reference / ar / "settings").glob("*.kt")):
        for number, line in enumerate(path.read_text().splitlines(), 1):
            if match := re.search(r"^\s*fun (\w+)\(\)\s*=\s*preferenceStore\.", line):
                preferences.append((path.relative_to(reference).as_posix(), number, match.group(1)))
    # A coverage check: every upstream preference must have an explicit row in the
    # manually reviewed matrix. Presence of a row says nothing about correctness.
    matrix = ROOT / "docs/ANIYOMI_KITSUGI_PLAYER_FULL_COMPARISON_2026-10-19.md"
    content = matrix.read_text()
    missing = [name for _, _, name in preferences
               if len(re.findall(r"^\| `" + re.escape(name) + r"` \|", content, re.MULTILINE)) != 1]
    if missing:
        raise ValueError(f"Unclassified upstream preferences: {missing}")
    commit = git_head(reference)
    base = f"https://github.com/aniyomiorg/aniyomi/blob/{commit}/"
    lines = ["# Oynatıcı karşılaştırması — kaynak envanteri", "", f"Referans commit: `{commit}`", f"Kitsugi tabanı: `{git_head(ROOT)}` (çalışma ağacı değişiklikleri dahil).", "", f"Aniyomi: {len(upstream)} Kotlin dosyası / {sum(r[1] for r in upstream)} satır.", f"Kitsugi seçili oynatıcı dizinleri: {len(local)} Kotlin dosyası / {sum(r[1] for r in local)} satır.", f"Referans oynatıcı tercihlerinin {len(preferences)}/{len(preferences)} tanesi ana matriste isim bazında yer alıyor.", "", "Bu sayılar otomatik envanter kapsamıdır; her satırın manuel incelendiğini veya test edildiğini ifade etmez.", "", "## Tercih kapsama listesi", ""]
    lines += [f"- [`{name}`]({base}{path}#L{number})" for path, number, name in preferences]
    lines += [""] + render_section("Aniyomi", upstream, base)
    lines += render_section("Kitsugi", local, "../")
    lines += ["## Zincir arama kanıtları", "", "Aşağıdakiler tüm main Kotlin kaynaklarında isim bazlı referanslardır. Tanım/import, çağrı anlamına gelmez. Satır başı yorumları dışlanır; bu bir Kotlin AST/call-graph analizi değildir.", ""]
    source = [(p.relative_to(ROOT).as_posix(), p.read_text().splitlines())
              for p in sorted((ROOT / kr).rglob("*.kt"))]
    terms = ["updateChapters", "updateChapter", "updateReadAhead", "selectSecondarySubtitleTrack",
             "PreviewGenerator", "applyMpvTlsOptions", "subtitleItalic", "subtitleBackgroundColor",
             "subtitleBorderColor", "subtitleFontScale", "subtitleOverrideAss", "playerStatisticsPage",
             "preferredAudioLanguages", "requestAudioFocus", "onSeekTo"]
    for term in terms:
        pattern = re.compile(r"\b" + re.escape(term) + r"\b")
        hits = [(path, i, line.strip()) for path, text in source for i, line in enumerate(text, 1)
                if not line.strip().startswith(("//", "*")) and pattern.search(line)]
        lines += [f"### `{term}` — {len(hits)} referans", ""]
        lines += [f"- [{path}:{i}](../{path}#L{i}): `{line.replace('`', chr(39))}`" for path, i, line in hits]
        if not hits:
            lines.append("Main Kotlin kaynaklarında bu isimde referans yok; bu sonuç eşdeğer davranışın yokluğunu tek başına kanıtlamaz.")
        lines.append("")
    lines += ["### Lua asset dosyaları", ""]
    lua = sorted((ROOT / "app/src").rglob("*.lua"))
    lines += [f"- `{p.relative_to(ROOT)}`" for p in lua] if lua else ["app/src altında .lua dosyası bulunmadı. Runtime üretilen custombuttons.lua bu listeye dahil değildir."]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines) + "\n")
    print(f"Inventory: upstream={len(upstream)} files, kitsugi={len(local)} files; preference rows={len(preferences)}/{len(preferences)}")
    print(f"Written: {args.output.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
