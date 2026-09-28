import paramiko
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060', timeout=5)
    
    # Get PID of the java process
    stdin, stdout, stderr = ssh.exec_command('pgrep -f "java @user_jvm_args.txt"')
    pid_str = stdout.read().decode().strip()
    if not pid_str:
        # Try generic java PID
        stdin, stdout, stderr = ssh.exec_command('pgrep -f java')
        pid_str = stdout.read().decode().strip()
    
    if not pid_str:
        print("No running java process found.")
        sys.exit(0)
    
    pid = pid_str.split()[0]
    print(f"Dumping jstack for PID: {pid}")
    
    # Dump jstack to a file
    stdin, stdout, stderr = ssh.exec_command(f'jstack {pid} > /home/blackdamage/jstack.txt')
    stdout.read() # Wait for execution
    
    # Read the jstack file content
    sftp = ssh.open_sftp()
    with sftp.open('/home/blackdamage/jstack.txt', 'r') as f:
        content = f.read().decode('utf-8', errors='replace')
        
    print("Total lines in jstack dump:", len(content.split('\n')))
    
    # Search for "main" or "Server thread"
    lines = content.split('\n')
    found = False
    for i, line in enumerate(lines):
        if '"main"' in line or '"Server thread"' in line:
            found = True
            print(f"--- Thread Match: {line} ---")
            for j in range(i, min(i + 40, len(lines))):
                print(lines[j])
            print("\n")
            
    if not found:
        print("Did not find main or Server thread in the dump. Printing first 100 lines of dump instead:")
        for line in lines[:100]:
            print(line)
            
except Exception as e:
    import traceback
    traceback.print_exc()
finally:
    ssh.close()
