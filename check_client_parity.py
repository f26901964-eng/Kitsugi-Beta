import os
import sys
import io
import zipfile
import re
import struct

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

CLIENT_DIR    = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"
SOURCE_SERVER = r"C:\Users\Administrator\Downloads\MC-1211-ModPaketi-DOSYALAR (6)\mods-server"
SOURCE_CLIENT = r"C:\Users\Administrator\Downloads\MC-1211-ModPaketi-DOSYALAR (6)\mods-client"

# Java sürüm sınırı - istemci Java 21 kullanıyor (class file version 65)
CLIENT_MAX_CLASS_VERSION = 65  # Java 21

def get_jar_min_java_version(jar_path):
    """JAR içindeki .class dosyalarının gerektirdiği minimum Java sürümünü döndürür."""
    max_version = 0
    try:
        with zipfile.ZipFile(jar_path, 'r') as z:
            for name in z.namelist():
                if name.endswith('.class') and not name.startswith('META-INF'):
                    try:
                        data = z.read(name)
                        if len(data) >= 8:
                            major = struct.unpack('>H', data[6:8])[0]
                            if major > max_version:
                                max_version = major
                            if major > CLIENT_MAX_CLASS_VERSION:
                                return major  # Erken çık, zaten uyumsuz
                    except Exception:
                        pass
    except Exception:
        pass
    return max_version

def get_mod_id_from_jar(jar_path):
    """Extract mod IDs from a JAR's TOML metadata."""
    try:
        with zipfile.ZipFile(jar_path, 'r') as z:
            names = z.namelist()
            for toml_file in ["META-INF/neoforge.mods.toml", "META-INF/mods.toml"]:
                if toml_file in names:
                    content = z.read(toml_file).decode("utf-8", errors="replace")
                    # Only grab IDs from [[mods]] section, not [[dependencies]]
                    mods_section = re.split(r'\[\[dependencies', content)[0]
                    ids = re.findall(r'\[\[mods\]\].*?modId\s*=\s*"([^"]+)"', mods_section, re.DOTALL)
                    if not ids:
                        ids = re.findall(r'modId\s*=\s*"([^"]+)"', mods_section)
                    return [i.lower() for i in ids] if ids else []
    except Exception:
        pass
    return []

def normalize(s):
    return re.sub(r'[^a-z0-9]', '', s.lower())

def build_mod_map(directory, check_java_compat=False):
    """Build {mod_id: filename} map for a directory."""
    mod_map = {}
    if not os.path.exists(directory):
        return mod_map
    for f in os.listdir(directory):
        if not f.endswith('.jar'):
            continue
        jar_path = os.path.join(directory, f)
        # Java uyumluluk kontrolü (istemci için)
        if check_java_compat:
            ver = get_jar_min_java_version(jar_path)
            if ver > CLIENT_MAX_CLASS_VERSION:
                java_needed = ver - 44  # class version -> Java version
                print(f"  [ATLA - Java {java_needed} gerekli] {f}")
                continue
        ids = get_mod_id_from_jar(jar_path)
        if ids:
            for mid in ids:
                mod_map[mid] = f
        else:
            mod_map[normalize(f)] = f
    return mod_map

print("Analiz yapılıyor, lütfen bekleyin...")
print("(Java 25+ ile derlenmis jarlar istemci icin otomatik atlanir)\n")

# Build maps
client_map = build_mod_map(CLIENT_DIR)
source_server_map = build_mod_map(SOURCE_SERVER, check_java_compat=True)
source_client_map = build_mod_map(SOURCE_CLIENT, check_java_compat=True)

# Merge source maps (both server and client side mods should be in local client)
all_source = {}
all_source.update(source_server_map)
all_source.update(source_client_map)

print(f"\nKaynakta toplam mod ID: {len(all_source)}")
print(f"İstemcide mevcut mod ID: {len(client_map)}")

outdated = []
missing = []
ok_count = 0

for mod_id, src_filename in sorted(all_source.items()):
    if mod_id not in client_map:
        missing.append((mod_id, src_filename))
    elif client_map[mod_id] != src_filename:
        outdated.append((mod_id, client_map[mod_id], src_filename))
    else:
        ok_count += 1

# Check for extra mods in client not in source (orphans)
extra = []
for mod_id, cli_filename in sorted(client_map.items()):
    if mod_id not in all_source:
        extra.append((mod_id, cli_filename))

print("\n" + "=" * 60)
print("        LOCAL CLIENT MOD PARİTE RAPORU")
print("=" * 60)

print(f"\n[OK] Güncel modlar: {ok_count}")

if outdated:
    print(f"\n[!] ESKİ VERSİYON ({len(outdated)} adet) - Güncelleme gerekli:")
    for mod_id, old, new in outdated:
        print(f"   • {mod_id}")
        print(f"     Mevcut : {old}")
        print(f"     Yeni   : {new}")
else:
    print("\n[OK] Tüm modlar güncel versiyonda!")

if missing:
    print(f"\n[X] EKSİK MODLAR ({len(missing)} adet) - Kopyalanması gerekli:")
    for mod_id, src_file in missing:
        print(f"   • {mod_id}: {src_file}")
else:
    print("\n[OK] Hiç eksik mod yok!")

if extra:
    print(f"\n[?] İSTEMCİDE FAZLADAN MOD ({len(extra)} adet) - Kaynakta yok:")
    for mod_id, cli_file in extra:
        print(f"   • {mod_id}: {cli_file}")

print("\n" + "=" * 60)
