# -*- coding: utf-8 -*-
import paramiko
import time
import sys

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')

print("Checking java process...")
_, stdout, _ = ssh.exec_command('pgrep -a java')
print(stdout.read().decode())

print("Following latest.log (timeout after 90s)...")
stdin, stdout, stderr = ssh.exec_command('tail -n 20 -f /home/blackdamage/minecraft-neoforge-1211/logs/latest.log')

channel = stdout.channel
channel.setblocking(False)

start_time = time.time()
while True:
    if channel.recv_ready():
        data = channel.recv(4096).decode('utf-8', errors='ignore')
        sys.stdout.write(data.encode('ascii', errors='ignore').decode())
        sys.stdout.flush()
        
        # Check if done or failed
        if "done" in data.lower() or "for help" in data.lower():
            print("\nServer is fully loaded!")
            break
            
    time.sleep(0.5)
    if time.time() - start_time > 90:
        print("\nMonitor timeout.")
        break

ssh.close()
