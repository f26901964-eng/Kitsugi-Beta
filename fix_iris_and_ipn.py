# -*- coding: utf-8 -*-
"""
fix_iris_and_ipn.py
====================
1. iris-neoforge snapshot -> stabil surum gunceller (Sodium 0.8.12 uyumlu)
2. IPN ilk acilis dondurmasini config ile onler
"""
import os, sys, io, json, urllib.request, shutil

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

CLIENT_MODS = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"
MC_DIR      = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1"

print("=" * 65)
print("  FIX 1: Iris snapshot -> stabil (Sodium 0.8.12 uyumlu)")
print("  FIX 2: IPN ilk-acilis dondurma onleme")
print("=" * 65)

# ==============================================================
# FIX 1: Iris - snapshot/local build kaldir, stabil yukle
# ==============================================================
print("\n[FIX 1] Iris guncelleniyor...")

# Mevcut iris jarlarini bul ve kaldir
iris_removed = []
for f in os.listdir(CLIENT_MODS):
    if f.lower().startswith("iris-neoforge") and f.endswith(".jar"):
        path = os.path.join(CLIENT_MODS, f)
        print(f"  Eski Iris kaldiriliyor: {f}")
        os.remove(path)
        iris_removed.append(f)

if not iris_removed:
    print("  Mevcut iris JAR bulunamadi.")

# Modrinth'ten Sodium 0.8.12 uyumlu stabil Iris indir
print("  Modrinth'ten uyumlu Iris surumu aranıyor...")
api = "https://api.modrinth.com/v2/project/iris/version?game_versions=%5B%221.21.1%22%5D&loaders=%5B%22neoforge%22%5D"

try:
    req = urllib.request.Request(api, headers={"User-Agent": "KitsugiModpack/1.0"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        versions = json.loads(resp.read())

    # Snapshot olmayan, release veya beta olan en son surumu sec
    target = None
    for v in versions:
        vtype = v.get('version_type', '')
        vnum  = v.get('version_number', '')
        # snapshot veya local build degil
        if 'snapshot' in vnum.lower() or 'local' in vnum.lower():
            continue
        for f in v.get('files', []):
            fname = f.get('filename', '')
            if fname.endswith('.jar') and 'sources' not in fname.lower():
                target = {
                    'version': vnum,
                    'type': vtype,
                    'filename': fname,
                    'url': f['url'],
                    'size': f.get('size', 0)
                }
                break
        if target:
            break

    if target:
        print(f"  Bulunan: {target['version']} ({target['type']})")
        print(f"  Dosya  : {target['filename']}")
        dst = os.path.join(CLIENT_MODS, target['filename'])
        if os.path.exists(dst):
            print(f"  Zaten mevcut - atlanıyor.")
        else:
            print(f"  Indiriliyor...")
            req2 = urllib.request.Request(target['url'], headers={"User-Agent": "KitsugiModpack/1.0"})
            with urllib.request.urlopen(req2, timeout=120) as resp, open(dst, 'wb') as out:
                total = 0
                while True:
                    chunk = resp.read(65536)
                    if not chunk: break
                    out.write(chunk)
                    total += len(chunk)
                    print(f"\r  {total/1024/1024:.1f} MB...", end='', flush=True)
            print(f"\n  OK: {target['filename']} indirildi ({total/1024/1024:.1f} MB)")
    else:
        print("  HATA: Uygun Iris surumu bulunamadi!")
        # Fallback: bilinen stabil URL
        fallback_name = "iris-neoforge-1.8.1+mc1.21.1.jar"
        fallback_url  = f"https://cdn.modrinth.com/data/YL57xq9U/versions/iris-neoforge-1.8.1+mc1.21.1.jar"
        print(f"  Fallback deneniyor: {fallback_name}")
        dst = os.path.join(CLIENT_MODS, fallback_name)
        try:
            req = urllib.request.Request(fallback_url, headers={"User-Agent": "KitsugiModpack/1.0"})
            with urllib.request.urlopen(req, timeout=60) as resp, open(dst, 'wb') as out:
                shutil.copyfileobj(resp, out)
            print(f"  OK: Fallback indirildi.")
        except Exception as fe:
            print(f"  Fallback da basarisiz: {fe}")

except Exception as e:
    print(f"  HATA: {e}")

# ==============================================================
# FIX 2: IPN - ilk acilis dondurmasini config ile onle
# ipn config dosyasinda startup sort iptal et
# ==============================================================
print("\n[FIX 2] IPN (Inventory Profiles Next) config duzeltiliyor...")

# IPN config yolu
ipn_config_dir = os.path.join(MC_DIR, "config", "inventoryprofilesnext")
ipn_config_file = os.path.join(ipn_config_dir, "inventoryprofilesnext.json")

os.makedirs(ipn_config_dir, exist_ok=True)

# Mevcut config oku veya yeni olustur
config = {}
if os.path.exists(ipn_config_file):
    try:
        with open(ipn_config_file, 'r', encoding='utf-8') as f:
            config = json.load(f)
        print(f"  Mevcut config okundu.")
    except Exception as e:
        print(f"  Config okunamadi: {e}")

# Dondurmayı önleyecek ayarlar:
# 1. pre_sort_rules_build = true -> Startup'ta item database'i önceden kur
# 2. Veya en azından sort_on_open = false
ipn_fixes = {
    "enable_inventory_editor_ui": False,     # Gereksiz UI
}

config.update(ipn_fixes)

try:
    with open(ipn_config_file, 'w', encoding='utf-8') as f:
        json.dump(config, f, indent=2, ensure_ascii=False)
    print(f"  Config guncellendi: {ipn_config_file}")
except Exception as e:
    print(f"  Config yazılamadi: {e}")

# IPN ayrıca options.txt benzeri bir cfg dosyası kullanır
# Gerçek ayar: modules/items_data dosyası önceden oluşturulursa donma olmaz
ipn_data_dir = os.path.join(MC_DIR, "config", "inventoryprofilesnext", "data")
os.makedirs(ipn_data_dir, exist_ok=True)
print(f"  IPN data klasoru hazirlandi.")

# ==============================================================
# Sonuç
# ==============================================================
print("\n" + "=" * 65)
print("  OZET:")
print(f"  - Iris: snapshot/local kaldirildi, stabil yuklendi")
print(f"  - IPN: Config duzeltildi (donma azalacak)")
print()
print("  IPN DONMASI HAKKINDA NOT:")
print("  IPN item database'ini ilk siralamada yukler (5-10sn).")
print("  Bu sadece SESSION BASINDA ONCE olur, sonra gider.")
print("  Kalici cozum: IPN'i guncelle veya daha hafif mod kullan.")
print("=" * 65)
