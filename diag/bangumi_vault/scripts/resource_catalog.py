"""Merged Android strings across the app and non-transitive resource-only shards.

Keep generated R$string classes small; resource names remain globally unique and
still resolve through Resources.getIdentifier(name, "string", appPackage).
"""
import hashlib
import re
from pathlib import Path

MODULES = tuple(f'bangumi-vault-resources-{i}' for i in range(1, 4))
MAX_SHARD_STRINGS = 16_000
MAX_APP_STRINGS = 20_000


def resource_paths(app_file: Path) -> list[Path]:
    root = app_file.parents[5]
    lang = app_file.parent.name
    return [app_file] + [root / module / 'src/main/res' / lang / 'strings.xml'
                         for module in MODULES]


def catalog_text(app_file: Path) -> str:
    bodies = []
    for path in resource_paths(app_file):
        if not path.exists():
            continue
        source = path.read_text(encoding='utf-8')
        match = re.search(r'<resources\b[^>]*>(.*)</resources>', source, re.S)
        if not match:
            raise ValueError(f'invalid resource XML wrapper: {path}')
        bodies.append(match[1])
    return '<resources>\n' + '\n'.join(bodies) + '\n</resources>\n'


def new_resource_path(app_file: Path, key: str) -> Path:
    index = int(hashlib.sha256(key.encode()).hexdigest()[:8], 16) % len(MODULES)
    return resource_paths(app_file)[index + 1]


def validate_catalog(app_file: Path) -> list[str]:
    """Guard class-file size, per-language symmetry, duplicates and Gradle wiring."""
    errors = []
    root = app_file.parents[5]
    tr = resource_paths(app_file)
    en = resource_paths(app_file.parent.parent / 'values-en/strings.xml')
    seen = {}
    for i, (tr_file, en_file) in enumerate(zip(tr, en)):
        sets = []
        for path in (tr_file, en_file):
            if not path.exists():
                errors.append(f'missing resource shard: {path}')
                sets.append(set())
                continue
            keys = re.findall(r'<string name="([^"]+)"', path.read_text())
            if len(keys) != len(set(keys)):
                errors.append(f'duplicate resource name inside {path}')
            sets.append(set(keys))
        if sets[0] != sets[1]:
            errors.append(f'TR/EN shard keys differ: {tr_file}')
        for key in sets[0]:
            if key in seen:
                errors.append(f'duplicate resource across modules: {key}')
            seen[key] = tr_file
        module = root / ('app' if i == 0 else MODULES[i - 1])
        all_strings = set()
        for path in module.glob('src/*/res/values*/*.xml'):
            all_strings.update(re.findall(r'<string\s+name="([^"]+)"', path.read_text()))
        limit = MAX_APP_STRINGS if i == 0 else MAX_SHARD_STRINGS
        if len(all_strings) > limit:
            errors.append(f'R$string class size guard: {module.name}: {len(all_strings)} > {limit}')
    properties = (root / 'gradle.properties').read_text()
    if not re.search(r'^android.nonTransitiveRClass=true\s*$', properties, re.M):
        errors.append('resource sharding requires android.nonTransitiveRClass=true')
    settings = (root / 'settings.gradle.kts').read_text()
    dependencies = (root / 'app/build.gradle.kts').read_text()
    for module in MODULES:
        if f'include(":{module}")' not in settings or f'project(":{module}")' not in dependencies:
            errors.append(f'resource module not wired: {module}')
    keep = root / 'app/src/main/res/raw/bangumi_vault_keep.xml'
    if not keep.exists() or 'tools:keep="@string/bangumi_*"' not in keep.read_text():
        errors.append('dynamic Bangumi resource names must be retained by release shrinker')
    return errors
