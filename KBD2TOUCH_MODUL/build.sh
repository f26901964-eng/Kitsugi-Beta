#!/usr/bin/env bash
# =====================================================================
#  Kbd2Touch derleme betigi
#
#  GEREKSINIM: zig (cc alt komutu Android/arm64 cross-derleme yapar)
#     pip install ziglang          # veya: https://ziglang.org/download/
#     Alternatif: Android NDK
#       $NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang
#
#  Kullanim:  ./build.sh
# =====================================================================
set -e
cd "$(dirname "$0")"
SRC=src/kbd2touch.c
OUT_ARM64=system/bin/kbd2touch

mkdir -p system/bin bin

if command -v zig >/dev/null 2>&1; then
    ZIG="zig cc"
elif python3 -m ziglang version >/dev/null 2>&1; then
    ZIG="python3 -m ziglang cc"
else
    echo "HATA: zig bulunamadi. 'pip install ziglang' calistir." >&2
    exit 1
fi

echo "-> arm64 (Android, statik musl) derleniyor..."
$ZIG cc -target aarch64-linux-musl -O2 -static -o "$OUT_ARM64" "$SRC" -lm
chmod 755 "$OUT_ARM64"
cp -f "$OUT_ARM64" bin/kbd2touch.aarch64   # yedek (git'e girmez)

echo "-> yerel test derlemesi (Linux x86_64)..."
if command -v cc >/dev/null 2>&1; then
    cc -O2 -Wall -Wextra -Wno-unused-parameter -Wno-misleading-indentation \
       -o bin/kbd2touch.linux-x86_64-test "$SRC" -lm || true
fi

echo "-> flaşlanabilir zip paketleniyor..."
./pack.sh

ls -l "$OUT_ARM64" bin/ dist/ 2>/dev/null
echo "TAMAM."
