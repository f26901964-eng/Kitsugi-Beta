# -*- coding: utf-8 -*-
"""
migrate_to_moonrise.py
======================
C2ME + ScalableLux + ServerCore → Moonrise geçişi
Hem yerel client hem sunucu tarafı
"""
import os, sys, io, urllib.request, json, time, paramiko, shutil

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

CLIENT_MODS = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"
SSH_HOST    = "192.168.1.100"
SSH_USER    = "blackdamage"
SSH_PASS    = "Gameras6060"
SERVER_DIR  = "/home/blackdamage/minecraft-neoforge-1211"
REMOTE_MODS = f"{SERVER_DIR}/mods"

# Kaldırılacaklar
REMOVE_LOCAL = [
    "c2me-neoforge-mc1.21.1-0.4.0-alpha.0.116.jar",
    "ScalableLux-0.1.0.1+neoforge.1cb1e91-all.jar",
    # ScalableLux yeni isimle de gelmiş olabilir
    "ScalableLux-neoforge-0.3.0-alpha.0.6-all.jar",
]
REMOVE_SERVER_EXTRA = [
    "servercore-neoforge-1.5.19+1.21.1.jar",  # sunucudan da kaldır
]

def sep(t):
    print(f"\n{'='*60}\n  {t}\n{'='*60}")

def dl(url, dest):
    req = urllib.request.Request(url, headers={"User-Agent": "ModMigrator/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r, open(dest, "wb") as f:
        data = r.read(); f.write(data)
    return len(data)

def get_latest(slug, mc="1.21.1", loader="neoforge"):
    req = urllib.request.Request(
        f"https://api.modrinth.com/v2/project/{slug}/version",
        headers={"User-Agent": "ModMigrator/1.0"}
    )
    data = json.loads(urllib.request.urlopen(req, timeout=10).read())
    matches = [v for v in data
               if mc in v.get("game_versions", [])
               and loader in v.get("loaders", [])
               and v.get("files")]
    return matches[0] if matches else None

# ══════════════════════════════════════════════════════
sep("AŞAMA 1: Moonrise & MoonriseCompats indiriliyor...")
# ══════════════════════════════════════════════════════

downloads = {}

for slug, label in [("moonrise-opt", "Moonrise"), ("moonrise-compats", "MoonriseCompats")]:
    print(f"\n  [{label}] Modrinth'ten alınıyor...")
    ver = get_latest(slug)
    if not ver:
        print(f"  ❌ {label} bulunamadı!")
        sys.exit(1)
    fname = ver["files"][0]["filename"]
    url   = ver["files"][0]["url"]
    vnum  = ver["version_number"]
    dest  = os.path.join(CLIENT_MODS, fname)
    size  = dl(url, dest)
    print(f"  ✅ {label} v{vnum} indirildi: {fname} ({size//1024} KB)")
    downloads[label] = {"fname": fname, "path": dest, "url": url}
    time.sleep(0.3)

# ══════════════════════════════════════════════════════
sep("AŞAMA 2: Yerel mods'tan eski modlar kaldırılıyor...")
# ══════════════════════════════════════════════════════

for fname in REMOVE_LOCAL:
    fpath = os.path.join(CLIENT_MODS, fname)
    if os.path.exists(fpath):
        os.remove(fpath)
        print(f"  ✅ Silindi (yerel): {fname}")
    else:
        print(f"  ℹ️  Zaten yok (yerel): {fname}")

# ══════════════════════════════════════════════════════
sep("AŞAMA 3: SSH bağlantısı + sunucu durdurma...")
# ══════════════════════════════════════════════════════

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=10)
print("  ✅ SSH bağlandı!")

def run(cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    stdout.channel.recv_exit_status()
    return stdout.read().decode('utf-8', errors='replace').strip()

def sudo(cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd, get_pty=True)
    stdin.write(SSH_PASS + '\n'); stdin.flush()
    stdout.channel.recv_exit_status()

print("  Sunucu durduruluyor...")
sudo("sudo systemctl stop minecraft")
time.sleep(4)
print(f"  Servis: {run('systemctl is-active minecraft')}")

# ══════════════════════════════════════════════════════
sep("AŞAMA 4: Sunucudan eski modlar kaldırılıyor...")
# ══════════════════════════════════════════════════════

sftp = ssh.open_sftp()
remote_files = set(sftp.listdir(REMOTE_MODS))

all_remove_server = REMOVE_LOCAL + REMOVE_SERVER_EXTRA
for fname in all_remove_server:
    if fname in remote_files:
        sftp.remove(f"{REMOTE_MODS}/{fname}")
        print(f"  ✅ Silindi (sunucu): {fname}")
    else:
        print(f"  ℹ️  Zaten yok (sunucu): {fname}")

# ══════════════════════════════════════════════════════
sep("AŞAMA 5: Moonrise & MoonriseCompats sunucuya yükleniyor...")
# ══════════════════════════════════════════════════════

for label, info in downloads.items():
    mb = os.path.getsize(info["path"]) / 1024 / 1024
    print(f"  ⬆️  Upload: {info['fname']} ({mb:.1f} MB)...", end=" ", flush=True)
    sftp.put(info["path"], f"{REMOTE_MODS}/{info['fname']}")
    print("✅")

sftp.close()

# ══════════════════════════════════════════════════════
sep("AŞAMA 6: Sunucu başlatılıyor...")
# ══════════════════════════════════════════════════════

sudo("sudo systemctl start minecraft")
print("  Başlatıldı! 30 saniye bekleniyor...")
time.sleep(30)

boot_ok = False
for tick in range(1, 9):
    time.sleep(10)
    log = run(f"tail -n 12 {SERVER_DIR}/logs/latest.log 2>/dev/null")
    status = run("systemctl is-active minecraft")
    print(f"\n  --- {tick}/8 | Servis: {status} ---")
    print(log)
    if "done" in log.lower() and "for help" in log.lower():
        print("\n  🎉 SUNUCU AÇILDI!")
        boot_ok = True
        break
    if "exception" in log.lower() and "moonrise" in log.lower():
        print("\n  ⚠️  Moonrise HATA! Log'a bak.")
        break

ssh.close()

# ══════════════════════════════════════════════════════
sep("ÖZET")
# ══════════════════════════════════════════════════════
print(f"""
❌ Kaldırıldı:
   • c2me-neoforge-mc1.21.1-0.4.0-alpha.0.116.jar  (yerel + sunucu)
   • ScalableLux-...jar                              (yerel + sunucu)
   • servercore-neoforge-1.5.19+1.21.1.jar          (sunucu)

✅ Eklendi:
   • {downloads['Moonrise']['fname']}    (yerel + sunucu)
   • {downloads['MoonriseCompats']['fname']}  (yerel + sunucu)

⚡ Kalıyor: lithium, ferritecore, modernfix, noisium

Sunucu boot: {'✅ BAŞARILI' if boot_ok else '⚠️ Manuel kontrol et'}
""")
