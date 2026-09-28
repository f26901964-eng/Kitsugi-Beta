import paramiko
import os
import sys
import io
import time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

LOCAL_MODS = r"D:\Free\eski sunucu 1.21.1\mods"
SERVER_MODS = "/home/blackdamage/minecraft-neoforge-1211/mods"
PASSWORD = "Gameras6060"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password=PASSWORD)

def run(cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    return stdout.read().decode('utf-8', errors='replace').strip()

def run_sudo(cmd):
    stdin, stdout, stderr = ssh.exec_command(f"sudo -S {cmd}")
    stdin.write(PASSWORD + '\n')
    stdin.flush()
    return stdout.read().decode('utf-8', errors='replace').strip()

# Get server mods
server_mods_raw = run(f"ls {SERVER_MODS}/*.jar")
server_mods = set()
for line in server_mods_raw.splitlines():
    fname = line.strip().split("/")[-1]
    if fname.endswith(".jar"):
        server_mods.add(fname)

# Get local mods
local_mods = set(f for f in os.listdir(LOCAL_MODS) if f.endswith(".jar"))

# Find client-only mods that are only local
only_local = local_mods - server_mods

print(f"📦 Sunucuya yüklenecek {len(only_local)} mod:")
for f in sorted(only_local):
    print(f"  - {f}")

print(f"\n🚀 Yükleme başlıyor...")
sftp = ssh.open_sftp()

uploaded = 0
failed = []
for i, fname in enumerate(sorted(only_local), 1):
    local_path = os.path.join(LOCAL_MODS, fname)
    remote_path = f"{SERVER_MODS}/{fname}"
    try:
        sftp.put(local_path, remote_path)
        print(f"  [{i}/{len(only_local)}] ✅ {fname}")
        uploaded += 1
    except Exception as e:
        print(f"  [{i}/{len(only_local)}] ❌ {fname} - HATA: {e}")
        failed.append(fname)

sftp.close()

print(f"\n✅ Yüklendi: {uploaded}/{len(only_local)} mod")
if failed:
    print(f"❌ Başarısız: {len(failed)} mod")
    for f in failed:
        print(f"  - {f}")

# Also remove c2me opencl from server (Java 25 issue)
print("\n🗑️ Java 25 uyumsuz c2me-opencl modu sunucudan kaldırılıyor...")
result = run(f"rm -f {SERVER_MODS}/c2me-neoforge-opts-accel-opencl-mc1.21.1-0.4.0-alpha.0.116.jar")
print("✅ Kaldırıldı")

# Restart server
print("\n🔄 Sunucu yeniden başlatılıyor...")
run_sudo("systemctl restart minecraft")
print("⏳ 20 saniye bekleniyor...")
time.sleep(20)

print("\n=== Son 10 log satırı ===")
print(run("tail -n 10 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log"))

ssh.close()
print("\n✅ Bitti! Minecraft'ı başlatıp sunucuya bağlanabilirsin.")
