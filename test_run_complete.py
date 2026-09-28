# -*- coding: utf-8 -*-
import paramiko
import time
import sys

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')

print("1. Killing all java and screen processes to ensure a clean state...")
ssh.exec_command('pkill -9 -f java')
ssh.exec_command('screen -wipe')
time.sleep(2)

print("2. Starting `./run.sh nogui` interactively...")
stdin, stdout, stderr = ssh.exec_command('cd /home/blackdamage/minecraft-neoforge-1211 && ./run.sh nogui')

channel = stdout.channel
channel.setblocking(False)

print("3. Streaming output (will wait up to 10 minutes for startup)...")
start_time = time.time()
last_output_time = time.time()

try:
    while True:
        # Check if process exited
        if channel.exit_status_ready():
            # Read any remaining data
            while channel.recv_ready():
                data = channel.recv(4096).decode('utf-8', errors='ignore')
                sys.stdout.write(data.encode('ascii', errors='ignore').decode())
            while channel.recv_stderr_ready():
                data_err = channel.recv_stderr(4096).decode('utf-8', errors='ignore')
                sys.stderr.write(data_err.encode('ascii', errors='ignore').decode())
            break
            
        # Read stdout
        if channel.recv_ready():
            data = channel.recv(4096).decode('utf-8', errors='ignore')
            sys.stdout.write(data.encode('ascii', errors='ignore').decode())
            sys.stdout.flush()
            last_output_time = time.time()
            
        # Read stderr
        if channel.recv_stderr_ready():
            data_err = channel.recv_stderr(4096).decode('utf-8', errors='ignore')
            sys.stderr.write(data_err.encode('ascii', errors='ignore').decode())
            sys.stderr.flush()
            last_output_time = time.time()
            
        # Check if server started successfully
        # Note: we check latest.log or the console output for "Done" or "For help"
        
        time.sleep(0.1)
        
        # Safety timeouts
        elapsed = time.time() - start_time
        if elapsed > 600: # 10 minutes maximum
            print("\nMaximum timeout of 10 minutes reached!")
            break
            
        # If no output has been received for 3 minutes, it might be hung
        if time.time() - last_output_time > 180:
            print("\nNo output received for 3 minutes. Potential hang.")
            break
            
except KeyboardInterrupt:
    print("\nInterrupted by user. Killing process...")
    
exit_status = channel.recv_exit_status()
print(f"\nProcess exited with status: {exit_status}")
ssh.close()
