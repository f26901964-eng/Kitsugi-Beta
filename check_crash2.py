# -*- coding: utf-8 -*-
"""Yeni crash raporunu kontrol et"""
import paramiko, io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

SSH_HOST = "192.168.1.100"
SSH_USER = "blackdamage"
SSH_PASS = "Gameras6060"
SERVER_DIR = "/home/blackdamage/minecraft-neoforge-1211"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=10)

def r(cmd):
    s, o, e = ssh.exec_command(cmd)
    o.channel.recv_exit_status()
    return o.read().decode('utf-8', errors='replace').strip()

# Son crash raporu
print("=== SON CRASH RAPORU ===")
latest = r(f"ls -t {SERVER_DIR}/crash-reports/*.txt 2>/dev/null | head -1")
print(f"Dosya: {latest}")
if latest:
    content = r(f"cat '{latest}'")
    print(content[:5000])

print("\n=== LATEST.LOG SON 30 SATIR ===")
log = r(f"tail -n 30 {SERVER_DIR}/logs/latest.log 2>/dev/null")
print(log)

print("\n=== SERVİS DURUM ===")
print(r("systemctl is-active minecraft"))
print(r(f"ls {SERVER_DIR}/mods/*.jar 2>/dev/null | wc -l") + " mod var sunucuda")

ssh.close()
