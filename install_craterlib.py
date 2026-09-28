import urllib.request
import json
import sys
import io
import os

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

CLIENT_DIR = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"

# Modrinth API - craterlib versiyonlarini al
url = 'https://api.modrinth.com/v2/project/craterlib/version'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})

try:
    with urllib.request.urlopen(req, timeout=10) as r:
        versions = json.loads(r.read())
except Exception as e:
    print(f"API hatasi: {e}")
    sys.exit(1)

# NeoForge + 1.21.1 filtrele
target = None
for v in versions:
    loaders = v.get('loaders', [])
    game_versions = v.get('game_versions', [])
    if 'neoforge' in loaders and '1.21.1' in game_versions:
        target = v
        break

if not target:
    print("NeoForge 1.21.1 icin craterlib versiyonu bulunamadi!")
    sys.exit(1)

version_number = target['version_number']
files = target.get('files', [])
primary_file = next((f for f in files if f.get('primary', False)), files[0] if files else None)

if not primary_file:
    print("Dosya bilgisi bulunamadi!")
    sys.exit(1)

dl_url = primary_file['url']
filename = primary_file['filename']
dest_path = os.path.join(CLIENT_DIR, filename)

print(f"CraterLib versiyonu: {version_number}")
print(f"Dosya: {filename}")
print(f"URL: {dl_url}")
print(f"Hedef: {dest_path}")

if os.path.exists(dest_path):
    print("\n[VAR] Zaten mevcut!")
    sys.exit(0)

print("\nIndiriliyor...")
req2 = urllib.request.Request(dl_url, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req2, timeout=30) as r:
    data = r.read()

with open(dest_path, 'wb') as f:
    f.write(data)

size_kb = len(data) // 1024
print(f"[OK] Indirildi ve kuruldu: {filename} ({size_kb} KB)")
print("\nArtik Minecraft'i yeniden ac!")
