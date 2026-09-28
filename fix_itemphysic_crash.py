# -*- coding: utf-8 -*-
"""
fix_itemphysic_crash.py
========================
ItemPhysic modu, Fabric→NeoForge (Sinytra Connector) üzerinden çalışırken
sunucuya `itemphysic:0c` paketi göndermeye çalışıyor.
Sunucuda ItemPhysic olmadığı için NetworkRegistry bu paketi reddedip
UnsupportedOperationException fırlatıyor → CLIENT CRASH.

Çözüm:
  1. İstemciden ItemPhysic JAR'ını kaldır (client-only görsel mod, sunucu uyumu yok)
  2. Sunucuda zaten yoksa doğrula ve orada da yoksa tamam.
  3. Sunucuyu yeniden başlatmaya gerek yok (sunucu zaten çalışıyor).
"""

import os
import sys
import io
import paramiko

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

CLIENT_MODS = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"
SSH_HOST    = "192.168.1.100"
SSH_USER    = "blackdamage"
SSH_PASS    = "Gameras6060"
REMOTE_MODS = "/home/blackdamage/minecraft-neoforge-1211/mods"

# =============================================
# 1. İstemciden ItemPhysic'i kaldır
# =============================================
print("=" * 60)
print("  ItemPhysic Crash Fix")
print("=" * 60)

client_files = os.listdir(CLIENT_MODS)
removed_client = []

for f in client_files:
    if f.lower().startswith("itemphysic"):
        full_path = os.path.join(CLIENT_MODS, f)
        print(f"\n  [REMOVE CLIENT] {f}")
        os.remove(full_path)
        removed_client.append(f)

if removed_client:
    print(f"\n  ✅ {len(removed_client)} dosya istemciden kaldırıldı: {', '.join(removed_client)}")
else:
    print("\n  ✅ ItemPhysic istemcide zaten yok.")

# =============================================
# 2. Sunucuda ItemPhysic var mı kontrol et
# =============================================
print("\n  Sunucu kontrol ediliyor...")
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS)
sftp = ssh.open_sftp()

remote_files = sftp.listdir(REMOTE_MODS)
server_physic = [f for f in remote_files if f.lower().startswith("itemphysic")]

if server_physic:
    print(f"  ⚠️  Sunucuda ItemPhysic bulundu, kaldırılıyor: {server_physic}")
    for f in server_physic:
        sftp.remove(f"{REMOTE_MODS}/{f}")
        print(f"  ✅ Sunucudan silindi: {f}")
else:
    print("  ✅ Sunucuda ItemPhysic zaten yok.")

sftp.close()
ssh.close()

# =============================================
# 3. Özet
# =============================================
print("\n" + "=" * 60)
print("  ÖZET")
print("=" * 60)
print(f"  İstemciden kaldırıldı : {len(removed_client)} dosya")
print(f"  Sunucu durumu         : Temiz")
print()
print("  YAPILACAK:")
print("  • Minecraft'ı yeniden başlatın.")
print("  • Sunucuya bağlanın – itemphysic:0c hatası artık olmamalı.")
print("  • (İsteğe bağlı) ItemPhysic'in sunucu uyumlu yeni versiyonu")
print("    çıkarsa tekrar ekleyebilirsiniz.")
print("=" * 60)
