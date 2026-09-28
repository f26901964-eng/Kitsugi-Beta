import os
import sys
import io
import shutil

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

CLIENT_DIR    = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"
SOURCE_SERVER = r"C:\Users\Administrator\Downloads\MC-1211-ModPaketi-DOSYALAR (6)\mods-server"
SOURCE_CLIENT = r"C:\Users\Administrator\Downloads\MC-1211-ModPaketi-DOSYALAR (6)\mods-client"

# Gerçek dosya adlarına göre eksikler (parity scriptten)
TO_COPY = [
    ("c2me-neoforge-opts-accel-opencl-mc1.21.1-0.4.0-alpha.0.116.jar", SOURCE_SERVER),
    ("configurable-3.5.2+1.21.1-neoforge.jar", SOURCE_SERVER),
    ("lithostitched-1.7.13-neoforge-21.1.jar", SOURCE_SERVER),
]
# GoblinTraders-test.jar -> bu bir test sürümü, skip edelim

SKIP_REASON = {
    "GoblinTraders-test.jar": "Test/geliştirme sürümü, istemciye kopyalanmıyor."
}

copied = 0
skipped = 0
errors = 0

print("=== EKSİK MODLAR İSTEMCİYE KOPYALANIYOR ===\n")

for filename, source_dir in TO_COPY:
    src_path = os.path.join(source_dir, filename)
    dst_path = os.path.join(CLIENT_DIR, filename)

    if not os.path.exists(src_path):
        # Try client folder too
        alt = os.path.join(SOURCE_CLIENT, filename)
        if os.path.exists(alt):
            src_path = alt
        else:
            print(f"[HATA] Kaynak bulunamadı: {filename}")
            errors += 1
            continue

    if os.path.exists(dst_path):
        print(f"[VAR] Zaten mevcut: {filename}")
        skipped += 1
        continue

    try:
        shutil.copy2(src_path, dst_path)
        size_kb = os.path.getsize(dst_path) // 1024
        print(f"[OK] Kopyalandı: {filename} ({size_kb} KB)")
        copied += 1
    except Exception as e:
        print(f"[HATA] {filename}: {e}")
        errors += 1

print()
for fname, reason in SKIP_REASON.items():
    print(f"[ATLA] {fname} -> {reason}")
    skipped += 1

print(f"\n=== ÖZET ===")
print(f"Kopyalanan : {copied}")
print(f"Atlanan    : {skipped}")
print(f"Hata       : {errors}")

total = len(os.listdir(CLIENT_DIR))
print(f"\nİstemci mods klasöründe toplam JAR: {total}")
print("\nİstemci klasörü güncel! ✅")
