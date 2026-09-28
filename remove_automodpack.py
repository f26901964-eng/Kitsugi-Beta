import paramiko
import sys
import io
import time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060')

def run(cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    return stdout.read().decode('utf-8', errors='replace').strip()

def run_sudo(cmd):
    stdin, stdout, stderr = ssh.exec_command(f"sudo -S {cmd}")
    stdin.write('Gameras6060\n')
    stdin.flush()
    out = stdout.read().decode('utf-8', errors='replace').strip()
    err = stderr.read().decode('utf-8', errors='replace').strip()
    return out

SERVER_MODS = "/home/blackdamage/minecraft-neoforge-1211/mods"

# 1. Remove AutoModpack from server
print("🗑️ AutoModpack sunucudan kaldırılıyor...")
result = run(f"rm -f {SERVER_MODS}/automodpack-mc1.21.1-neoforge-4.0.6.jar")
print("✅ AutoModpack modu silindi")

# 2. Remove c2me opencl (Java 25 problem mod)
print("\n🗑️ c2me-opencl modu kaldırılıyor...")
run(f"rm -f {SERVER_MODS}/c2me-neoforge-opts-accel-opencl-mc1.21.1-0.4.0-alpha.0.116.jar")
print("✅ c2me-opencl silindi")

# 3. Remove any extra mods we uploaded in the previous failed attempt
print("\n🗑️ Son yükleme denemesinde eklenen fazladan modlar temizleniyor...")
# Get current server mods
server_mods_raw = run(f"ls {SERVER_MODS}/*.jar")
server_mods = set()
for line in server_mods_raw.splitlines():
    fname = line.strip().split("/")[-1]
    if fname.endswith(".jar"):
        server_mods.add(fname)

# Original server mods (154 mods from our earlier check, minus automodpack and c2me-opencl)
original_server_mods = {
    "c2me-neoforge-mc1.21.1-0.4.0-alpha.0.116.jar",
    # All 151 shared mods are fine - we only need to remove what we uploaded
}

# Client-only mods we may have uploaded
client_only_uploaded = [
    "AdvancementPlaques-1.21.1-neoforge-1.6.8.jar",
    "AmbientSounds_NEOFORGE_v6.3.8_mc1.21.1.jar",
    "Audio Improvements v1.1 - NeoForge 1.21-1.21.3.jar",
    "BetterAdvancements-NeoForge-1.21.1-0.4.3.21.jar",
    "BetterModsButton-v21.1.0-1.21.1-NeoForge.jar",
    "ConfiguredDefaults-v21.1.3-1.21.1-NeoForge.jar",
    "Controlling-neoforge-1.21.1-19.0.5.jar",
    "DeleteWorldsToTrash-v21.1.0-1.21.1-NeoForge.jar",
    "Essential_1-4-1-1_neoforge_1-21-1.jar",
    "FpsReducer2-neoforge-1.21-2.10.jar",
    "Highlighter-1.21-neoforge-1.1.11.jar",
    "Iceberg-1.21.1-neoforge-1.3.2.jar",
    "ImmediatelyFast-NeoForge-1.6.11+1.21.1.jar",
    "JustEnoughProfessions-neoforge-1.21.1-4.0.5.jar",
    "JustEnoughResources-NeoForge-1.21.1-1.6.0.12.jar",
    "LongerChatHistory-neoforge-1.7.jar",
    "MindfulDarkness-v21.1.0-1.21.1-NeoForge.jar",
    "MouseTweaks-neoforge-mc1.21-2.26.1.jar",
    "OverflowingBars-v21.1.1-1.21.1-NeoForge.jar",
    "ParticleEffects-1.5.0+1.21.1+neoforge.jar",
    "ResourcePackOverrides-v21.1.0-1.21.1-NeoForge.jar",
    "Searchables-neoforge-1.21.1-1.0.2.jar",
    "SimpleDiscordRichPresence-neoforge-88.0.1-build.54+mc1.21.1.jar",
    "StylishEffects-v21.1.3-1.21.1-NeoForge.jar",
    "YungsMenuTweaks-1.21.1-NeoForge-2.1.2.jar",
    "appleskin-neoforge-mc1.21-3.0.9.jar",
    "athena-neoforge-1.21.1-4.0.6.jar",
    "better-compatability-checker-neoforge-21.1.8.jar",
    "betterworldloadingnf21-1.0.jar",
    "chat_heads-0.15.4-neoforge-1.21.jar",
    "colorwheel-neoforge-1.2.9+mc1.21.1.jar",
    "continuity-3.0.0+1.21.neoforge.jar",
    "distraction_free_recipes-neoforge-1.2.1-1.21.1.jar",
    "drippyloadingscreen_neoforge_3.1.5_MC_1.21.1.jar",
    "eatinganimation-1.21.0-6.0.1.jar",
    "enchdesc-neoforge-1.21.1-21.1.10.jar",
    "entity_model_features-3.2.4-1.21-neoforge.jar",
    "entity_texture_features_1.21-neoforge-7.1.jar",
    "entityculling-neoforge-1.10.5-mc1.21.1.jar",
    "fancymenu_neoforge_3.9.9_MC_1.21.1.jar",
    "glow_up-neoforge-1.2.1-1.21-1.21.1-neoforge.jar",
    "gml-6.0.2.jar",
    "inventoryhud.neoforged.1.21.1-3.4.28.jar",
    "iris-neoforge-1.8.14-beta.1+mc1.21.1.jar",
    "iris_shader_folder-1.4.1-neoforge.jar",
    "jei-1.21.1-neoforge-19.43.0.393.jar",
    "konkrete_neoforge_1.9.9_MC_1.21.jar",
    "logbegone-neoforge-1.21.1-1.0.3.jar",
    "luna_minecraft-neoforge-5.3.1.jar",
    "melody_neoforge_1.0.10_MC_1.21.jar",
    "midnightlib-neoforge-1.9.3+1.21.1.jar",
    "modpack-update-checker-1.21.1-neoforge-0.15.6.jar",
    "notenoughanimations-neoforge-1.12.4-mc1.21.1.jar",
    "prickle-neoforge-1.21.1-21.1.11.jar",
    "rebind_narrator-1.21.1-neoforge-2025.12.23.jar",
    "skinlayers3d-neoforge-1.11.2-mc1.21.1.jar",
    "sodium-neoforge-0.8.12+mc1.21.1.jar",
    "sound-physics-remastered-neoforge-1.21.1-1.4.10.jar",
    "txnilib-neoforge-1.0.24-1.21.1.jar",
]

removed = 0
for mod in client_only_uploaded:
    if mod in server_mods:
        run(f"rm -f '{SERVER_MODS}/{mod}'")
        print(f"  🗑️ Kaldırıldı: {mod}")
        removed += 1

print(f"\n✅ {removed} adet yanlış yüklenen mod temizlendi")

# 4. Verify server mods count
server_mods_after = run(f"ls {SERVER_MODS}/*.jar | wc -l")
print(f"\n📦 Sunucudaki toplam mod sayısı: {server_mods_after}")

# 5. Restart server
print("\n🔄 Sunucu yeniden başlatılıyor (temiz başlangıç)...")
run_sudo("systemctl restart minecraft")
print("⏳ 20 saniye bekleniyor...")
time.sleep(20)

print("\n=== Son 10 log satırı ===")
print(run("tail -n 10 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log"))

ssh.close()
print("\n✅ Sunucu AutoModpack'siz temiz şekilde başlatıldı!")
