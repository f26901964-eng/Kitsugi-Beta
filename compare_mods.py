import paramiko
import os
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

LOCAL_MODS = r"D:\Free\eski sunucu 1.21.1\mods"
SERVER_MODS = "/home/blackdamage/minecraft-neoforge-1211/mods"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')

def run(cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    return stdout.read().decode('utf-8', errors='replace').strip()

# Get server mods list
server_mods_raw = run(f"ls {SERVER_MODS}/*.jar")
server_mods = set()
for line in server_mods_raw.splitlines():
    fname = line.strip().split("/")[-1]
    if fname.endswith(".jar"):
        server_mods.add(fname)

# Get local mods list
local_mods = set(f for f in os.listdir(LOCAL_MODS) if f.endswith(".jar"))

# Mods that are in both - check if server has DIFFERENT version of same base mod
# (same mod name but different version numbers)
def get_base_name(filename):
    # Remove version numbers to get mod base name
    # e.g. "Quark-4.1-482.jar" -> "Quark"
    parts = filename.replace(".jar", "").split("-")
    # Take first part as base
    return parts[0].lower()

# Build base name -> full name maps
local_base = {}
for f in local_mods:
    base = get_base_name(f)
    local_base[base] = f

server_base = {}
for f in server_mods:
    base = get_base_name(f)
    server_base[base] = f

# Find mods where names match but files are different
mismatched = []
for base, local_file in local_base.items():
    if base in server_base and server_base[base] != local_file:
        mismatched.append((local_file, server_base[base]))

print(f"=== LOCAL MODS: {len(local_mods)} ===")
print(f"=== SERVER MODS: {len(server_mods)} ===")

print(f"\n⚠️ FARKLI VERSIYONLAR ({len(mismatched)} mod):")
for local_f, server_f in sorted(mismatched):
    print(f"  LOCAL:  {local_f}")
    print(f"  SERVER: {server_f}")
    print()

only_server = server_mods - local_mods
print(f"\n🖧 SADECE SUNUCU'DA OLAN ({len(only_server)} mod):")
for f in sorted(only_server):
    print(f"  - {f}")

ssh.close()
