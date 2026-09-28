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

def update_config_file(sftp, filepath):
    try:
        # Read the file and decode bytes
        with sftp.open(filepath, 'r') as f:
            content_bytes = f.read()
        content = content_bytes.decode('utf-8', errors='replace')
        
        # Modify parameters
        lines = content.splitlines()
        new_lines = []
        modified = False
        
        for line in lines:
            if line.startswith("voice_host="):
                new_lines.append("voice_host=")  # Set it to empty so it auto-detects based on connection IP
                modified = True
            elif line.startswith("login_timeout="):
                new_lines.append("login_timeout=30000")
                modified = True
            else:
                new_lines.append(line)
                
        if modified:
            new_content = "\n".join(new_lines) + "\n"
            with sftp.open(filepath, 'w') as f:
                f.write(new_content.encode('utf-8'))
            print(f"✅ Config file updated: {filepath}")
        else:
            print(f"⚠️  No changes to make in: {filepath}")
    except IOError as e:
        print(f"ℹ️  File not found: {filepath}")
    except Exception as e:
        print(f"❌ Error updating config {filepath}: {e}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=15)
        print("✅ Connected to SSH server.")
    except Exception as e:
        print(f"❌ Connection failed: {e}")
        return

    # 1. Stop the minecraft server (this will wait until the backup is completed)
    print("⏳ Stopping Minecraft server (waiting for backup to complete)...")
    stdin, stdout, stderr = ssh.exec_command(f"echo '{SSH_PASS}' | sudo -S systemctl stop minecraft 2>/dev/null", get_pty=False)
    stdout.channel.recv_exit_status()
    print("✅ Minecraft server stopped successfully.")

    # 2. Modify configs
    sftp = ssh.open_sftp()
    update_config_file(sftp, "/home/blackdamage/minecraft-neoforge-1211/config/voicechat/voicechat-server.properties")
    update_config_file(sftp, "/home/blackdamage/minecraft-bmc4-server/config/voicechat/voicechat-server.properties")
    sftp.close()

    # 3. Start the server again
    print("🔄 Starting Minecraft server back up...")
    stdin, stdout, stderr = ssh.exec_command(f"echo '{SSH_PASS}' | sudo -S systemctl start minecraft 2>/dev/null", get_pty=False)
    stdout.channel.recv_exit_status()
    print("✅ Minecraft server start command sent.")

    # 4. Verify config persists
    time.sleep(2)
    stdin, stdout, stderr = ssh.exec_command("cat /home/blackdamage/minecraft-neoforge-1211/config/voicechat/voicechat-server.properties | grep -E 'voice_host|login_timeout'")
    print("\n--- Verified configuration on active server ---")
    print(stdout.read().decode('utf-8', errors='replace').strip())

    ssh.close()

if __name__ == '__main__':
    main()
