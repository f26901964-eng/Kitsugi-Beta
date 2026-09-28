import paramiko
import json
import sys
import io
import os
import shutil
import time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

LOCAL_MODS = r"D:\Free\eski sunucu 1.21.1\mods"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')

def run(cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    return stdout.read().decode('utf-8', errors='replace').strip()

def run_sudo(cmd):
    stdin, stdout, stderr = ssh.exec_command(f"sudo -S {cmd}")
    stdin.write('Gameras6060\n')
    stdin.flush()
    return stdout.read().decode('utf-8', errors='replace').strip()

# ============================
# 1. AutoModpack config: exclude c2me opencl and configure syncedFiles
# ============================
config_path = "/home/blackdamage/minecraft-neoforge-1211/automodpack/automodpack-server.json"
config = json.loads(run(f"cat {config_path}"))

# Ensure only synced files are mods (no exclusion of c2me opencl)
# Remove all existing exclusions first
synced = [s for s in config["syncedFiles"] if not s.startswith("!")]

# Add exclusion ONLY for opencl version (Java 25 requirement)
exclusions = [
    "!/mods/c2me-neoforge-opts-accel-opencl-mc1.21.1-0.4.0-alpha.0.116.jar"
]

config["syncedFiles"] = synced + exclusions

print("Updated syncedFiles:")
for s in config["syncedFiles"]:
    print(f"  {s}")

# Write updated config
sftp = ssh.open_sftp()
with sftp.file(config_path, 'w') as f:
    json.dump(config, f, indent=2)
print("\n✅ AutoModpack config updated!")

# ============================
# 2. Download c2me (non-opencl) from server to local mods folder
# ============================
print("\nDownloading c2me-neoforge-mc1.21.1-0.4.0-alpha.0.116.jar from server...")
local_target = os.path.join(LOCAL_MODS, "c2me-neoforge-mc1.21.1-0.4.0-alpha.0.116.jar")

if not os.path.exists(local_target):
    sftp.get(
        "/home/blackdamage/minecraft-neoforge-1211/mods/c2me-neoforge-mc1.21.1-0.4.0-alpha.0.116.jar",
        local_target
    )
    print(f"✅ Downloaded: c2me-neoforge-mc1.21.1-0.4.0-alpha.0.116.jar")
else:
    print("✅ c2me mod already exists locally, skipping download")

sftp.close()

# ============================
# 3. Remove c2me opencl from local if exists
# ============================
opencl_local = os.path.join(LOCAL_MODS, "c2me-neoforge-opts-accel-opencl-mc1.21.1-0.4.0-alpha.0.116.jar")
if os.path.exists(opencl_local):
    os.remove(opencl_local)
    print("✅ Removed opencl mod from local mods")
else:
    print("✅ opencl mod not in local mods (already clean)")

# ============================
# 4. Restart server
# ============================
print("\nRestarting Minecraft server to apply config changes...")
run_sudo("systemctl restart minecraft")
print("Server restarting... waiting 20 seconds...")
time.sleep(20)

print("\n=== Server last 10 log lines ===")
print(run("tail -n 10 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log"))

ssh.close()
print("\n✅ All done! Now launch Minecraft and connect to the server.")
