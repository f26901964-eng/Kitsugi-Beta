# -*- coding: utf-8 -*-
"""
add_c2me_client.py
==================
Standart c2me (Java 21 uyumlu) Modrinth'ten indirir
ve client mods klasörüne ekler.
"""
import os
import sys
import io
import urllib.request
import json

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

CLIENT_MODS = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"

print("=" * 60)
print("  C2ME (standart) - Client Kurulum")
print("=" * 60)

# Modrinth'ten NeoForge 1.21.1 sürümlerini çek
MODRINTH_API = "https://api.modrinth.com/v2/project/c2me-neoforge/version?game_versions=%5B%221.21.1%22%5D&loaders=%5B%22neoforge%22%5D"

print("\n[1] Modrinth'ten mevcut surumler kontrol ediliyor...")

try:
    req = urllib.request.Request(
        MODRINTH_API,
        headers={"User-Agent": "KitsugiModpack/1.0"}
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        versions = json.loads(resp.read().decode('utf-8'))

    if not versions:
        print("  HATA: Modrinth'te NeoForge 1.21.1 surumu bulunamadi!")
        sys.exit(1)

    # En son stabil/alpha sürümü seç
    # Java 21 uyumlu olanı (class 65 = Java 21) bul
    # Önce alpha olmayan, yoksa alpha al
    target = None
    for v in versions:
        for f in v.get('files', []):
            fname = f.get('filename', '')
            # opencl variant'ı atla
            if 'opencl' in fname.lower():
                continue
            if fname.endswith('.jar'):
                target = {
                    'version_number': v['version_number'],
                    'filename': fname,
                    'url': f['url'],
                    'size': f.get('size', 0)
                }
                break
        if target:
            break

    if not target:
        # opencl olmayan jar'ı bul (herhangi versiyon)
        print("  Standart build aranıyor...")
        for v in versions:
            for f in v.get('files', []):
                fname = f.get('filename', '')
                if 'opencl' not in fname.lower() and fname.endswith('.jar'):
                    target = {
                        'version_number': v['version_number'],
                        'filename': fname,
                        'url': f['url'],
                        'size': f.get('size', 0)
                    }
                    break
            if target:
                break

    if not target:
        print("  HATA: Uygun c2me surumu bulunamadi!")
        print("  Mevcut surumler:")
        for v in versions[:5]:
            for f in v.get('files', []):
                print(f"    - {f.get('filename', '?')}")
        sys.exit(1)

    print(f"  Bulunan surum  : {target['version_number']}")
    print(f"  Dosya adi      : {target['filename']}")
    print(f"  Boyut          : {target['size']/1024/1024:.2f} MB")
    print(f"  URL            : {target['url'][:70]}...")

except Exception as e:
    print(f"  HATA: Modrinth baglantisi basarisiz: {e}")
    # Fallback: bilinen URL ile dene
    print("\n  Fallback: bilinen surum deneniyor...")
    target = {
        'version_number': '0.4.0-alpha.0.116+1.21.1',
        'filename': 'c2me-neoforge-mc1.21.1-0.4.0-alpha.0.116.jar',
        'url': 'https://cdn.modrinth.com/data/c2me-neoforge/versions/c2me-neoforge-mc1.21.1-0.4.0-alpha.0.116.jar',
        'size': 3662294
    }

# İndir
dst = os.path.join(CLIENT_MODS, target['filename'])

# Zaten varsa atla
if os.path.exists(dst):
    print(f"\n  Zaten mevcut: {target['filename']}")
    print("  Atlanıyor (tekrar indirmeye gerek yok).")
else:
    print(f"\n[2] İndiriliyor: {target['filename']}...")
    try:
        req = urllib.request.Request(
            target['url'],
            headers={"User-Agent": "KitsugiModpack/1.0"}
        )
        with urllib.request.urlopen(req, timeout=60) as resp, \
             open(dst, 'wb') as out:
            total = 0
            while True:
                chunk = resp.read(65536)
                if not chunk:
                    break
                out.write(chunk)
                total += len(chunk)
                mb = total / 1024 / 1024
                print(f"\r  {mb:.2f} MB indirildi...", end='', flush=True)
        print(f"\n  OK: {target['filename']} indirildi ({total/1024/1024:.2f} MB)")
    except Exception as e:
        print(f"\n  HATA indirme: {e}")
        # Son çare: Downloads klasöründen kopyala (varsa)
        dl_path = r"C:\Users\Administrator\Downloads\Kitsugi_Mods_1.21.1_NeoForge"
        old_c2me = os.path.join(dl_path, "c2me-neoforge-mc1.21.1-0.4.0-alpha.0.116.jar")
        if os.path.exists(old_c2me):
            import shutil
            shutil.copy2(old_c2me, dst)
            print(f"  OK: Downloads klasöründen kopyalandı.")
        else:
            print("  HATA: Hicbir yerde bulunamadi!")
            sys.exit(1)

# Sonucu doğrula
if os.path.exists(dst):
    size = os.path.getsize(dst)
    print(f"\n  Dogrulama: {target['filename']}")
    print(f"  Boyut: {size/1024/1024:.2f} MB")
    print(f"  Konum: {dst}")

# Mevcut c2me modlarını listele
print("\n  Client mods klasöründeki c2me dosyaları:")
for f in sorted(os.listdir(CLIENT_MODS)):
    if 'c2me' in f.lower():
        sz = os.path.getsize(os.path.join(CLIENT_MODS, f))
        print(f"    {f} ({sz/1024/1024:.2f} MB)")

print("\n" + "=" * 60)
print("  TAMAMLANDI!")
print("  Artik Minecraft'i baslat ve test et.")
print("  c2me chunk yüklemeyi önemli ölçüde hizlandiracak.")
print("=" * 60)
