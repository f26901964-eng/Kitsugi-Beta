# -*- coding: utf-8 -*-
import paramiko
import time
import sys

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')

print("1. Killing all java processes...")
ssh.exec_command('pkill -9 -f java')
time.sleep(2)

print("2. Wiping screen...")
ssh.exec_command('screen -wipe')
time.sleep(1)

print("3. Restarting minecraft service via systemctl...")
stdin, stdout, stderr = ssh.exec_command('sudo systemctl restart minecraft', get_pty=True)
stdin.write('Gameras6060\n')
stdin.flush()
stdout.channel.recv_exit_status()
print("Service start command finished.")
time.sleep(10)

print("4. Monitoring logs...")
for i in range(1, 10):
    time.sleep(10)
    _, p_java, _ = ssh.exec_command('pgrep -a java')
    java_proc = p_java.read().decode().strip()
    
    _, p_log, _ = ssh.exec_command('tail -n 15 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log')
    log_content = p_log.read().decode('utf-8', errors='ignore').encode('ascii', errors='ignore').decode()
    
    print(f"\n--- Check {i}/9 ---")
    print(f"Java Process: {java_proc if java_proc else 'None'}")
    print("Latest log:")
    print(log_content)
    
    if "done" in log_content.lower() and "for help" in log_content.lower():
        print("\nServer successfully started!")
        break
        
ssh.close()
