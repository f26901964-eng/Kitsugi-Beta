# -*- coding: utf-8 -*-
import paramiko
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

SSH_HOST = "192.168.1.100"
SSH_USER = "blackdamage"
SSH_PASS = "Gameras6060"

def run_cmd(ssh, label, cmd):
    print(f"\n{'='*50}")
    print(f" {label}")
    print(f"{'='*50}")
    stdin, stdout, stderr = ssh.exec_command(cmd)
    stdout.channel.recv_exit_status()
    out = stdout.read().decode('utf-8', errors='replace').strip()
    err = stderr.read().decode('utf-8', errors='replace').strip()
    if out:
        print(out)
    if err:
        print(f"Hata/Stderr: {err}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=15)
        print("✅ Sunucuya bağlanıldı. Donanım tespiti yapılıyor...")
    except Exception as e:
        print(f"❌ Bağlantı hatası: {e}")
        return

    # 1. CPU Bilgisi
    run_cmd(ssh, "1. CPU (İşlemci) Bilgisi", "lscpu | grep -E 'Model name|Thread|Core|Socket|CPU\(s\):|CPU MHz'")

    # 2. RAM (Bellek) Bilgisi
    run_cmd(ssh, "2. Bellek (RAM) Bilgisi", "free -h")

    # 3. Disk (Depolama) Bilgisi
    run_cmd(ssh, "3. Depolama (Disk) Bilgisi", "lsblk -o NAME,SIZE,TYPE,MOUNTPOINT,MODEL,FSTYPE 2>/dev/null || df -h")

    # 4. Ekran Kartı (GPU) Bilgisi
    run_cmd(ssh, "4. Ekran Kartı (GPU) Bilgisi", "lspci | grep -iE 'vga|3d|display|nvidia|amd|intel'; echo '--- Nvidia Sürücü Durumu ---'; nvidia-smi 2>/dev/null || echo 'Nvidia-smi bulunamadı (Nvidia kart yok veya sürücü yüklü değil)'")

    # 5. Anakart Bilgisi
    run_cmd(ssh, "5. Anakart Bilgisi", "echo 'Anakart Üreticisi ve Modeli:'; echo 'Gamera PASS gerektirebilir (sudo dmidecode)...'; echo 'Gameras6060' | sudo -S dmidecode -t baseboard 2>/dev/null | grep -E 'Manufacturer|Product Name|Version|Serial Number'")

    # 6. İşletim Sistemi (OS) Bilgisi
    run_cmd(ssh, "6. İşletim Sistemi Bilgisi", "uname -a; cat /etc/os-release | grep -E 'PRETTY_NAME|VERSION='")

    ssh.close()

if __name__ == '__main__':
    main()
