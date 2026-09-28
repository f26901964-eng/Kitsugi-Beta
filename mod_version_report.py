import os
import sys
import io
import zipfile
import re
import struct

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

# ── Klasörler ──────────────────────────────────────────────────────────────
CLIENT_DIR    = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"
SOURCE_SERVER = r"C:\Users\Administrator\Downloads\MC-1211-ModPaketi-DOSYALAR (6)\mods-server"
SOURCE_CLIENT = r"C:\Users\Administrator\Downloads\MC-1211-ModPaketi-DOSYALAR (6)\mods-client"
CLIENT_MAX_CLASS_VERSION = 65  # Java 21

# ── Yardımcı fonksiyonlar ──────────────────────────────────────────────────
def get_jar_info(jar_path):
    """JAR'dan mod ID ve versiyon bilgisini çeker."""
    info = {"mod_id": None, "version": None, "name": None, "filename": os.path.basename(jar_path)}
    try:
        with zipfile.ZipFile(jar_path, 'r') as z:
            names = z.namelist()
            for toml_name in ["META-INF/neoforge.mods.toml", "META-INF/mods.toml"]:
                if toml_name in names:
                    content = z.read(toml_name).decode("utf-8", errors="replace")
                    # Sadece [[mods]] bölümünü al
                    mods_section = re.split(r'\[\[dependencies', content)[0]
                    ids = re.findall(r'\[\[mods\]\].*?modId\s*=\s*"([^"]+)"', mods_section, re.DOTALL)
                    if not ids:
                        ids = re.findall(r'modId\s*=\s*"([^"]+)"', mods_section)
                    versions = re.findall(r'version\s*=\s*"([^"$][^"]*)"', mods_section)
                    names_list = re.findall(r'displayName\s*=\s*"([^"]+)"', mods_section)
                    if ids:
                        info["mod_id"] = ids[0].lower().strip()
                    if versions:
                        v = versions[0].strip()
                        if not v.startswith('$'):
                            info["version"] = v
                    if names_list:
                        info["name"] = names_list[0].strip()
                    break
    except Exception:
        pass
    # Fallback: dosya adından çıkar
    if not info["mod_id"]:
        info["mod_id"] = re.sub(r'[^a-z0-9]', '', os.path.basename(jar_path).lower().split('.')[0])
    return info

def get_jar_java_version(jar_path):
    """JAR'ın gerektirdiği Java class version'ını döndürür."""
    max_v = 0
    try:
        with zipfile.ZipFile(jar_path, 'r') as z:
            for name in z.namelist():
                if name.endswith('.class') and not name.startswith('META-INF'):
                    try:
                        data = z.read(name)
                        if len(data) >= 8:
                            major = struct.unpack('>H', data[6:8])[0]
                            if major > max_v:
                                max_v = major
                            if major > CLIENT_MAX_CLASS_VERSION:
                                return major
                    except Exception:
                        pass
    except Exception:
        pass
    return max_v

def scan_dir(directory, label, java_filter=False):
    """Klasördeki tüm jar'ları tara, {mod_id: info} döndür."""
    result = {}
    if not os.path.exists(directory):
        return result
    for f in sorted(os.listdir(directory)):
        if not f.endswith('.jar'):
            continue
        path = os.path.join(directory, f)
        if java_filter:
            jv = get_jar_java_version(path)
            if jv > CLIENT_MAX_CLASS_VERSION:
                continue
        info = get_jar_info(path)
        if info["mod_id"]:
            result[info["mod_id"]] = info
    return result

# ── Tarama ────────────────────────────────────────────────────────────────
print("Modlar taranıyor, lütfen bekleyin...\n")

current = scan_dir(CLIENT_DIR, "İstemci")
new_server = scan_dir(SOURCE_SERVER, "Yeni-Server", java_filter=True)
new_client = scan_dir(SOURCE_CLIENT, "Yeni-Client", java_filter=True)

# Yeni kaynak = server + client birleşimi
new_all = {}
new_all.update(new_server)
new_all.update(new_client)

# ── Karşılaştırma ──────────────────────────────────────────────────────────
UPDATED   = []   # versiyon değişti
SAME      = []   # versiyon aynı
ADDED     = []   # yeni eklendi (kaynakta var, client'ta yok)
REMOVED   = []   # kaldırıldı (client'ta var, kaynakta yok)
JAVA_SKIP = []   # Java uyumsuzluğu nedeniyle atlandı

