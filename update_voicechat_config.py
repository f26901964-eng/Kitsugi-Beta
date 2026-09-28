# -*- coding: utf-8 -*-
import paramiko
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

SSH_HOST = "192.168.1.100"
SSH_USER = "blackdamage"
SSH_PASS = "Gameras6060"
TAILSCALE_IP = "100.70.34.111"

def update_config_file(sftp, filepath):
    try:
        # Read the file
        with sftp.open(filepath, 'r') as f:
            content = f.read()
        
        # Modify the parameters
        lines = content.splitlines()
        new_lines = []
        modified = False
        
        for line in lines:
            if line.startswith("voice_host="):
                new_lines.append(f"voice_host={TAILSCALE_IP}")
                modified = True
            elif line.startswith("login_timeout="):
                new_lines.append("login_timeout=30000")
                modified = True
            else:
                new_lines.append(line)
                
        if modified:
            new_content = "\n".join(new_lines) + "\n"
            with sftp.open(filepath, 'w') as f:
                f.write(new_content)
            print(f"✅ Successfully updated: {filepath}")
        else:
            print(f"⚠️  No modifications made to: {filepath}")
    except FileNotFoundError:
        print(f"ℹ️  File not found (might not be initialized yet): {filepath}")
    except Exception as e:
        print(f"❌ Error updating {filepath}: {e}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=15)
        print("✅ Connected to SSH server.")
    except Exception as e:
        print(f"❌ Connection failed: {e}")
        return

    sftp = ssh.open_sftp()
    
    # Update NeoForge 1211 config
    update_config_file(sftp, "/home/blackdamage/minecraft-neoforge-1211/config/voicechat/voicechat-server.properties")
    
    # Update BMC4 config
    update_config_file(sftp, "/home/blackdamage/minecraft-bmc4-server/config/voicechat/voicechat-server.properties")
    
    sftp.close()

    # Restart minecraft server to apply changes
    print("🔄 Restarting Minecraft server to apply the updated voicechat configurations...")
    stdin, stdout, stderr = ssh.exec_command(f"echo '{SSH_PASS}' | sudo -S systemctl restart minecraft 2>/dev/null", get_pty=False)
    stdout.channel.recv_exit_status()
    print("✅ Minecraft server restart command completed.")

    ssh.close()

if __name__ == '__main__':
    main()
