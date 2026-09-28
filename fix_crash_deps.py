# -*- coding: utf-8 -*-
"""
fix_crash_deps.py
=================
Crash sebebi tespite göre sunucu mod sorunlarını düzeltir:
1. owo-lib ekle (accessories için - Aether bağımlılığı)
2. c2me-opts-accel-opencl'yi kaldır, base c2me koy
3. journeymap-webmap'i kaldır (sunucuda journeymap yok)
4. cyclic 1.14.2'yi kaldır, eski 1.14.1'e geri dön

Ardından sunucuyu yeniden başlatır.
"""
import paramiko
import os
import sys
import io
import time
import shutil

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

SSH_HOST   = "192.168.1.100"
SSH_USER   = "blackdamage"
SSH_PASS   = "Gameras6060"
SERVER_DIR = "/home/blackdamage/minecraft-neoforge-1211"
REMOTE_MODS = f"{SERVER_DIR}/mods"
NEW_MODS    = r"C:\Users\Administrator\Downloads\Kitsugi_Mods_1.21.1_NeoForge"
CLIENT_MODS = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"

def exec_sudo(ssh, cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd, get_pty=True)
    stdin.write(SSH_PASS + '\n')
    stdin.flush()
    exit_status = stdout.channel.recv_exit_status()
    out = stdout.read().decode('utf-8', errors='replace')
    lines = [l for l in out.splitlines() if '[sudo] password for' not in l]
    return exit_status, '\n'.join(lines).strip()

def exec_ssh(ssh, cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    stdout.channel.recv_exit_status()
    return stdout.read().decode('utf-8', errors='replace').strip()

print("=" * 60)
print("  CRASH FIX - Mod Uyumluluk Duzeltme")
print("=" * 60)

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=10)
print("SSH baglanti OK")

# Sunucuyu durdur
print("\n[1] Sunucu durduruluyor...")
exec_sudo(ssh, "sudo systemctl stop minecraft")
time.sleep(3)
print("    OK")

sftp = ssh.open_sftp()
remote_files = sftp.listdir(REMOTE_MODS)
remote_set = set(remote_files)

print("\n[2] Sorunlu modlar duzeltiliyor...")

# -----------------------------------------------------------------
# FIX 1: c2me-opts-accel-opencl kaldir, yerine standart c2me koy
# Sorun: opencl variant bagimsiz calismaz, base c2me jar gerekiyor
# -----------------------------------------------------------------
opencl_jar = "c2me-neoforge-opts-accel-opencl-mc1.21.1-0.4.0-alpha.0.116.jar"
base_c2me   = "c2me-neoforge-mc1.21.1-0.4.0-alpha.0.116.jar"  # eski istemci sürümü

if opencl_jar in remote_set:
    print(f"    Kaldiriliyor (opencl variant sorunlu): {opencl_jar}")
    sftp.remove(f"{REMOTE_MODS}/{opencl_jar}")

# base c2me istemci mods klasöründe var mı? (silinmiş olabilir)
# Kontrol et ve geri yükle
base_c2me_client = os.path.join(CLIENT_MODS, base_c2me)
if os.path.exists(base_c2me_client):
    if base_c2me not in remote_set:
        print(f"    Yukleniyor (base c2me): {base_c2me}")
        sftp.put(base_c2me_client, f"{REMOTE_MODS}/{base_c2me}")
else:
    # Yeni modlar klasöründe de yok, opencl'yi kaldırdık, yedek c2me bul
    # Sunucuda c2me olmayacak, bu tür chunk optimizasyonu olmasın
    print(f"    UYARI: base c2me jar bulunamadi. c2me olmadan devam edilecek.")

# -----------------------------------------------------------------
# FIX 2: journeymap-webmap kaldir (sunucuda journeymap yok, 1.21.4 için)
# -----------------------------------------------------------------
jm_webmap = "journeymap-webmap-neoforge-1.21.4-1.0.10.jar"
if jm_webmap in remote_set:
    print(f"    Kaldiriliyor (uyumsuz versiyon): {jm_webmap}")
    sftp.remove(f"{REMOTE_MODS}/{jm_webmap}")
    # İstemciden de kaldır
    jm_client = os.path.join(CLIENT_MODS, jm_webmap)
    if os.path.exists(jm_client):
        os.remove(jm_client)
        print(f"    Istemciden de silindi: {jm_webmap}")

# -----------------------------------------------------------------
# FIX 3: cyclic 1.14.2 kaldir, 1.14.1'e geri don
# Sorun: 1.14.2 NeoForge 21.1.241 istiyor, sunucuda 21.1.238 var
# -----------------------------------------------------------------
cyclic_new = "cyclic-1.21.1-1.14.2.jar"
cyclic_old = "cyclic-1.21.1-1.14.1.jar"

