import paramiko
import os
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

LOCAL_MODS = r"C:\Users\Administrator\AppData\Roaming\.sklauncher\instances\neoforge-1-21-1\mods"
SERVER_MODS = "/home/blackdamage/minecraft-neoforge-1211/mods"
SERVER_CONFIG = "/home/blackdamage/minecraft-neoforge-1211/config"

print("--- LOCAL DH CHECK ---")
dh_local_files = [f for f in os.listdir(LOCAL_MODS) if "distant" in f.lower() or "horizon" in f.lower()]
if dh_local_files:
    for f in dh_local_files:
        full_path = os.path.join(LOCAL_MODS, f)
        print(f"Found local: {f} (Size: {os.path.getsize(full_path)} bytes)")
else:
    print("No Distant Horizons jar found in local mods folder!")

print("\n--- SERVER DH CHECK ---")
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060', timeout=10)
    
    def run(cmd):
        stdin, stdout, stderr = ssh.exec_command(cmd)
        return stdout.read().decode('utf-8', errors='replace').strip()

    # Find jar files matching distant/horizon on server
    server_dh = run(f"find {SERVER_MODS} -iname '*distant*' -o -iname '*horizon*'")
    print("Server DH files:")
    print(server_dh if server_dh else "(none)")

    # Find configs
    print("\nServer DH config files:")
    server_configs = run(f"find {SERVER_CONFIG} -iname '*distant*' -o -iname '*horizon*'")
    print(server_configs if server_configs else "(none)")

    # Let's inspect logs for DistantHorizons
    print("\nServer logs grep for 'distant' or 'horizon':")
    dh_log = run("grep -i -E 'distant|horizon' /home/blackdamage/minecraft-neoforge-1211/logs/latest.log | tail -n 15")
    print(dh_log if dh_log else "(none)")

    ssh.close()
except Exception as e:
    print(f"Failed to connect to server: {e}")
