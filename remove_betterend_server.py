"""
Fix: Remove BetterEnd + BetterNether from server mods (they crash NeoForge via Connector).
Move them to AutoModpack host-modpack client-only folder so clients still get them.
"""
import os, paramiko, sys, io, time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

PASSWORD = "Gameras6060"
MODS_DIR = "/home/blackdamage/minecraft-neoforge-1211/mods"
CLIENT_MODS_DIR = "/home/blackdamage/minecraft-neoforge-1211/automodpack/host-modpack/main/mods"
LOCAL_SOURCE = r"C:\Users\Administrator\Downloads\MC-1211-ModPaketi-DOSYALAR (6)\mods-server"
LOCAL_CLIENT  = r"C:\Users\Administrator\Downloads\MC-1211-ModPaketi-DOSYALAR (6)\mods-client"

# These Fabric mods crash the NeoForge server via Connector
SERVER_CRASH_MODS = [
    'better-end-',
    'better-nether-',
    'BetterEnd-',
    'BetterNether-',
    'bclib-',
    'worldweaver-',
    'wunderlib-',
]

print("=== Connecting SSH ===")
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username=PASSWORD[:0] + 'blackdamage', password=PASSWORD)
sftp = ssh.open_sftp()

remote_server_files = sftp.listdir(MODS_DIR)
remote_client_files = sftp.listdir(CLIENT_MODS_DIR)

removed = []
print("\n=== Removing crash-causing mods from server/mods/ ===")
for rf in remote_server_files:
    for pat in SERVER_CRASH_MODS:
        if rf.startswith(pat) or rf.lower().startswith(pat.lower()):
            print(f"  REMOVE from server: {rf}")
            sftp.remove(f"{MODS_DIR}/{rf}")
            removed.append(rf)
            break

print("\n=== Moving crash mods to client-mods folder (AutoModpack will serve them) ===")
# Find these files locally and upload to client mods folder
crash_patterns = ['better-end-', 'better-nether-', 'bclib-', 'worldweaver-', 'wunderlib-']
for local_dir in [LOCAL_SOURCE, LOCAL_CLIENT]:
    if not os.path.exists(local_dir):
        continue
    for f in os.listdir(local_dir):
        if not f.endswith('.jar'):
            continue
        for pat in crash_patterns:
            if f.lower().startswith(pat.lower()):
                # Upload to client mods folder if not already there
                if f not in remote_client_files:
                    print(f"  UPLOAD to client-mods: {f}")
                    sftp.put(os.path.join(local_dir, f), f"{CLIENT_MODS_DIR}/{f}")
                else:
                    print(f"  Already in client-mods: {f}")
                break

# Also remove them from local source to prevent re-sync putting them back
print("\n=== Removing crash mods from local source to prevent re-deployment ===")
for f in list(os.listdir(LOCAL_SOURCE)):
    for pat in crash_patterns:
        if f.lower().startswith(pat.lower()):
            print(f"  [LOCAL SOURCE REMOVE] {f}")
            os.remove(os.path.join(LOCAL_SOURCE, f))
            break

sftp.close()

print("\n=== Restarting Minecraft server ===")
stdin, stdout, stderr = ssh.exec_command("sudo -S systemctl restart minecraft")
stdin.write(PASSWORD + "\n")
stdin.flush()
stdout.read()

print("Waiting 90s for server boot...")
time.sleep(90)

stdin, stdout, stderr = ssh.exec_command(
    "tail -n 30 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log"
)
log = stdout.read().decode('utf-8', errors='replace')
print("\n=== SERVER LOG TAIL ===")
print(log)

fatal = [l for l in log.splitlines() if 'FATAL' in l or 'Failed to start' in l or 'Exception in server tick loop' in l or 'Biome source' in l]
if fatal:
    print("\n⚠️  STILL CRASHING:")
    for l in fatal:
        print(f"  {l}")
else:
    if 'Done' in log or 'For help' in log:
        print("\n✅ Server fully loaded and stable!")
    else:
        print("\n⏳ Server still loading (no fatal errors). Checking again in 60s...")
        time.sleep(60)
        stdin, stdout, stderr = ssh.exec_command(
            "tail -n 20 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log"
        )
        log2 = stdout.read().decode('utf-8', errors='replace')
        print(log2)
        if 'Done' in log2 or 'For help' in log2:
            print("\n✅ Server fully loaded and stable!")
        else:
            print("\n❓ Still loading or check manually.")

ssh.close()