if cyclic_new in remote_set:
    print(f"    Kaldiriliyor (NF 21.1.241 gerektirir): {cyclic_new}")
    sftp.remove(f"{REMOTE_MODS}/{cyclic_new}")

# Eski sürümü Downloads'dan bul
cyclic_old_dl = os.path.join(NEW_MODS, cyclic_old)
if os.path.exists(cyclic_old_dl):
    print(f"    Geri yukleniyor (eski stabil): {cyclic_old}")
    sftp.put(cyclic_old_dl, f"{REMOTE_MODS}/{cyclic_old}")
    print(f"    Istemciye de kopyalaniyor: {cyclic_old}")
    shutil.copy2(cyclic_old_dl, os.path.join(CLIENT_MODS, cyclic_old))
else:
    # İstemci klasöründe var mı?
    cyclic_old_client = os.path.join(CLIENT_MODS, cyclic_old)
    if os.path.exists(cyclic_old_client):
        print(f"    Istemciden yukleniyor: {cyclic_old}")
        sftp.put(cyclic_old_client, f"{REMOTE_MODS}/{cyclic_old}")
    else:
        print(f"    KRITIK: {cyclic_old} hic bulunamadi! Cyclic sunucuda olmayacak.")

# -----------------------------------------------------------------
# FIX 4: owo-lib ekle (accessories/Aether JiJ bağımlılığı)
# Sunucuda owo-lib eksik - yeni klasörden yükle
# -----------------------------------------------------------------
owo_jar = "owo-lib-neoforge-0.12.15.5-beta.1+1.21.jar"
# Remote'da yok mu?
remote_files_updated = sftp.listdir(REMOTE_MODS)
if owo_jar not in remote_files_updated:
    owo_src = os.path.join(NEW_MODS, owo_jar)
    if os.path.exists(owo_src):
        print(f"    Yukleniyor (owo-lib - accessories dep): {owo_jar}")
        sftp.put(owo_src, f"{REMOTE_MODS}/{owo_jar}")
    else:
        print(f"    UYARI: {owo_jar} kaynak klasorde bulunamadi!")
else:
    print(f"    owo-lib zaten mevcut: {owo_jar}")

# Mevcut durumu göster
final_mods = sftp.listdir(REMOTE_MODS)
final_jars = [f for f in final_mods if f.endswith('.jar')]
print(f"\n  Sunucu toplam mod sayisi: {len(final_jars)}")

sftp.close()

# -----------------------------------------------------------------
# Sunucuyu yeniden başlat
# -----------------------------------------------------------------
print("\n[3] Sunucu yeniden baslatiliyor...")
exec_sudo(ssh, "sudo systemctl start minecraft")
print("    Komut gonderildi. 25 saniye bekleniyor...")
time.sleep(25)

# Log takibi
print("\n[4] Boot logu izleniyor (90 saniye)...")
boot_ok = False
for tick in range(1, 10):
    time.sleep(10)
    log = exec_ssh(ssh, f"tail -n 15 {SERVER_DIR}/logs/latest.log 2>/dev/null")
    svc = exec_ssh(ssh, "systemctl is-active minecraft 2>/dev/null")
    print(f"\n  --- Tick {tick}/9 | {svc} ---")
    print(log)
    
    log_lower = log.lower()
    if "done" in log_lower and "for help" in log_lower:
        print("\n  BASARILI! Sunucu acildi!")
        boot_ok = True
        break
    if "mod loading has failed" in log_lower or "loaderexception" in log_lower.replace(" ", ""):
        print("\n  UYARI: Hata tespit edildi! Crash raporu kontrol ediliyor...")
        crash_content = exec_ssh(ssh, f"ls -t {SERVER_DIR}/crash-reports/*.txt 2>/dev/null | head -1 | xargs cat 2>/dev/null | head -60")
        print(crash_content)
        break

if not boot_ok:
    final_svc = exec_ssh(ssh, "systemctl is-active minecraft 2>/dev/null")
    print(f"\n  Final servis durumu: {final_svc}")
    if final_svc != "active":
        print("  Son crash raporu:")
        crash = exec_ssh(ssh, f"ls -t {SERVER_DIR}/crash-reports/*.txt 2>/dev/null | head -1 | xargs cat 2>/dev/null | head -50")
        print(crash)

ts_ip = exec_ssh(ssh, "tailscale ip -4 2>/dev/null")
print(f"\n  Sunucu: {ts_ip}:25565")
print(f"  Boot: {'BASARILI' if boot_ok else 'Kontrol gerekiyor'}")

ssh.close()
print("\nFix tamamlandi.")
