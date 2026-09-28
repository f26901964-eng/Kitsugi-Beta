"""
Replace Fabric BetterEnd/BCLib/WorldWeaver/WunderLib/BetterNether
with their NeoForge-native "New Dawn" ports by Raijin2312.
These work directly on NeoForge without Sinytra Connector,
eliminating the "Biome source config is not set" crash.
"""
import urllib.request, json, os, shutil, paramiko, sys, io, time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

PASSWORD = "Gameras6060"
MODS_DIR = "/home/blackdamage/minecraft-neoforge-1211/mods"
LOCAL_SOURCE = r"C:\Users\Administrator\Downloads\MC-1211-ModPaketi-DOSYALAR (6)\mods-server"
TMP = r"C:\Users\Administrator\Downloads\new_dawn_tmp"
os.makedirs(TMP, exist_ok=True)

# New Dawn NeoForge-native project slugs
NEW_DAWN_SLUGS = [
    'bclib-neoforge',          # BCLib: New Dawn
    'worldweaver-neoforge',    # WorldWeaver: New Dawn
    'wunderlib-neoforge',      # WunderLib: New Dawn
    'betterend-neoforge',      # BetterEnd: New Dawn
]

# Also check for BetterNether New Dawn
BETTER_NETHER_SLUGS = ['betternether-neoforge', 'better-nether-neoforge']

def modrinth_get_latest(slug, mc_version='1.21.1'):
    url = f'https://api.modrinth.com/v2/project/{slug}/version?game_versions=["{mc_version}"]'
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        versions = json.loads(urllib.request.urlopen(req, timeout=15).read())
        if versions:
            # Get the most recent release or beta
            v = versions[0]
            f = v['files'][0]
            return f['url'], f['filename'], v['version_number']
    except Exception as e:
        print(f"  Error fetching {slug}: {e}")
    return None, None, None

print("=== Finding New Dawn NeoForge versions for 1.21.1 ===")
downloads = {}

for slug in NEW_DAWN_SLUGS + BETTER_NETHER_SLUGS:
    url, fn, ver = modrinth_get_latest(slug)
    if url:
        print(f"  FOUND {slug}: {fn} ({ver})")
        downloads[slug] = (url, fn)
    else:
        print(f"  NOT FOUND for 1.21.1: {slug}")

print(f"\n=== Downloading {len(downloads)} New Dawn mods ===")
for slug, (url, fn) in downloads.items():
    dest = os.path.join(TMP, fn)
    if not os.path.exists(dest):
        print(f"  Downloading {fn}...")
        urllib.request.urlretrieve(url, dest)
        print(f"  Done")
    else:
        print(f"  Cached: {fn}")

# Old Fabric files to remove (these cause the crash)
OLD_PREFIXES = [
    'bclib-', 'worldweaver-', 'wunderlib-',
    'better-end-', 'better-nether-',
    'BetterEnd-', 'BetterNether-'
]

print("\n=== Updating local source folder ===")
for f in list(os.listdir(LOCAL_SOURCE)):
    if f.endswith('.jar'):
        for pat in OLD_PREFIXES:
            if f.lower().startswith(pat.lower()):
                print(f"  [REMOVE OLD] {f}")
                os.remove(os.path.join(LOCAL_SOURCE, f))
                break

for slug, (url, fn) in downloads.items():
    src = os.path.join(TMP, fn)
    dst = os.path.join(LOCAL_SOURCE, fn)
    shutil.copy2(src, dst)
    print(f"  [ADD NEW] {fn}")

print("\n=== Connecting SSH ===")
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password=PASSWORD)
sftp = ssh.open_sftp()

remote_files = sftp.listdir(MODS_DIR)

print("\n=== Removing old Fabric versions from server ===")
for rf in remote_files:
    if not rf.endswith('.jar'):
        continue
    for pat in OLD_PREFIXES:
        if rf.lower().startswith(pat.lower()):
            print(f"  REMOVE: {rf}")
            try:
                sftp.remove(f"{MODS_DIR}/{rf}")
            except Exception as e:
                print(f"    Error: {e}")
            break

print("\n=== Uploading New Dawn NeoForge versions to server ===")
for slug, (url, fn) in downloads.items():
    local = os.path.join(TMP, fn)
    remote = f"{MODS_DIR}/{fn}"
    print(f"  UPLOAD: {fn}")
    sftp.put(local, remote)
    print(f"  OK")

sftp.close()

print("\n=== Restarting Minecraft server ===")
stdin, stdout, stderr = ssh.exec_command("sudo -S systemctl restart minecraft")
stdin.write(PASSWORD + "\n")
stdin.flush()
stdout.read()

print("Waiting 90s for server boot...")
time.sleep(90)

stdin, stdout, stderr = ssh.exec_command(
    "tail -n 40 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log"
)
log = stdout.read().decode('utf-8', errors='replace')
print("\n=== SERVER LOG TAIL ===")
print(log)

fatal = [l for l in log.splitlines() if 'FATAL' in l or 'Failed to start' in l or 'Exception in server tick loop' in l or 'Biome source' in l or 'IllegalState' in l]
if fatal:
    print("\n⚠️  STILL CRASHING:")
    for l in fatal:
        print(f"  {l}")
    sftp2 = ssh.open_sftp()
    crashes = sorted(sftp2.listdir('/home/blackdamage/minecraft-neoforge-1211/crash-reports'))
    if crashes:
        print(f"\n  Latest crash: {crashes[-1]}")
    sftp2.close()
else:
    if 'Done' in log or 'For help' in log:
        print("\n✅ SERVER FULLY LOADED AND STABLE!")
    else:
        print("\n⏳ No fatal errors. Waiting more (60s)...")
        time.sleep(60)
        stdin, stdout, stderr = ssh.exec_command(
            "tail -n 20 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log"
        )
        log2 = stdout.read().decode('utf-8', errors='replace')
        print(log2)
        if 'Done' in log2 or 'For help' in log2:
            print("\n✅ SERVER FULLY LOADED AND STABLE!")
        else:
            print("\n❓ Check server manually.")

ssh.close()
