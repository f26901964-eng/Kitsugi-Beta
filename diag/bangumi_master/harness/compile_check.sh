#!/usr/bin/env bash
# Bangumi MASTER sözlükleri için bağımsız kotlinc derleme denetimi.
#
# Neden gerekli: Python doğrulayıcı sözdizimini GÖRMEZ. Geçmişte yaşanan kritik
# hata tam olarak buydu — yeni g(...) bloğu listenin son elemanının ardına
# virgülsüz eklenmişti, sözlük derlenmiyordu ve bunu yalnızca kotlinc yakaladı.
#
# Kullanım:
#   bash diag/bangumi_master/harness/compile_check.sh
#   KOTLINC=/yol/kotlinc bash diag/bangumi_master/harness/compile_check.sh
#
# Bu betik Android SDK istemez: derlenen dosyalar saf JVM Kotlin'dir
# (sözlükler + test), Android API'sine dokunmazlar.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
UTILS="$ROOT/app/src/main/java/com/kitsugi/animelist/utils"
TEST_SRC="$ROOT/app/src/test/java/com/kitsugi/animelist/utils/BangumiMasterDictionaryTest.kt"
OUT="${TMPDIR:-/tmp}/bangumi_master_compile"

KOTLINC="${KOTLINC:-$(command -v kotlinc || true)}"
if [ -z "$KOTLINC" ]; then
  echo "HATA: kotlinc bulunamadı." >&2
  echo "  Kurulum: https://github.com/JetBrains/kotlin/releases (kotlin-compiler-*.zip)" >&2
  echo "  Sonra:   KOTLINC=/yol/kotlinc bash $0" >&2
  exit 2
fi

echo "=== Bangumi MASTER derleme denetimi ==="
echo "kotlinc: $KOTLINC"
"$KOTLINC" -version 2>&1 | head -1

rm -rf "$OUT"
mkdir -p "$OUT"

# 1) Sözlükler tek başına derlenmeli (Android/Compose bağımlılığı yok).
echo "--- sözlükler ---"
"$KOTLINC" -nowarn -d "$OUT" \
  "$UTILS/BangumiOrgDictionary.kt" \
  "$UTILS/BangumiTagDictionary.kt"
echo "✅ sözlükler derlendi"

# 2) Test dosyası JUnit ile derlenip koşturulmalı.
JUNIT_JAR="${JUNIT_JAR:-}"
if [ -n "$JUNIT_JAR" ] && [ -f "$TEST_SRC" ]; then
  echo "--- test ---"
  "$KOTLINC" -nowarn -cp "$JUNIT_JAR" -d "$OUT" \
    "$UTILS/BangumiOrgDictionary.kt" \
    "$UTILS/BangumiTagDictionary.kt" \
    "$TEST_SRC"
  echo "✅ test derlendi"
else
  echo "⚠ JUnit_JAR verilmedi ya da test yok → test derlemesi atlandı"
  echo "  (tam koşum için: ./gradlew :app:testDebugUnitTest --tests '*BangumiMasterDictionaryTest*')"
fi

echo "SONUÇ: DERLEME GEÇTİ"
