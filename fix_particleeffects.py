# -*- coding: utf-8 -*-
"""
fix_particleeffects.py
ParticleEffects modunu sunucudan kaldir ve sunucuyu yeniden baslat
"""
import paramiko, io, sys, time
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

SSH_HOST = "192.168.1.100"
SSH_USER = "blackdamage"
SSH_PASS = "Gameras6060"
SERVER_DIR = "/home/blackdamage/minecraft-neoforge-1211"
REMOTE_MODS = f"{SERVER_DIR}/mods"

def exec_sudo(ssh, cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd, get_pty=True)
    stdin.write(SSH_PASS + '\n'); stdin.flush()
    exit_status = stdout.channel.recv_exit_status()
    out = stdout.read().decode('utf-8', errors='replace')
    lines = [l for l in out.splitlines() if '[sudo] password for' not in l]
    return exit_status, '\n'.join(lines).strip()

def r(ssh, cmd):
    s, o, e = ssh.exec_command(cmd)
    o.channel.recv_exit_status()
    return o.read().decode('utf-8', errors='replace').strip()

print("=" * 60)
print("  FIX: ParticleEffects Sunucudan Kaldiriliyor")
print("=" * 60)

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=10)
print("SSH OK")

# Sunucuyu durdur
print("\n[1] Sunucu durduruluyor...")
exec_sudo(ssh, "sudo systemctl stop minecraft")
time.sleep(3)

sftp = ssh.open_sftp()

# ParticleEffects'i kaldır - sadece istemci modu
particle_jar = "ParticleEffects-1.5.0+1.21.1+neoforge.jar"
mods_list = sftp.listdir(REMOTE_MODS)
if particle_jar in mods_list:
    sftp.remove(f"{REMOTE_MODS}/{particle_jar}")
    print(f"[2] Kaldirıldı: {particle_jar}")
else:
    print(f"[2] {particle_jar} sunucuda bulunamadi (zaten yok?)")
    # Tüm particle içerenleri ara
    for f in mods_list:
        if 'particle' in f.lower():
            print(f"    Bulunan particle dosyasi: {f}")
            sftp.remove(f"{REMOTE_MODS}/{f}")
            print(f"    Kaldirıldı: {f}")

sftp.close()

# Sunucuyu başlat
print("\n[3] Sunucu baslatiliyor...")
exec_sudo(ssh, "sudo systemctl start minecraft")
print("    Baslatildi, 30 saniye bekleniyor...")
time.sleep(30)

print("\n[4] Boot logu izleniyor (120 saniye)...")
boot_ok = False
for tick in range(1, 13):
    time.sleep(10)
    log = r(ssh, f"tail -n 20 {SERVER_DIR}/logs/latest.log 2>/dev/null")
    svc = r(ssh, "systemctl is-active minecraft 2>/dev/null")
    print(f"\n  --- Tick {tick}/12 | {svc} ---")
    # Sadece son kısmı göster
    lines = log.split('\n')
    for line in lines[-10:]:
        print(f"  {line}")

    log_lower = log.lower()
    if "done" in log_lower and "for help" in log_lower:
        print("\n  *** SUNUCU BASARIYLA ACILDI! ***")
        boot_ok = True
        break
    if "mod loading has failed" in log_lower or "classcastexception" in log_lower or "modloadingexception" in log_lower:
        print("\n  YENI HATA tespit edildi!")
        crash = r(ssh, f"ls -t {SERVER_DIR}/crash-reports/*.txt 2>/dev/null | head -1 | xargs cat 2>/dev/null | head -40")
        print(crash)
        break

ts_ip = r(ssh, "tailscale ip -4 2>/dev/null")
print(f"\n  Sunucu: {ts_ip}:25565")
print(f"  Boot: {'BASARILI' if boot_ok else 'Kontrol gerekiyor'}")
ssh.close()
print("\nTamamlandi.")
