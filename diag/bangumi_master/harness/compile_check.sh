#!/usr/bin/env bash
# Bangumi MASTER sözlükleri — derleme + çalıştırma doğrulaması.
#
# Ne yapar:
#   1. BangumiTagDictionary.kt + BangumiOrgDictionary.kt dosyalarının PRISTINE kopyasını alır
#      (yalnızca `package` satırı kaldırılır — kotlinc paket/dizin uyuşmazlığına izin verir,
#      ama bağımlılıksız tek-main derlemesi paketsiz daha basit).
#   2. harness/Main.kt (JUnit testinin aynısı, bağımlılıksız) ile birlikte derler.
#   3. Çalıştırır; tüm kontroller geçerse "✅ TÜM KONTROLLER GEÇTİ (0 hata)" yazar, exit 0.
#
# Gereksinimler:
#   - kotlinc 2.x  (PATH'te ya da KOTLINC=/yol/kotlinc ile)
#   - JDK 17+      (PATH'te java ya da JAVA_HOME tanımlı)
#
# Kullanım:
#   bash diag/bangumi_master/harness/compile_check.sh
#
# Not (2026-10-11): Bu harness ilk kez çalıştırıldığında `instrumental` satırından sonra
# virgül eksikliğini yakaladı (yeni blok listSon elemanın hemen ardına eklenmişti).
# Üretici (gen_bangumi_master.py) artık ekleme öncesi virgülü otomatik tamamlıyor.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
UTILS="$ROOT/app/src/main/java/com/kitsugi/animelist/utils"
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

echo "• çalıştırılıyor"
java -jar "$BUILD_DIR/check.jar"
