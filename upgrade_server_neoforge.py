# -*- coding: utf-8 -*-
import os
import sys
import io
import time
import paramiko

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

SSH_HOST = "192.168.1.100"
SSH_USER = "blackdamage"
SSH_PASS = "Gameras6060"
SERVER_DIR = "/home/blackdamage/minecraft-neoforge-1211"
VERSION = "21.1.247"

def exec_sudo(ssh, cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd, get_pty=True)
    stdin.write(SSH_PASS + '\n')
    stdin.flush()
    exit_status = stdout.channel.recv_exit_status()
    out = stdout.read().decode('utf-8', errors='replace')
    lines = [l for l in out.splitlines() if '[sudo] password for' not in l]
    return exit_status, '\n'.join(lines).strip()

def exec_ssh(ssh, cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    exit_status = stdout.channel.recv_exit_status()
    return exit_status, stdout.read().decode('utf-8', errors='replace').strip(), stderr.read().decode('utf-8', errors='replace').strip()

def main():
    print("=== STARTING NEOFORGE UPGRADE ===")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    
    try:
        print(f"Connecting to SSH: {SSH_HOST}...")
        ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=15)
        print("Connected successfully!")
        
        # Step 1: Stop server
        print("\nStopping Minecraft service (ensuring it is stopped)...")
        status, out = exec_sudo(ssh, "sudo systemctl stop minecraft")
        print(f"Service stop executed (exit code: {status})")
        time.sleep(2)
        
        # Step 2: Download installer
        url = f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{VERSION}/neoforge-{VERSION}-installer.jar"
        dest = f"{SERVER_DIR}/neoforge-{VERSION}-installer.jar"
        print(f"\nDownloading installer from:\n  {url}\nTo:\n  {dest}...")
        
        dl_cmd = f"wget -q --show-progress {url} -O {dest}"
        status, out, err = exec_ssh(ssh, dl_cmd)
        if status != 0:
            print(f"Download failed with code {status}!\nError: {err}\nOutput: {out}")
            ssh.close()
            return
        print("Download completed successfully!")
        
        # Step 3: Run installer
        print(f"\nRunning installer to install NeoForge {VERSION}...")
        inst_cmd = f"cd {SERVER_DIR} && java -jar neoforge-{VERSION}-installer.jar --installServer"
        status, out, err = exec_ssh(ssh, inst_cmd)
        print(f"Installer finished (exit code: {status})")
        if status != 0:
            print(f"Installation failed!\nError: {err}\nOutput: {out}")
            ssh.close()
            return
        print("Installation completed successfully!")
        
        # Step 4: Update run.sh
        print("\nUpdating run.sh with new NeoForge path...")
        # Read run.sh
        status, run_sh_content, _ = exec_ssh(ssh, f"cat {SERVER_DIR}/run.sh")
        if "21.1.241" in run_sh_content:
            new_run_sh = run_sh_content.replace("21.1.241", VERSION)
            
            # Write updated run.sh back
            sftp = ssh.open_sftp()
            with sftp.open(f"{SERVER_DIR}/run.sh", "w") as f:
                f.write(new_run_sh)
            sftp.close()
            print("Successfully updated run.sh!")
        else:
            print("WARNING: Could not find 21.1.241 in run.sh to replace. Please check run.sh content:")
            print(run_sh_content)
            
        # Step 5: Clean up installer
        print("\nCleaning up installer jar...")
        exec_ssh(ssh, f"rm {dest}")
        print("Installer jar removed.")
        
        # Step 6: Start server
        print("\nStarting Minecraft service...")
        status, out = exec_sudo(ssh, "sudo systemctl start minecraft")
        print(f"Service start executed (exit code: {status})")
        
        # Step 7: Monitor boot logs
        print("\nMonitoring server boot logs (60s)...")
        time.sleep(10)
        for tick in range(1, 7):
            time.sleep(10)
            _, log, _ = exec_ssh(ssh, f"tail -n 20 {SERVER_DIR}/logs/latest.log 2>/dev/null")
            _, status_out, _ = exec_ssh(ssh, "systemctl is-active minecraft 2>/dev/null")
            print(f"\n--- Tick {tick}/6 | Status: {status_out} ---")
            print(log)
            if "done" in log.lower() and "for help" in log.lower():
                print("\nServer successfully started with NeoForge 21.1.247!")
                break
                
        ssh.close()
        print("\nUpgrade completed successfully!")
        
    except Exception as e:
        print(f"Error during upgrade: {e}")

if __name__ == '__main__':
    main()
