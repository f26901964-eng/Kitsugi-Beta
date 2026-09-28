# -*- coding: utf-8 -*-
import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')

sftp = ssh.open_sftp()
f = sftp.open('/home/blackdamage/minecraft-neoforge-1211/user_jvm_args.txt', 'r')
content = f.read().decode('utf-8')
f.close()

# Remove the unrecognized flag
updated_content = content.replace('-XX:+UseCompactObjectHeaders', '')

f = sftp.open('/home/blackdamage/minecraft-neoforge-1211/user_jvm_args.txt', 'w')
f.write(updated_content.encode('utf-8'))
f.close()

sftp.close()
ssh.close()
print("user_jvm_args.txt successfully updated!")
