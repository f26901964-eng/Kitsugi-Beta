import paramiko
import os
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060', timeout=10)
    
    def run(cmd):
        stdin, stdout, stderr = ssh.exec_command(cmd)
        return stdout.read().decode('utf-8', errors='replace').strip()

    print("--- SERVER CRASH REPORTS ---")
    crash_reports = run("ls -la /home/blackdamage/minecraft-neoforge-1211/crash-reports/")
    print(crash_reports if crash_reports else "(no crash reports)")

    print("\n--- DISTANT HORIZONS TOML STAT ---")
    toml_stat = run("ls -la /home/blackdamage/minecraft-neoforge-1211/config/DistantHorizons.toml")
    print(toml_stat)

    print("\n--- CHECKING IF DH JAR WAS EVER ON SERVER (OLD LOGS) ---")
    old_logs_dh = run("zgrep -i -E 'distant|horizon' /home/blackdamage/minecraft-neoforge-1211/logs/*.log.gz | tail -n 20")
    print(old_logs_dh if old_logs_dh else "(none)")

    ssh.close()
except Exception as e:
    print(f"Failed to connect to server: {e}")
