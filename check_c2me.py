import paramiko
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')

def run(cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    return stdout.read().decode('utf-8', errors='replace').strip()

# Check if c2me-opencl backup exists
print("Checking server mods folder...")
result = run("ls /home/blackdamage/minecraft-neoforge-1211/mods/ | grep c2me")
print(f"c2me mods on server:\n{result}")

ssh.close()
