import paramiko
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060', timeout=5)
    
    # Run grep for "Done"
    print("=== GREP 'Done' in latest.log ===")
    stdin, stdout, stderr = ssh.exec_command('grep -i "Done" /home/blackdamage/minecraft-neoforge-1211/logs/latest.log')
    print(stdout.read().decode('utf-8', errors='replace'))
    
    # Run grep for "Failed"
    print("=== GREP 'Failed' in latest.log ===")
    stdin, stdout, stderr = ssh.exec_command('grep -i "Failed" /home/blackdamage/minecraft-neoforge-1211/logs/latest.log')
    print(stdout.read().decode('utf-8', errors='replace'))
    
    # Print the last 20 lines of latest.log
    print("=== LAST 20 LINES OF latest.log ===")
    stdin, stdout, stderr = ssh.exec_command('tail -n 20 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log')
    print(stdout.read().decode('utf-8', errors='replace'))
    
except Exception as e:
    import traceback
    traceback.print_exc()
finally:
    ssh.close()
