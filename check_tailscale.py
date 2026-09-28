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

print("=== Tailscale durumu ===")
print(run("tailscale status"))

print("\n=== Tailscale IP ===")
print(run("tailscale ip -4"))

print("\n=== Minecraft portu dinleniyor mu? ===")
print(run("ss -tlnp | grep 25565"))

print("\n=== Sunucu log son 5 satır ===")
print(run("tail -n 5 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log"))

ssh.close()
