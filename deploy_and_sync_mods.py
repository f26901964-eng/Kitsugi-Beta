# -*- coding: utf-8 -*-
"""
deploy_and_sync_mods.py
========================
Kitsugi NeoForge 1.21.1 Modpack Güncelleme Betiği
- Yeni modları yerel client mods klasörüne kopyalar
- Sunucudaki eski modları temizleyip yeni modları upload eder
- Sunucuyu yeniden başlatır ve boot log'unu takip eder
"""

import os
import sys
import io
import time
import shutil
import paramiko

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

# ========================= YAPILANDIRMA =========================
NEW_MODS_DIR   = r"C:\Users\Administrator\Downloads\Kitsugi_Mods_1.21.1_NeoForge"
CLIENT_MODS    = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"

SSH_HOST       = "192.168.1.100"
SSH_USER       = "blackdamage"
SSH_PASS       = "Gameras6060"
SERVER_DIR     = "/home/blackdamage/minecraft-neoforge-1211"
REMOTE_MODS    = f"{SERVER_DIR}/mods"

# Sadece istemcide bulunması gereken modlar (sunucuya gönderilmez)
# !! Buraya eklenen modlar: client mods klasöründe kalır, sunucuya UPLOAD EDİLMEZ !!
CLIENT_ONLY = {
    # === PERFORMANS (client-side only) ===
    "badoptimizations",
    "immediatelyfast",
    "moreculling",
    "bobby",
    "reeses",
    "sodium-neoforge",
    "sodium-extra",
    "fpsreducer",
    "entityculling",
    "lambdynamiclights",
    "continuity",
    # === SHADER / RENDER ===
    "iris-neoforge",
    "iris_shader_folder",
    # === ENVANTER / UI ===
    "inventoryhud",
    "inventoryprofilesnext",
    "libipn",
    "clientsort",
    "controlling",
    "mousetweaks",
    "playeranimationlib",
    "player-animation-lib",
    "playeranimatorapi",
    # === GÖRSEL / HUD ===
    "chat_heads",
    "notenoughanimations",
    "skinlayers3d",
    "eating-animation",
    "eatinganimation",
    "overflowingbars",
    "highlighter",
    "entity_model_features",
    "entity_texture_features",
    "particleeffects",
    "particle_effects",
    "particle",
    "midnightlib",
    "colorwheel",
    # === SES ===
    "sound-physics",
    "ambientso",
    "audio improvements",
    "melody",
    # === MÜZİK / BAŞLANGIÇ EKRANI ===
    "fancymenu",
    "drippyloadingscreen",
    "betterworldloading",
    "konkrete",
    # === YARDIMCI / KÜÇÜK CLIENT MODLAR ===
    "essential_",
    "txnilib",
    "simpledrpc",
    "simplerpc",
    "simplediscordrichpresence",
    "rebind_narrator",
    "longercha",
    "distraction_free_recipes",
    "nvidium",
    "offlineskins",
    "customskinloader",
    "reeses-sodium",
    "appleskin",
    # === BİLGİ / ARAYÜZ ===
    "justenoughprofessions",
    "betteradvancements",
    "advancementplaques",
    "yungsmenutweaks",
    "bettermodsbutton",
    "resourcepackoverrides",
    "stylisheffects",
    "mindfulda",
    "configureddefaults",
    "deleteworlds",
    "leavesbe",
    "tesseraui",
    "logbegone",
    "enchdesc",
    "modpack-update-checker",
}

# Sadece sunucuda bulunması gereken modlar (istemciye gönderilmez)
# !! Buraya eklenen modlar: sunucuda kalır, client'a KOPYALANMAZ !!
SERVER_ONLY = {
    # === SUNUCU PERFORMANS ===
    "lithium",
    "ferritecore",
    "modernfix",
    "alternate_current",
    "structure_layout_optimizer",
    # === SUNUCU ARAÇLARI ===
    "spark",
    "chunky",
    "livepanel",
    "structureessentials",
}

# ================================================================

def is_client_only(filename: str) -> bool:
    fl = filename.lower()
    for prefix in CLIENT_ONLY:
        if fl.startswith(prefix.lower()):
            return True
    return False

def is_server_only(filename: str) -> bool:
    fl = filename.lower()
    for prefix in SERVER_ONLY:
        if fl.startswith(prefix.lower()):
            return True
    return False

def exec_sudo(ssh, cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd, get_pty=True)
    stdin.write(SSH_PASS + '\n')
    stdin.flush()
    exit_status = stdout.channel.recv_exit_status()
    out = stdout.read().decode('utf-8', errors='replace')
    lines = [l for l in out.splitlines() if '[sudo] password for' not in l]
    result = '\n'.join(lines).strip()
    return exit_status, result

