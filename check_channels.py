import os
import paramiko

client_mods_dir = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060', timeout=10)

def run(cmd):
    _, o, _ = ssh.exec_command(cmd)
    return o.read().decode('utf-8', errors='replace').strip()

print("--- SERVER MODS LIST FOR TARGETS ---")
for target in ['cyclic', 'tc-veinglow', 'underlay', 'tc_veinminer']:
    res = run(f"find /home/blackdamage/minecraft-neoforge-1211/mods -iname '*{target}*'")
    print(f"Target '{target}': {res}")

print("\n--- SERVER LATEST LOG SEARCH ---")
server_log = run("cat /home/blackdamage/minecraft-neoforge-1211/logs/latest.log")
for line in server_log.splitlines():
    if any(k in line.lower() for k in ['tc_veinminer', 'veinglow', 'cyclic', 'underlay']):
        print(line)

ssh.close()
