import paramiko
import os
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060', timeout=10)
    
    def run(cmd):
        stdin, stdout, stderr = ssh.exec_command(cmd)
        return stdout.read().decode('utf-8', errors='replace').strip()

    print("--- SERVER DISTANT HORIZONS CONFIG ---")
    config_content = run("cat /home/blackdamage/minecraft-neoforge-1211/config/DistantHorizons.toml")
    print(config_content if config_content else "(empty or not found)")

    ssh.close()
except Exception as e:
    print(f"Failed to connect to server: {e}")

print("\n--- CLIENT LATEST LOG GREP FOR DH ---")
client_log_path = r"C:\Users\Administrator\AppData\Roaming\.sklauncher\instances\neoforge-1-21-1\logs\latest.log"
if os.path.exists(client_log_path):
    with open(client_log_path, 'r', encoding='utf-8', errors='replace') as f:
        lines = f.readlines()
    dh_lines = [l.strip() for l in lines if "distant" in l.lower() or "horizon" in l.lower() or "lod" in l.lower()]
    print(f"Found {len(dh_lines)} matching lines in client latest.log. Last 20:")
    for l in dh_lines[-20:]:
        print(l)
else:
    print("Client latest.log not found!")
