import paramiko, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060', timeout=10)

def run(cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    stdout.channel.recv_exit_status()
    return stdout.read().decode('utf-8', errors='replace').strip()

print('=== Server Status ===')
print('Service:', run('systemctl is-active minecraft'))

print('\n=== Tail of latest.log ===')
log_tail = run('tail -200 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log')
print(log_tail)

ssh.close()
