# -*- coding: utf-8 -*-
"""Son durum kontrolü - Done mesajı var mı?"""
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

# Done mesajı var mı?
done_check = r(f"grep -i 'done.*for help' {SERVER_DIR}/logs/latest.log 2>/dev/null | tail -3")
print("=== 'DONE' mesaji kontrolu ===")
print(done_check if done_check else "Henuz 'Done' mesaji yok")

# Son loglar
print("\n=== SON 40 LOG SATIRI ===")
log = r(f"tail -n 40 {SERVER_DIR}/logs/latest.log 2>/dev/null")
print(log)

# Crash var mi?
crash_check = r(f"ls -t {SERVER_DIR}/crash-reports/*.txt 2>/dev/null | head -1")
print(f"\n=== SON CRASH RAPORU: {crash_check} ===")
if crash_check:
    crash_time = r(f"stat -c '%Y' '{crash_check}' 2>/dev/null")
    server_start = r("systemctl show minecraft --property=ActiveEnterTimestamp --no-pager 2>/dev/null")
    print(f"  Crash dosyasi: {crash_check}")
    print(f"  Servis baslangici: {server_start}")
    # Son crash iceriği
    crash_txt = r(f"head -30 '{crash_check}' 2>/dev/null")
    print(crash_txt[:1000])

# Screen/Java prosesi
print("\n=== JAVA PROSES ===")
java_proc = r("ps aux | grep java | grep -v grep | awk '{print $1,$2,$3,$4,$11}' | head -3")
print(java_proc if java_proc else "Java prosesi bulunamadi!")

print("\n=== SCREEN SESSIONS ===")
screen = r("screen -ls 2>/dev/null")
print(screen)

print("\n=== SERVIS DURUMU ===")
print(r("systemctl is-active minecraft"))

ssh.close()
