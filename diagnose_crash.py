# -*- coding: utf-8 -*-
"""
diagnose_crash.py
Sunucu crash sebebini tam log alarak tespit eder
"""
import paramiko
import io
import sys
import time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

SSH_HOST = "192.168.1.100"
SSH_USER = "blackdamage"
SSH_PASS = "Gameras6060"
SERVER_DIR = "/home/blackdamage/minecraft-neoforge-1211"

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=10)
print("SSH baglandi.")

def exec_ssh(cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    stdout.channel.recv_exit_status()
    return stdout.read().decode('utf-8', errors='replace').strip()

# systemctl durumu
status = exec_ssh("systemctl status minecraft --no-pager 2>&1 | head -30")
print("=== SYSTEMCTL STATUS ===")
print(status)

# Sunucu durumu
is_active = exec_ssh("systemctl is-active minecraft")
print(f"\nServis durumu: {is_active}")

# Tam log - ilk 200 satir (crash baslangici)
print("\n=== LATEST.LOG (ilk 100 satir) ===")
log_head = exec_ssh(f"head -n 100 {SERVER_DIR}/logs/latest.log 2>/dev/null")
print(log_head)

print("\n=== LATEST.LOG (son 60 satir - hata detayi) ===")
log_tail = exec_ssh(f"tail -n 60 {SERVER_DIR}/logs/latest.log 2>/dev/null")
print(log_tail)

# crash-reports klasoru var mi?
print("\n=== CRASH REPORTS ===")
crash = exec_ssh(f"ls -la {SERVER_DIR}/crash-reports/ 2>/dev/null | tail -5")
print(crash if crash else "Crash reports klasoru bos veya yok.")

# En son crash raporu
latest_crash = exec_ssh(f"ls -t {SERVER_DIR}/crash-reports/*.txt 2>/dev/null | head -1")
if latest_crash:
    print(f"\n=== EN SON CRASH RAPORU: {latest_crash} ===")
    crash_content = exec_ssh(f"cat '{latest_crash}' 2>/dev/null | head -80")
    print(crash_content)

# Sunucu restart sayisi (son 5 dakika)
print("\n=== JOURNALCTL (son 30 kayit) ===")
journal = exec_ssh("journalctl -u minecraft -n 30 --no-pager 2>/dev/null")
print(journal)

ssh.close()
print("\nTani tamamlandi.")
