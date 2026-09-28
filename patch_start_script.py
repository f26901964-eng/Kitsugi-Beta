import paramiko
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060', timeout=5)
    sftp = ssh.open_sftp()
    
    # Read mc-start.sh
    path = '/home/blackdamage/mc-start.sh'
    with sftp.open(path, 'r') as f:
        content = f.read().decode('utf-8', errors='replace')
        
    print("=== Original mc-start.sh ===")
    print(content)
    
    # Check if TERM is already set
    if 'export TERM=' not in content:
        # Insert TERM export after #!/bin/bash or at the top of exports
        lines = content.split('\n')
        inserted = False
        for i, line in enumerate(lines):
            if 'export MESA_SHADER_CACHE_MAX_SIZE' in line:
                lines.insert(i, 'export TERM=xterm-256color')
                inserted = True
                break
        if not inserted:
            lines.insert(1, 'export TERM=xterm-256color')
            
        new_content = '\n'.join(lines)
        print("=== Patched mc-start.sh ===")
        print(new_content)
        
        # Write back to server
        with sftp.open(path, 'w') as f:
            f.write(new_content.encode('utf-8'))
        print("Patched mc-start.sh successfully!")
    else:
        print("TERM is already configured in mc-start.sh.")
        
except Exception as e:
    import traceback
    traceback.print_exc()
finally:
    ssh.close()
