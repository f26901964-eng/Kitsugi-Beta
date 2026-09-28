import sys, urllib.request, json, os

sys.stdout.reconfigure(encoding='utf-8')

url = 'https://api.modrinth.com/v2/project/sBwZemk4/version'
req = urllib.request.Request(url, headers={'User-Agent': 'Kitsugi/1.0'})
with urllib.request.urlopen(req) as r:
    versions = json.loads(r.read())

print(f'Toplam surum: {len(versions)}')

# NeoForge 1.21.1 uyumlu surumu bul
target = None
for v in versions:
    loaders = v.get('loaders', [])
    gv = v.get('game_versions', [])
    files = v.get('files', [])
    fname = files[0]['filename'] if files else 'N/A'
    vnum = v.get('version_number', '?')
    print(f'  [{vnum}] loaders={loaders} game={gv} -> {fname}')
    if 'neoforge' in loaders and '1.21.1' in gv and target is None:
        target = v

if target is None:
    print('\nNeoForge 1.21.1 icin surum bulunamadi! Tum loader surumlere bakiliyor...')
    for v in versions:
        gv = v.get('game_versions', [])
        if '1.21.1' in gv:
            target = v
            break

if target is None:
    print('Hic uyumlu surum bulunamadi!')
    sys.exit(1)

files = target.get('files', [])
fname = files[0]['filename']
furl = files[0]['url']
vnum = target.get('version_number', '?')
print(f'\nSecilen surum: {vnum}')
print(f'Dosya: {fname}')
print(f'URL: {furl}')

mods_dir = r'C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods'
dest = os.path.join(mods_dir, fname)

# Eski nvidium'u kaldir
for f in os.listdir(mods_dir):
    if 'nvidium' in f.lower():
        old_path = os.path.join(mods_dir, f)
        os.remove(old_path)
        print(f'Silindi: {f}')

print(f'Indiriliyor...')
req2 = urllib.request.Request(furl, headers={'User-Agent': 'Kitsugi/1.0'})
with urllib.request.urlopen(req2) as r, open(dest, 'wb') as out:
    data = r.read()
    out.write(data)

print(f'Tamamlandi: {fname} ({len(data)//1024} KB)')
