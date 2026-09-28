import os, shutil, paramiko

NEW_JAR    = r'C:\Users\Administrator\Downloads\majruszs-enchantments-neoforge-1.21.1-1.10.8.jar'
OLD_LIB    = r'C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods\majrusz-library-neoforge-1.21.1-7.0.8.jar'
CLIENT_DIR = r'C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods'
JAR_NAME   = 'majruszs-enchantments-neoforge-1.21.1-1.10.8.jar'
SERVER_MODS= '/home/blackdamage/minecraft-neoforge-1211/mods'
SERVER_WORLD='/home/blackdamage/minecraft-neoforge-1211/world'

# 1. Client deploy
print("1. Client Deploy")
dst_client = os.path.join(CLIENT_DIR, JAR_NAME)
shutil.copy2(NEW_JAR, dst_client)
print("   OK - Client: " + dst_client)

if os.path.exists(OLD_LIB):
    os.remove(OLD_LIB)
    print("   OK - Eski majrusz-library silindi (artik builtin)")

# 2. Server deploy
print()
print("2. Server Deploy")
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060', timeout=10)

def run(cmd):
    _, o, _ = ssh.exec_command(cmd)
    return o.read().decode('utf-8', errors='replace').strip()

sftp = ssh.open_sftp()
try:
    sftp.remove(f'{SERVER_MODS}/majrusz-library-neoforge-1.21.1-7.0.8.jar')
    print("   OK - Sunucudan eski majrusz-library silindi")
except:
    print("   - Eski library sunucuda yoktu zaten")

sftp.put(NEW_JAR, f'{SERVER_MODS}/{JAR_NAME}')
print("   OK - Sunucu: " + SERVER_MODS + "/" + JAR_NAME)
sftp.close()

# 3. Sunucu world analizi
print()
print("3. Sunucu World Yapisi")
out = run(f'ls {SERVER_WORLD}')
print("   " + out.replace('\n', '\n   '))

# Playerdata klasoru
print()
print("4. Playerdata Listesi")
out = run(f'ls {SERVER_WORLD}/playerdata/ 2>/dev/null | head -20')
if out:
    print("   " + out.replace('\n', '\n   '))
else:
    print("   Playerdata bulunamadi")

ssh.close()
print()
print("TAMAMLANDI!")
