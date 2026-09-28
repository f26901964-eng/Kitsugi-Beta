# -*- coding: utf-8 -*-
import paramiko
import sys
import time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')

print("Starting run.sh...")
stdin, stdout, stderr = ssh.exec_command('cd /home/blackdamage/minecraft-neoforge-1211 && ./run.sh nogui')

# Read stdout and stderr in a loop
channel = stdout.channel
channel.setblocking(False)

start_time = time.time()
while True:
    # Check if channel is closed and no data left
    if channel.exit_status_ready() and not channel.recv_ready() and not channel.recv_stderr_ready():
        break
        
    if channel.recv_ready():
        data = channel.recv(1024).decode('utf-8', errors='ignore')
        sys.stdout.write(data.encode('ascii', errors='ignore').decode())
        sys.stdout.flush()
        
    if channel.recv_stderr_ready():
        data_err = channel.recv_stderr(1024).decode('utf-8', errors='ignore')
        sys.stderr.write("ERR: " + data_err.encode('ascii', errors='ignore').decode())
        sys.stderr.flush()
        
    time.sleep(0.1)
    # Stop after 90 seconds if it's still running (it shouldn't take this long to crash if it's crashing)
    if time.time() - start_time > 90:
        print("\nTimeout reached (90s). Closing connection...")
        break

print(f"\nExit status: {channel.recv_exit_status()}")
ssh.close()
