"""
Surgical fix: restore the exact bclib/worldweaver/wunderlib versions
that were previously on the server (21.0.25, 21.0.25, 21.0.10).
Also remove better-end and better-nether Fabric jars from server
(they can't load as Fabric mods and only cause dependency failures).
"""
import urllib.request, json, os, paramiko, sys, io, time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

MODS_DIR = "/home/blackdamage/minecraft-neoforge-1211/mods"
TMP = r"C:\Users\Administrator\Downloads\dep_fix_tmp"
os.makedirs(TMP, exist_ok=True)

def modrinth_all_versions(slug):
    url = f'https://api.modrinth.com/v2/project/{slug}/version'
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    return json.loads(urllib.request.urlopen(req, timeout=15).read())

# The server previously had these specific files working fine:
# bclib-21.0.25.jar, worldweaver-21.0.25.jar, wunderlib-21.0.10.jar
# Our deploy script wrongly replaced them with 21.0.13/21.0.8 versions.
# We need to find the download URL for each of these exact filenames.

NEEDED_FILES = {
    'bclib': 'bclib-21.0.25.jar',
    'worldweaver': 'worldweaver-21.0.25.jar',
    'wunderlib': 'wunderlib-21.0.10.jar',
}

print("=== Finding exact download URLs for previously-working versions ===")
downloads = {}
for slug, target_fn in NEEDED_FILES.items():
    versions = modrinth_all_versions(slug)
    found = False
    for v in versions:
        for f in v['files']:
            if f['filename'] == target_fn:
                print(f"  FOUND {slug}: {target_fn}")
                downloads[slug] = (f['url'], f['filename'])
                found = True
                break
        if found:
            break
    if not found:
        # Fall back: get any version with same major version
        target_major = target_fn.split('-')[1].rsplit('.', 1)[0]  # e.g. "21.0"
        for v in versions:
            if '1.21.1' in v.get('game_versions', []) or '1.21' in v.get('game_versions', []):
                fn = v['files'][0]['filename']
                fv = fn.split('-')[1].replace('.jar', '') if '-' in fn else ''
                if fv.startswith('21.0.1') or fv.startswith('21.0.2'):
                    print(f"  FALLBACK {slug}: {fn} (wanted {target_fn})")
                    downloads[slug] = (v['files'][0]['url'], fn)
                    break

print("\n=== Downloading ===")
for slug, (url, fn) in downloads.items():
    dest = os.path.join(TMP, fn)
    if not os.path.exists(dest):
        print(f"  Downloading {fn}...")
        urllib.request.urlretrieve(url, dest)
        print(f"  Done")
    else:
        print(f"  Cached: {fn}")

print("\n=== Connecting SSH ===")
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')
sftp = ssh.open_sftp()
remote_files = sftp.listdir(MODS_DIR)

# Remove ALL bclib/worldweaver/wunderlib/betterend/betternether
REMOVE_STARTS = ['bclib-', 'worldweaver-', 'wunderlib-', 'better-end-', 'better-nether-',
                 'BetterEnd-', 'BetterNether-']
print("\n=== Removing old versions + Fabric-only BetterEnd/Nether ===")
for rf in remote_files:
    for pat in REMOVE_STARTS:
        if rf.startswith(pat) or rf.lower().startswith(pat.lower()):
            print(f"  REMOVE: {rf}")
            sftp.remove(f"{MODS_DIR}/{rf}")
            break

# Upload correct versions
print("\n=== Uploading correct versions ===")
for slug, (url, fn) in downloads.items():
    local = os.path.join(TMP, fn)
    remote = f"{MODS_DIR}/{fn}"
    print(f"  UPLOAD: {fn}")
    sftp.put(local, remote)
    print(f"  OK")

sftp.close()

# Restart
print("\n=== Restarting Minecraft server ===")
stdin, stdout, stderr = ssh.exec_command("sudo -S systemctl restart minecraft")
stdin.write("Gameras6060\n")
stdin.flush()
stdout.read()

print("Waiting 90s for boot...")
time.sleep(90)

stdin, stdout, stderr = ssh.exec_command(
    "tail -n 60 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log"
)
log = stdout.read().decode('utf-8', errors='replace')
print("\n=== SERVER LOG TAIL ===")
print(log)

fatal = [l for l in log.splitlines() if 'FATAL' in l or 'Failed to start' in l]
if fatal:
    print("\n⚠️  STILL CRASHING:")
    for l in fatal:
        print(f"  {l}")
else:
    # Check if server is done loading
    if 'Done' in log or 'For help' in log:
        print("\n✅ Server fully loaded!")
    else:
        print("\n⏳ Server still loading (no fatal errors). Check again in a moment.")

ssh.close()
