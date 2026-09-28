# -*- coding: utf-8 -*-
"""
fix_cyclic_client.py
====================
cyclic-1.14.2'yi client'tan kaldirir (NF 21.1.241 gerektirir)
cyclic-1.14.1'i Modrinth'ten indirip koyar (NF 21.1.238 ile uyumlu)
"""
import os, sys, io, urllib.request, json

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

CLIENT_MODS = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"

print("=" * 60)
print("  FIX: cyclic 1.14.2 -> 1.14.1 (NeoForge 21.1.238 uyumlu)")
print("=" * 60)

# 1. Uyumsuz sürümü sil
bad = "cyclic-1.21.1-1.14.2.jar"
bad_path = os.path.join(CLIENT_MODS, bad)
if os.path.exists(bad_path):
    os.remove(bad_path)
    print(f"\n[1] Silindi: {bad}")
else:
    print(f"\n[1] Zaten yok: {bad}")

# 2. Eski uyumlu sürümü indir
good = "cyclic-1.21.1-1.14.1.jar"
good_path = os.path.join(CLIENT_MODS, good)

if os.path.exists(good_path):
    print(f"[2] Zaten mevcut: {good} — atlanıyor.")
else:
    print(f"\n[2] Modrinth'ten indiriliyor: {good}...")
    # Modrinth API - cyclic projesi tum versiyonlari
    api = "https://api.modrinth.com/v2/project/cyclic/version?game_versions=%5B%221.21.1%22%5D&loaders=%5B%22neoforge%22%5D"
    try:
        req = urllib.request.Request(api, headers={"User-Agent": "KitsugiModpack/1.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            versions = json.loads(resp.read())

        # 1.14.1 sürümünü bul
        target_url = None
        target_fname = None
        for v in versions:
            vnum = v.get('version_number', '')
            if '1.14.1' in vnum:
                for f in v.get('files', []):
                    fname = f.get('filename', '')
                    if fname.endswith('.jar'):
                        target_url = f['url']
                        target_fname = fname
                        break
            if target_url:
                break

        # 1.14.1 yoksa en eski uyumlu sürümü al (son eleman = en eski)
        if not target_url:
            print("  1.14.1 bulunamadi, en eski uyumlu sürüm aranıyor...")
            for v in reversed(versions):
                for f in v.get('files', []):
                    fname = f.get('filename', '')
                    if fname.endswith('.jar'):
                        target_url = f['url']
                        target_fname = fname
                        print(f"  Alternatif bulundu: {fname}")
                        break
                if target_url:
                    break

        if target_url:
            dst = os.path.join(CLIENT_MODS, target_fname)
            req2 = urllib.request.Request(target_url, headers={"User-Agent": "KitsugiModpack/1.0"})
            with urllib.request.urlopen(req2, timeout=60) as resp, open(dst, 'wb') as out:
                total = 0
                while True:
                    chunk = resp.read(65536)
                    if not chunk:
                        break
                    out.write(chunk)
                    total += len(chunk)
                    print(f"\r  {total/1024/1024:.2f} MB...", end='', flush=True)
            print(f"\n  OK: {target_fname} indirildi ({total/1024/1024:.2f} MB)")
            good_path = dst
        else:
            print("  HATA: Uygun cyclic surumu bulunamadi!")

    except Exception as e:
        print(f"  Modrinth hatasi: {e}")
        # CurseForge CDN fallback
        fallback_url = "https://edge.forgecdn.net/files/6063/908/cyclic-1.21.1-1.14.1.jar"
        print(f"  CurseForge fallback deneniyor...")
        try:
            req = urllib.request.Request(fallback_url, headers={"User-Agent": "KitsugiModpack/1.0"})
            with urllib.request.urlopen(req, timeout=60) as resp, open(good_path, 'wb') as out:
                total = 0
                while True:
                    chunk = resp.read(65536)
                    if not chunk:
                        break
                    out.write(chunk)
                    total += len(chunk)
            print(f"  OK: {total/1024/1024:.2f} MB indirildi (fallback)")
        except Exception as e2:
            print(f"  Fallback da basarisiz: {e2}")
            sys.exit(1)

# 3. Durumu göster
print("\n[3] Client mods klasöründeki cyclic dosyaları:")
for f in sorted(os.listdir(CLIENT_MODS)):
    if 'cyclic' in f.lower():
        sz = os.path.getsize(os.path.join(CLIENT_MODS, f))
        print(f"    {f} ({sz/1024/1024:.2f} MB) OK")

print("\n" + "=" * 60)
print("  TAMAMLANDI! Minecraft'i yeniden baslat.")
print("=" * 60)