def exec_ssh(ssh, cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    stdout.channel.recv_exit_status()
    return stdout.read().decode('utf-8', errors='replace').strip()

def sep(title=""):
    if title:
        print(f"\n{'='*60}")
        print(f"  {title}")
        print('='*60)
    else:
        print('-'*60)

# ============================================================
# 1. YENİ KLASÖRDEN MODLARI OKU
# ============================================================
sep("AŞAMA 1: Yeni modlar okunuyor...")
if not os.path.isdir(NEW_MODS_DIR):
    print(f"HATA: Yeni modlar klasoru bulunamadi: {NEW_MODS_DIR}")
    sys.exit(1)

new_mods_files = [
    f for f in os.listdir(NEW_MODS_DIR)
    if f.endswith('.jar')
]
print(f"OK: Yeni klasorde {len(new_mods_files)} adet JAR dosyasi bulundu.")

# ============================================================
# 2. ISTEMCI MODS KLASORUNU GUNCELLE
# ============================================================
sep("AŞAMA 2: Istemci mods klasoru guncelleniyor...")

if not os.path.isdir(CLIENT_MODS):
    print(f"HATA: Istemci mods klasoru bulunamadi: {CLIENT_MODS}")
else:
    new_mods_set = set(new_mods_files)
    existing_client = [f for f in os.listdir(CLIENT_MODS) if f.endswith('.jar')]

    # Yeni klasorde olmayan modlari sil
    deleted = 0
    for old_file in existing_client:
        if old_file not in new_mods_set:
            old_path = os.path.join(CLIENT_MODS, old_file)
            print(f"  SILIYOR: {old_file}")
            try:
                os.remove(old_path)
                deleted += 1
            except Exception as e:
                print(f"     UYARI silinemedi: {e}")

    # Yeni modlari kopyala (degisen veya eksik olanlari)
    copied = 0
    skipped = 0
    for new_file in sorted(new_mods_files):
        src = os.path.join(NEW_MODS_DIR, new_file)
        dst = os.path.join(CLIENT_MODS, new_file)
        if os.path.exists(dst) and os.path.getsize(dst) == os.path.getsize(src):
            skipped += 1
            continue
        print(f"  KOPYALANIYOR: {new_file}")
        try:
            shutil.copy2(src, dst)
            copied += 1
        except Exception as e:
            print(f"     UYARI kopyalanamadi: {e}")

    print(f"\nOK: Istemci guncellendi: {deleted} silindi, {copied} kopyalandi, {skipped} atlandı.")

# ============================================================
# 3. SSH BAGLANTISI KUR
# ============================================================
sep("AŞAMA 3: Sunucuya SSH baglantisi kuruluyor...")

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())

connected = False
for attempt in range(5):
    try:
        print(f"  Baglaniliyor: {SSH_HOST} (Deneme {attempt+1}/5)...")
        ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=10)
        connected = True
        break
    except Exception as e:
        print(f"  HATA baglanti: {e}")
        time.sleep(2)

if not connected:
    print("HATA: SSH baglantisi kurulamadi. Cikiliyor.")
    sys.exit(1)

print("OK: SSH baglantisi basarili!")

# ============================================================
# 4. SUNUCU SERVISINI DURDUR
# ============================================================
sep("AŞAMA 4: Sunucu servisi durduruluyor...")
status, out = exec_sudo(ssh, "sudo systemctl stop minecraft")
print(f"  Servis durduruldu. (exit={status})")
time.sleep(3)

# ============================================================
# 5. SUNUCU MODS KLASORUNU ANALIZ ET VE GUNCELLE
# ============================================================
sep("AŞAMA 5: Sunucu modlari analiz ediliyor ve guncelleniyor...")

sftp = ssh.open_sftp()

try:
    remote_files = sftp.listdir(REMOTE_MODS)
except Exception as e:
    print(f"HATA: Uzak mods klasoru okunamadi: {e}")
    sftp.close()
    ssh.close()
    sys.exit(1)

remote_jars = set(f for f in remote_files if f.endswith('.jar') and not f.startswith('.'))

# Sunucuya gonderilecek modlar = yeni modlar listesinden client_only olanlari cikar
server_target = set(f for f in new_mods_files if not is_client_only(f))

print(f"  Uzaktaki mevcut: {len(remote_jars)} JAR")
print(f"  Hedef (yeni): {len(server_target)} JAR")

to_delete_remote = remote_jars - server_target
to_upload = server_target - remote_jars
to_check_update = server_target & remote_jars

print(f"\n  Ozet:")
print(f"     Silinecek (artik yok): {len(to_delete_remote)}")
print(f"     Upload edilecek (yeni): {len(to_upload)}")
print(f"     Boyut kontrolu: {len(to_check_update)}")

# Boyutu farkli olanlari da guncelle
to_update_set = set()
for fname in sorted(to_check_update):
    local_size = os.path.getsize(os.path.join(NEW_MODS_DIR, fname))
    try:
        remote_attr = sftp.stat(f"{REMOTE_MODS}/{fname}")
        if remote_attr.st_size != local_size:
            to_update_set.add(fname)
    except:
        to_upload.add(fname)

