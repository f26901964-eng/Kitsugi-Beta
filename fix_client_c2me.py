# -*- coding: utf-8 -*-
"""
fix_client_c2me.py
==================
c2me-opts-accel-opencl istemci mods klasöründen kaldırır.
Bu mod Java 25 ile derlenmiş, client Java 21 ile çalışıyor — crash sebebi bu.
"""
import os
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

CLIENT_MODS = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"

print("=" * 60)
print("  CLIENT FIX: Java sürüm uyumsuz modlar kaldırılıyor")
print("=" * 60)

# Java 25 gerektiren (class file 69.0) - client Java 21'de çalışmaz
JAVA25_MODS = [
    "c2me-neoforge-opts-accel-opencl-mc1.21.1-0.4.0-alpha.0.116.jar",
]

if not os.path.isdir(CLIENT_MODS):
    print(f"HATA: Klasor bulunamadi: {CLIENT_MODS}")
    sys.exit(1)

existing = os.listdir(CLIENT_MODS)
removed = 0

for mod in JAVA25_MODS:
    if mod in existing:
        path = os.path.join(CLIENT_MODS, mod)
        print(f"  Kaldiriliyor (Java 25 gerektirir, Java 21 ile calismiyor):")
        print(f"    {mod}")
        try:
            os.remove(path)
            removed += 1
            print(f"    OK silindi.")
        except Exception as e:
            print(f"    HATA: {e}")
    else:
        # Prefix ile de ara
        for f in existing:
            if "c2me" in f.lower() and "opencl" in f.lower() and f.endswith(".jar"):
                path = os.path.join(CLIENT_MODS, f)
                print(f"  Kaldiriliyor: {f}")
                try:
                    os.remove(path)
                    removed += 1
                    print(f"    OK silindi.")
                except Exception as e:
                    print(f"    HATA: {e}")

# Kalan c2me modlarını kontrol et
print("\n  Kalan c2me modları:")
for f in os.listdir(CLIENT_MODS):
    if "c2me" in f.lower():
        size = os.path.getsize(os.path.join(CLIENT_MODS, f))
        print(f"    {f} ({size/1024/1024:.1f} MB)")

if removed == 0:
    print("\n  Zaten temiz - opencl jar bulunamadi (muhtemelen onceden silindi).")
else:
    print(f"\n  OK: {removed} mod kaldirildi.")

print("\n  Simdi Minecraft'i yeniden baslat!")
print("  Kalan hatalar:")
print("    - logs/latest.log Stream Closed -> Zararsiz, kendisi duzeltir")
print("    - Essential Connection timed out -> Zararsiz, oyun acar")
print("=" * 60)
