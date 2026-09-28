import paramiko, sys, io, time
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
time.sleep(30)
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')
stdin, stdout, stderr = ssh.exec_command(
    'grep -i "Done\\|Failed to start\\|timed out\\|Exception in thread" '
    '/home/blackdamage/minecraft-neoforge-1211/logs/latest.log | tail -5'
)
out = stdout.read().decode('utf-8', errors='replace')
print("=== KEY LOG LINES ===")
print(out if out.strip() else "(no matches yet — server still loading)")

stdin2, stdout2, stderr2 = ssh.exec_command(
    'systemctl is-active minecraft'
)
print("Service status:", stdout2.read().decode().strip())
ssh.close()
