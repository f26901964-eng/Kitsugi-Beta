#!/usr/bin/env bash
# Bangumi MASTER sözlükleri — derleme + çalıştırma doğrulaması (TSV kaynaklarıyla).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
UTILS="$ROOT/app/src/main/java/com/kitsugi/animelist/utils"
RES_DIR="$ROOT/app/src/main/resources"
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT

KOTLINC="${KOTLINC:-$(command -v kotlinc || true)}"
if [[ -z "$KOTLINC" ]]; then
  echo "HATA: kotlinc bulunamadı. KOTLINC=/yol/kotlinc ile belirtin." >&2
  exit 2
fi
if ! command -v java >/dev/null && [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

python3 - "$UTILS/BangumiTagDictionary.kt" "$UTILS/BangumiOrgDictionary.kt" \
        "$(dirname "${BASH_SOURCE[0]}")/Main.kt" "$BUILD_DIR" <<'PY'
import re, sys
from pathlib import Path
out = Path(sys.argv[4])
for src in sys.argv[1:4]:
    text = Path(src).read_text(encoding="utf-8")
    text = re.sub(r'^package\s+[\w.]+\s*$', '', text, count=1, flags=re.M)
    (out / Path(src).name).write_text(text, encoding="utf-8")
PY

echo "• derleniyor: $BUILD_DIR"
"$KOTLINC" "$BUILD_DIR"/*.kt -include-runtime -d "$BUILD_DIR/check.jar" 2>&1 | grep -v "^warning:" || true
[[ -f "$BUILD_DIR/check.jar" ]] || { echo "HATA: derleme başarısız (check.jar üretilmedi)." >&2; exit 1; }

# Classpath veri depolarını (`bangumi/tags.tsv`, `bangumi/orgs.tsv`) JAR içine göm
python3 - "$BUILD_DIR/check.jar" "$RES_DIR" <<'PY'
import sys, zipfile
from pathlib import Path
jar_path, res_dir = Path(sys.argv[1]), Path(sys.argv[2])
if res_dir.is_dir():
    with zipfile.ZipFile(jar_path, "a", compression=zipfile.ZIP_DEFLATED) as zf:
        for file_path in sorted(res_dir.rglob("*")):
            if file_path.is_file():
                zf.write(file_path, file_path.relative_to(res_dir).as_posix())
PY

echo "• çalıştırılıyor"
java -jar "$BUILD_DIR/check.jar"
