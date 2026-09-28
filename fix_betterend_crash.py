"""
Fix: BetterEnd crash - "Biome source config is not set"
Root cause: BCLib/WorldWeaver 21.0.13 is not compatible with BetterEnd 21.0.11 via Connector.
Solution: Download the latest BCLib/WorldWeaver/WunderLib versions compatible with BetterEnd 21.0.11.
"""
import urllib.request, json, os, paramiko, sys, io, time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

MODS_DIR = "/home/blackdamage/minecraft-neoforge-1211/mods"
LOCAL_SOURCE = r"C:\Users\Administrator\Downloads\MC-1211-ModPaketi-DOSYALAR (6)\mods-server"
TMP = r"C:\Users\Administrator\Downloads\dep_fix_tmp2"
os.makedirs(TMP, exist_ok=True)

def modrinth_all_versions(slug):
    url = f'https://api.modrinth.com/v2/project/{slug}/version'
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    return json.loads(urllib.request.urlopen(req, timeout=15).read())

print("=== Fetching BCLib versions ===")
bclib_versions = modrinth_all_versions('bclib')
ww_versions = modrinth_all_versions('worldweaver')
wl_versions = modrinth_all_versions('wunderlib')

# BetterEnd 21.0.11 requires specific BCLib version
# Find the latest BCLib that matches BetterEnd 21.0.x series (21.0.x family)
def find_best_version(versions, major_minor='21.0', mc_filter='1.21.1'):
    """Find highest version in the 21.0.x series for 1.21.1"""
    candidates = []
    for v in versions:
        if mc_filter in v.get('game_versions', []) or '1.21' in v.get('game_versions', []):
            for f in v['files']:
                fn = f['filename']
                # Extract version from filename like bclib-21.0.25.jar
                parts = fn.replace('.jar', '').split('-')
                if len(parts) >= 2:
                    ver = parts[1]
                    if ver.startswith(major_minor):
                        try:
                            patch = int(ver.rsplit('.', 1)[-1])
                            candidates.append((patch, ver, f['url'], fn))
                        except:
                            pass
    if candidates:
        candidates.sort(reverse=True)
        return candidates[0]  # (patch, ver, url, filename)
    return None

bc = find_best_version(bclib_versions)
ww = find_best_version(ww_versions)
wl = find_best_version(wl_versions)

print(f"BCLib best: {bc}")
print(f"WorldWeaver best: {ww}")
print(f"WunderLib best: {wl}")

downloads = {}
if bc: downloads['bclib'] = (bc[2], bc[3])
if ww: downloads['worldweaver'] = (ww[2], ww[3])
if wl: downloads['wunderlib'] = (wl[2], wl[3])

print("\n=== Downloading latest 21.0.x versions ===")
for slug, (url, fn) in downloads.items():
    dest = os.path.join(TMP, fn)
    if not os.path.exists(dest):
        print(f"  Downloading {fn}...")
        urllib.request.urlretrieve(url, dest)
        print(f"  Done")
    else:
        print(f"  Cached: {fn}")

print("\n=== Updating local source folder ===")
for slug, (url, fn) in downloads.items():
    # Remove old version from source folder
    for existing in os.listdir(LOCAL_SOURCE):
        if existing.lower().startswith(slug + '-'):
            print(f"  [SOURCE REMOVE] {existing}")
            os.remove(os.path.join(LOCAL_SOURCE, existing))
    # Copy new version
    src = os.path.join(TMP, fn)
    dst = os.path.join(LOCAL_SOURCE, fn)
    import shutil
    shutil.copy2(src, dst)
    print(f"  [SOURCE ADD] {fn}")

print("\n=== Connecting SSH ===")
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')
sftp = ssh.open_sftp()
remote_files = sftp.listdir(MODS_DIR)

REMOVE_STARTS = ['bclib-', 'worldweaver-', 'wunderlib-']
print("\n=== Removing old bclib/worldweaver/wunderlib from server ===")
for rf in remote_files:
    for pat in REMOVE_STARTS:
        if rf.lower().startswith(pat.lower()):
            print(f"  REMOVE: {rf}")
            sftp.remove(f"{MODS_DIR}/{rf}")
            break

print("\n=== Uploading latest versions to server ===")
for slug, (url, fn) in downloads.items():
    local = os.path.join(TMP, fn)
    remote = f"{MODS_DIR}/{fn}"
    print(f"  UPLOAD: {fn}")
    sftp.put(local, remote)
    print(f"  OK")

sftp.close()

print("\n=== Restarting Minecraft server ===")
stdin, stdout, stderr = ssh.exec_command("sudo -S systemctl restart minecraft")
stdin.write("Gameras6060\n")
stdin.flush()
stdout.read()

print("Waiting 90s for server to boot...")
time.sleep(90)

stdin, stdout, stderr = ssh.exec_command(
    "tail -n 30 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log"
)
log = stdout.read().decode('utf-8', errors='replace')
print("\n=== SERVER LOG TAIL ===")
print(log)

fatal = [l for l in log.splitlines() if 'FATAL' in l or 'Failed to start' in l or 'Exception in server tick loop' in l]
if fatal:
    print("\n⚠️  STILL CRASHING:")
    for l in fatal:
        print(f"  {l}")
    # Print latest crash report name
    sftp2 = ssh.open_sftp()
    crashes = sftp2.listdir('/home/blackdamage/minecraft-neoforge-1211/crash-reports')
    crashes.sort()
    if crashes:
        print(f"\n  Latest crash: {crashes[-1]}")
    sftp2.close()
else:
    if 'Done' in log or 'For help' in log or 'DONE' in log.upper():
        print("\n✅ Server fully loaded!")
    else:
        print("\n⏳ Server still loading (no fatal errors). Waiting more...")
        time.sleep(60)
        stdin, stdout, stderr = ssh.exec_command(
            "tail -n 20 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log"
        )
        log2 = stdout.read().decode('utf-8', errors='replace')
        print(log2)
        if 'Done' in log2 or 'For help' in log2:
            print("\n✅ Server fully loaded!")
        else:
            print("\n❓ Check server manually.")

ssh.close()
