# -*- coding: utf-8 -*-
import paramiko
import sys
import io
import time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

SSH_HOST = "192.168.1.100"
SSH_USER = "blackdamage"
SSH_PASS = "Gameras6060"

def exec_sudo(ssh, cmd):
    print(f"Running sudo: {cmd}")
    stdin, stdout, stderr = ssh.exec_command(f"echo '{SSH_PASS}' | sudo -S {cmd} 2>/dev/null", get_pty=False)
    stdout.channel.recv_exit_status()
    out = stdout.read().decode('utf-8', errors='replace').strip()
    err = stderr.read().decode('utf-8', errors='replace').strip()
    if out:
        print(f"Out: {out}")
    if err:
        print(f"Err: {err}")

def exec_cmd(ssh, cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    stdout.channel.recv_exit_status()
    return stdout.read().decode('utf-8', errors='replace').strip()

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=15)
        print("✅ Connected to SSH server.")
    except Exception as e:
        print(f"❌ Connection failed: {e}")
        return

    # Fix ownership of voicechat config folder in both server directories
    exec_sudo(ssh, "chown -R blackdamage:blackdamage /home/blackdamage/minecraft-neoforge-1211/config/voicechat")
    exec_sudo(ssh, "chown -R blackdamage:blackdamage /home/blackdamage/minecraft-bmc4-server/config/voicechat")

    # Restart minecraft server to generate the config file
    print("🔄 Restarting Minecraft server to generate voicechat configs...")
    exec_sudo(ssh, "systemctl restart minecraft")
    
    # Wait for startup to initialize the voicechat mod
    print("Waiting 20 seconds for the server to boot and load voicechat...")
    time.sleep(20)

    # Check if the properties file got generated
    print("\n--- Check generated files ---")
    files_1211 = exec_cmd(ssh, "ls -la /home/blackdamage/minecraft-neoforge-1211/config/voicechat")
    print("NeoForge 1211 config dir:")
    print(files_1211)

    ssh.close()

if __name__ == '__main__':
    main()