# Java uyumsuzları bul (kaynak + client'ta da var olanlar)
for f in sorted(os.listdir(SOURCE_SERVER)):
    if not f.endswith('.jar'):
        continue
    path = os.path.join(SOURCE_SERVER, f)
    jv = get_jar_java_version(path)
    if jv > CLIENT_MAX_CLASS_VERSION:
        info = get_jar_info(path)
        JAVA_SKIP.append(info)

# Kaynakta olanları karşılaştır
for mod_id, new_info in sorted(new_all.items(), key=lambda x: (x[1].get('name') or x[0]).lower()):
    if mod_id in current:
        cur_info = current[mod_id]
        cur_ver  = cur_info.get("version") or "?"
        new_ver  = new_info.get("version") or "?"
        cur_file = cur_info["filename"]
        new_file = new_info["filename"]
        display  = new_info.get("name") or mod_id

        if cur_file == new_file:
            SAME.append({
                "name": display, "mod_id": mod_id,
                "version": new_ver, "file": new_file
            })
        else:
            UPDATED.append({
                "name": display, "mod_id": mod_id,
                "old_ver": cur_ver, "new_ver": new_ver,
                "old_file": cur_file, "new_file": new_file
            })
    else:
        new_info_display = new_info.get("name") or mod_id
        ADDED.append({
            "name": new_info_display, "mod_id": mod_id,
            "version": new_info.get("version") or "?",
            "file": new_info["filename"]
        })

# Client'ta olup kaynakta olmayan modlar
for mod_id, cur_info in sorted(current.items(), key=lambda x: (x[1].get('name') or x[0]).lower()):
    if mod_id not in new_all:
        REMOVED.append({
            "name": cur_info.get("name") or mod_id,
            "mod_id": mod_id,
            "version": cur_info.get("version") or "?",
            "file": cur_info["filename"]
        })

# ── RAPOR ─────────────────────────────────────────────────────────────────
SEP = "─" * 70

print(SEP)
print("  MOD SÜRÜM KARŞILAŞTIRMA RAPORU — NeoForge 1.21.1")
print(SEP)
print(f"  Kaynak (güncel): {len(new_all)} mod")
print(f"  Kurulu (client): {len(current)} mod")
print()

# ── GÜNCELLENDİ ──────────────────────────────────────────────────────────
print(f"{'🔄  GÜNCELLENDİ / DEĞİŞTİRİLDİ':─<70}")
if UPDATED:
    for m in UPDATED:
        name    = (m['name'][:28]).ljust(30)
        old_v   = (m['old_ver'] or '?')[:22]
        new_v   = (m['new_ver'] or '?')[:22]
        print(f"  {name}  {old_v:22}  →  {new_v}")
else:
    print("  (yok)")

print()
print(f"{'✅  AYNI SÜRÜM (güncel)':─<70}")
if SAME:
    for m in SAME:
        name = (m['name'][:28]).ljust(30)
        ver  = (m['version'] or '?')[:30]
        print(f"  {name}  {ver}")
else:
    print("  (yok)")

print()
print(f"{'🆕  YENİ EKLENEN (kaynakta var, istemcide yoktu)':─<70}")
if ADDED:
    for m in ADDED:
        name = (m['name'][:28]).ljust(30)
        ver  = (m['version'] or '?')[:30]
        print(f"  {name}  {ver}")
else:
    print("  (yok)")

print()
print(f"{'🗑️  KALDIRILAN (istemcide var, kaynakta yok)':─<70}")
if REMOVED:
    for m in REMOVED:
        name = (m['name'][:28]).ljust(30)
        ver  = (m['version'] or '?')[:30]
        file = m['file'][:40]
        print(f"  {name}  {ver}")
        print(f"    └─ {file}")
else:
    print("  (yok)")

print()
print(f"{'⚠️  JAVA 25 GEREKTİREN (sunucu için, istemciye uyumsuz)':─<70}")
if JAVA_SKIP:
    for m in JAVA_SKIP:
        name = (m.get('name') or m.get('mod_id') or '?')[:50]
        print(f"  {name}  →  {m['filename']}")
else:
    print("  (yok)")

print()
print(SEP)
print(f"  GÜNCELLENDİ : {len(UPDATED):3}  |  AYNI : {len(SAME):3}  |  YENİ : {len(ADDED):3}  |  KALDIRILDI : {len(REMOVED):3}  |  JAVA-SKIP : {len(JAVA_SKIP):2}")
print(SEP)
