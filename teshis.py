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
    print(f"\n{'='*60}")
    print(f"  {label}")
    print(f"{'='*60}")
    stdin, stdout, stderr = ssh.exec_command(cmd)
    stdout.channel.recv_exit_status()
    out = stdout.read().decode('utf-8', errors='replace').strip()
    err = stderr.read().decode('utf-8', errors='replace').strip()
    if out:
        print(out)
    if err:
        print(f"[stderr] {err}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=15)
        print("✅ SSH bağlantısı kuruldu.")
    except Exception as e:
        print(f"❌ Bağlantı hatası: {e}")
        return

    # Test 1: Tailscale status — direct mi relay mi?
    run_cmd(ssh,
        "TEST 1: Tailscale status, version ve netcheck",
        "tailscale status; echo '---'; tailscale version; echo '---'; tailscale netcheck"
    )

    # Test 2: Son voicechat log satırları — Dropping voice var mı?
    run_cmd(ssh,
        "TEST 2: Son voicechat logları (Dropping voice kontrolü)",
        "grep -iE 'voicechat|Dropping voice' "
        "/home/blackdamage/minecraft-neoforge-1211/logs/latest.log "
        "| tail -50"
    )

    # Test 3: Sunucu açık mı, kaç saniye oldu?
    run_cmd(ssh,
        "TEST 3: Minecraft servis durumu",
        "systemctl status minecraft --no-pager | head -20"
    )

    # Test 4: Son genel log — "Done" geldi mi?
    run_cmd(ssh,
        "TEST 4: Sunucu açıldı mı? (Done kontrolü)",
        "grep -E 'Done|For help|voicechat.*started|voice chat server' "
        "/home/blackdamage/minecraft-neoforge-1211/logs/latest.log "
        "| tail -10"
    )

    ssh.close()

if __name__ == '__main__':
    main()
