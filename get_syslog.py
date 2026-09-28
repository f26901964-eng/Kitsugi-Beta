# -*- coding: utf-8 -*-
import paramiko
import sys

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')

stdin, stdout, stderr = ssh.exec_command('sudo journalctl -n 100 --no-pager', get_pty=True)
stdin.write('Gameras6060\n')
stdin.flush()

print(stdout.read().decode('utf-8', errors='ignore').encode('ascii', errors='ignore').decode())
ssh.close()
