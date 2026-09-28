import paramiko
import sys
import io
import time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060', timeout=5)
    
    print("Executing systemctl restart minecraft via sudo...")
    stdin, stdout, stderr = ssh.exec_command('sudo -S systemctl restart minecraft')
    stdin.write('Gameras6060\n')
    stdin.flush()
    
    # Wait for the systemctl restart command to complete
    restart_err = stderr.read().decode('utf-8', errors='replace')
    restart_out = stdout.read().decode('utf-8', errors='replace')
    print("Restart output:", restart_out)
    if restart_err:
        print("Restart stderr:", restart_err)
        
    print("Waiting 15 seconds for initial process spawn...")
    time.sleep(15)
    
    # Check if java process is running
    print("Checking java process...")
    stdin, stdout, stderr = ssh.exec_command('pgrep -f "java @user_jvm_args.txt"')
    pid = stdout.read().decode().strip()
    if pid:
        print(f"Java process spawned with PID: {pid}")
    else:
        print("Java process NOT found. Checking generic java...")
        stdin, stdout, stderr = ssh.exec_command('pgrep -f java')
        pid = stdout.read().decode().strip()
        if pid:
            print(f"Generic Java PID: {pid}")
        else:
            print("No Java process running at all!")
            
    # Check systemctl status
    print("Checking systemctl status minecraft...")
    stdin, stdout, stderr = ssh.exec_command('systemctl status minecraft')
    print(stdout.read().decode('utf-8', errors='replace'))
    
except Exception as e:
    import traceback
    traceback.print_exc()
finally:
    ssh.close()
