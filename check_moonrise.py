import urllib.request, json, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

for slug in ['moonrise-opt', 'moonrise', 'moonrise-compats']:
    req = urllib.request.Request(
        f'https://api.modrinth.com/v2/project/{slug}/version',
        headers={'User-Agent': 'Search/1.0'}
    )
    data = json.loads(urllib.request.urlopen(req, timeout=10).read())
    matches = [v for v in data if '1.21.1' in v.get('game_versions', []) and 'neoforge' in v.get('loaders', [])]
    print(f'{slug}: {len(matches)} versiyon')
    if matches:
        v = matches[0]
        fname = v['files'][0]['filename']
        url = v['files'][0]['url']
        vtype = v['version_type']
        vnum = v['version_number']
        print(f'  En yeni: {vnum} | {vtype} | {fname}')
        print(f'  URL: {url}')