print(f"     Guncellenecek (boyut farki): {len(to_update_set)}")

# --- Eski modlari sil ---
if to_delete_remote:
    print("\n  Siliniyor (uzakta fazladan):")
    for fname in sorted(to_delete_remote):
        print(f"    SILIYOR: {fname}")
        try:
            sftp.remove(f"{REMOTE_MODS}/{fname}")
        except Exception as e:
            print(f"       UYARI silinemedi: {e}")

# --- .bak dosyalarini temizle ---
bak_files = [f for f in remote_files if '.bak' in f]
if bak_files:
    print(f"\n  {len(bak_files)} adet .bak dosyasi temizleniyor...")
    for fname in bak_files:
        try:
            sftp.remove(f"{REMOTE_MODS}/{fname}")
            print(f"    BAK SILINDI: {fname}")
        except Exception as e:
            print(f"    UYARI {fname}: {e}")

# --- Yeni ve guncellenen modlari upload et ---
all_to_upload = to_upload | to_update_set
if all_to_upload:
    print(f"\n  {len(all_to_upload)} adet mod upload ediliyor...")
    uploaded = 0
    failed = []
    total = len(all_to_upload)
    for idx, fname in enumerate(sorted(all_to_upload), 1):
        src = os.path.join(NEW_MODS_DIR, fname)
        dst = f"{REMOTE_MODS}/{fname}"
        size_mb = os.path.getsize(src) / (1024*1024)
        print(f"    [{idx}/{total}] UPLOAD: {fname} ({size_mb:.1f} MB)...")
        try:
            sftp.put(src, dst)
            uploaded += 1
        except Exception as e:
            print(f"       HATA upload: {e}")
            failed.append(fname)

    print(f"\n  OK: Upload tamamlandi: {uploaded} basarili, {len(failed)} hatali.")
    if failed:
        print("  Hatali dosyalar:")
        for f in failed:
            print(f"    - {f}")
else:
    print("\n  OK: Sunucuda tum modlar zaten guncel, upload gerekmiyor.")

sftp.close()

# ============================================================
# 6. SUNUCU SERVISINI BASLAT
# ============================================================
sep("AŞAMA 6: Sunucu servisi baslatiliyor...")
status, out = exec_sudo(ssh, "sudo systemctl start minecraft")
print(f"  Servis baslatildi. (exit={status})")
if out:
    print(f"  Cikti: {out}")

print("\n  Sunucunun boot etmesi icin bekleniyor (20 saniye)...")
time.sleep(20)

# ============================================================
# 7. SUNUCU LOG TAKIBI
# ============================================================
sep("AŞAMA 7: Sunucu boot logu takip ediliyor (60 saniye)...")

boot_ok = False
crash_detected = False

for tick in range(1, 7):
    time.sleep(10)
    log = exec_ssh(ssh, f"tail -n 20 {SERVER_DIR}/logs/latest.log 2>/dev/null")
    status_out = exec_ssh(ssh, "systemctl is-active minecraft 2>/dev/null")

    print(f"\n  --- Tick {tick}/6 | Servis durumu: {status_out} ---")
    print(log)
    print("  " + "-"*56)

    log_lower = log.lower()
    if "done" in log_lower and "for help" in log_lower:
        print("\n  SUNUCU BASARIYLA ACILDI! 'Done' mesaji goruldu.")
        boot_ok = True
        break
    if "exception" in log_lower or "crashed" in log_lower:
        crash_detected = True

if not boot_ok:
    final_status = exec_ssh(ssh, "systemctl is-active minecraft 2>/dev/null")
    if final_status == "active":
        print("\n  UYARI: Servis aktif ama 'Done' mesaji henuz gorulemedi.")
        print("     Sunucu muhtemelen hala yukleniyordur. Manuel kontrol ediniz.")
    else:
        print(f"\n  HATA: Sunucu servisi durumu: {final_status}")
        print("     Son loglar:")
        last_log = exec_ssh(ssh, f"tail -n 40 {SERVER_DIR}/logs/latest.log 2>/dev/null")
        print(last_log)

# ============================================================
# 8. OZET
# ============================================================
sep("OZET")
ts_ip = exec_ssh(ssh, "tailscale ip -4 2>/dev/null")
if not ts_ip.startswith("100."):
    ts_ip = SSH_HOST

print(f"  Sunucu IP      : {ts_ip}:25565")
print(f"  Boot durumu    : {'BASARILI' if boot_ok else 'Kontrol edilmeli'}")
print(f"  Crash tespit   : {'EVET - Loglara bak!' if crash_detected and not boot_ok else 'YOK'}")
print(f"  Istemci mods   : {CLIENT_MODS}")
print(f"  Sunucu mods    : {SSH_USER}@{SSH_HOST}:{REMOTE_MODS}")
sep()

ssh.close()
print("\nTum islemler tamamlandi!")
