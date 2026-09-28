# -*- coding: utf-8 -*-
import paramiko
import sys
import io
import time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

SSH_HOST = "192.168.1.100"
SSH_USER = "blackdamage"
SSH_PASS = "Gameras6060"

def update_config_file(sftp, filepath):
    try:
        with sftp.open(filepath, 'r') as f:
            content_bytes = f.read()
        content = content_bytes.decode('utf-8', errors='replace')
        
        lines = content.splitlines()
        new_lines = []
        
        for line in lines:
            if line.startswith("mtu_size="):
                new_lines.append("mtu_size=1000")        # Tailscale MTU=1280, 1275+28=1303 > 1280 → paket düşüyor
                print(f"  mtu_size: {line} → mtu_size=1000")
            elif line.startswith("login_timeout="):
                new_lines.append("login_timeout=10000")  # geri al, force_voice_chat=false iken etkisiz
                print(f"  login_timeout: {line} → login_timeout=10000 (revert)")
            elif line.startswith("keep_alive="):
                new_lines.append("keep_alive=1000")       # varsayılan, dokunma
                print(f"  keep_alive: {line} → keep_alive=1000 (korundu)")
            elif line.startswith("voice_host="):
                new_lines.append("voice_host=")           # boş bırak, auto-detect
                print(f"  voice_host: → boş (auto-detect)")
            else:
                new_lines.append(line)
                
        new_content = "\n".join(new_lines) + "\n"
        with sftp.open(filepath, 'w') as f:
            f.write(new_content.encode('utf-8'))
        print(f"✅ Config yazıldı: {filepath}")
    except IOError:
        print(f"ℹ️  Dosya bulunamadı: {filepath}")
    except Exception as e:
        print(f"❌ Hata: {filepath}: {e}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=15)
        print("✅ SSH bağlantısı kuruldu.")
    except Exception as e:
        print(f"❌ Bağlantı hatası: {e}")
        return

    # 1. Sunucuyu güvenli durdur (yedekleme bitene kadar bekler)
    print("\n⏳ Sunucu durduruluyor (yedekleme bitmesi bekleniyor)...")
    stdin, stdout, stderr = ssh.exec_command(
        f"echo '{SSH_PASS}' | sudo -S systemctl stop minecraft 2>/dev/null",
        get_pty=False
    )
    stdout.channel.recv_exit_status()
    print("✅ Sunucu durduruldu.")

    # 2. Config güncelle
    print("\n📝 Config güncelleniyor...")
    sftp = ssh.open_sftp()
    update_config_file(sftp, "/home/blackdamage/minecraft-neoforge-1211/config/voicechat/voicechat-server.properties")
    sftp.close()

    # 3. Sunucuyu başlat
    print("\n🔄 Sunucu başlatılıyor...")
    stdin, stdout, stderr = ssh.exec_command(
        f"echo '{SSH_PASS}' | sudo -S systemctl start minecraft 2>/dev/null",
        get_pty=False
    )
    stdout.channel.recv_exit_status()
    print("✅ Sunucu başlatıldı.")

    # 4. Config doğrula
    time.sleep(2)
    print("\n--- Aktif config doğrulama ---")
    stdin, stdout, stderr = ssh.exec_command(
        "grep -E 'mtu_size|keep_alive|login_timeout|voice_host' "
        "/home/blackdamage/minecraft-neoforge-1211/config/voicechat/voicechat-server.properties"
    )
    print(stdout.read().decode('utf-8', errors='replace').strip())

    # 5. Log'dan Tailscale MTU ile ilgili hata satırları çek
    print("\n--- Son voicechat log satırları (Dropping voice kontrol) ---")
    stdin, stdout, stderr = ssh.exec_command(
        "grep -iE 'voicechat|Dropping voice' "
        "/home/blackdamage/minecraft-neoforge-1211/logs/latest.log | tail -50"
    )
    print(stdout.read().decode('utf-8', errors='replace').strip())

    ssh.close()

if __name__ == '__main__':
    main()
