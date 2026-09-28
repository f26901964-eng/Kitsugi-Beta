# -*- coding: utf-8 -*-
"""
deploy_itemphysic_server.py
============================
ItemPhysic client_and_server modudur (Modrinth'e göre).
Sunucuda yoktu → itemphysic:0c paketi reddediliyordu → client crash.
Bu script sunucuya ItemPhysic'i yükler ve sunucuyu yeniden başlatır.
"""

import os
import sys
import io
import time
import paramiko

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

CLIENT_MODS = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"
SSH_HOST    = "192.168.1.100"
SSH_USER    = "blackdamage"
SSH_PASS    = "Gameras6060"
REMOTE_MODS = "/home/blackdamage/minecraft-neoforge-1211/mods"
SERVER_DIR  = "/home/blackdamage/minecraft-neoforge-1211"

MOD_FILENAME = "ItemPhysic_NEOFORGE_v1.8.13_mc1.21.1.jar"

print("=" * 60)
print("  ItemPhysic → Sunucuya Deploy")
print("=" * 60)

# 1. Client'ta mod var mı?
local_path = os.path.join(CLIENT_MODS, MOD_FILENAME)
if not os.path.exists(local_path):
    print(f"\n  HATA: Client mods klasöründe {MOD_FILENAME} bulunamadı!")
    sys.exit(1)

print(f"\n  ✅ Client'ta mevcut: {MOD_FILENAME} ({os.path.getsize(local_path)/1024:.1f} KB)")

# 2. SSH bağlantısı
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS)
sftp = ssh.open_sftp()

# 3. Sunucuda var mı kontrol et
remote_files = sftp.listdir(REMOTE_MODS)
already_there = [f for f in remote_files if f.lower().startswith("itemphysic")]

if already_there:
    print(f"\n  Sunucuda mevcut: {already_there}")
    # Farklı versiyon varsa temizle
    for f in already_there:
        if f != MOD_FILENAME:
            sftp.remove(f"{REMOTE_MODS}/{f}")
            print(f"  🗑️  Eski versiyon silindi: {f}")
        else:
            print(f"  ✅ Zaten doğru versiyon sunucuda, tekrar upload atlanıyor.")
            sftp.close()
            ssh.close()
            sys.exit(0)

# 4. Upload et
print(f"\n  📤 Sunucuya upload ediliyor: {MOD_FILENAME}...")
sftp.put(local_path, f"{REMOTE_MODS}/{MOD_FILENAME}")
print(f"  ✅ Upload tamamlandı!")
sftp.close()

# 5. Sunucuyu yeniden başlat
print("\n  🔄 Sunucu yeniden başlatılıyor...")
stdin, stdout, stderr = ssh.exec_command("sudo -S systemctl restart minecraft")
stdin.write(SSH_PASS + "\n")
stdin.flush()
stdout.read()

print("  ⏳ 90 saniye bekleniyor (sunucu boot)...")
time.sleep(90)

# 6. Log kontrol
stdin, stdout, stderr = ssh.exec_command(
    f"tail -n 30 {SERVER_DIR}/logs/latest.log"
)
log = stdout.read().decode('utf-8', errors='replace')
print("\n=== SUNUCU LOG ===")
print(log)

if "Done" in log or "For help" in log:
    print("\n  ✅ SUNUCU BAŞARIYLA AÇILDI!")
    print("  Artık Minecraft'ı başlatıp bağlanabilirsin.")
    print("  itemphysic:0c crash'i ortadan kalktı.")
elif "Exception" in log or "ERROR" in log or "FATAL" in log:
    print("\n  ⚠️  Sunucu loglarında hata var, yukarıya bak.")
else:
    print("\n  ⏳ Sunucu hâlâ yükleniyor olabilir. Manuel kontrol et.")

ssh.close()
