import paramiko, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060', timeout=10)
def run(cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    stdout.channel.recv_exit_status()
    return stdout.read().decode('utf-8', errors='replace').strip()

print('Servis:', run('systemctl is-active minecraft'))
print()

# Done mesajını ara
done = run("grep 'Done' /home/blackdamage/minecraft-neoforge-1211/logs/latest.log | tail -3")
print('=== Done mesajı ===')
print(done if done else '(henüz yok)')

# Crash var mı
crash = run("grep -i 'crash' /home/blackdamage/minecraft-neoforge-1211/logs/latest.log | tail -5")
print('\n=== Crash ===')
print(crash if crash else '(yok)')

# Moonrise log
moon = run("grep -i 'moonrise' /home/blackdamage/minecraft-neoforge-1211/logs/latest.log | head -10")
print('\n=== Moonrise log ===')
print(moon if moon else '(yok)')

# Son 5 satır
print('\n=== Son 5 satır ===')
print(run('tail -5 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log'))

ssh.close()
