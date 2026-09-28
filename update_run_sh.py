# -*- coding: utf-8 -*-
import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')

sftp = ssh.open_sftp()
f = sftp.open('/home/blackdamage/minecraft-neoforge-1211/run.sh', 'w')
f.write('#!/usr/bin/env sh\n/usr/lib/jvm/java-1.21.0-openjdk-amd64/bin/java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.247/unix_args.txt "$@"\n')
f.close()

sftp.close()
ssh.close()
print("run.sh successfully updated!")
