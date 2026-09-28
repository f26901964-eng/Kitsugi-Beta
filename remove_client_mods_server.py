"""
Remove the remaining client-only mods that cause crashes on dedicated server.
"""
import paramiko, sys, io, time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

MODS_DIR = "/home/blackdamage/minecraft-neoforge-1211/mods"
PASSWORD = "Gameras6060"

# Known client-only mods that must NOT be on the dedicated server.
# These reference client-side classes (Screen, GuiGraphics, etc.)
CLIENT_ONLY_MODS = [
    'distraction_free_recipes-neoforge-1.2.1-1.21.1.jar',
    'distraction-free-recipes',  # prefix match
    'journeymap-neoforge',       # prefix - JourneyMap is client only
    'journeymap-neoforge-1.21.1-6.0.3.jar',
    'drippyloadingscreen',
    'fancymenu',
    'konkrete',
    'melody',
    'luna_minecraft',
    'logbegone',
    'LongerChatHistory',
    'modpack-update-checker',
    'notenoughanimations',
    'rebind_narrator',
    'Searchables',
    'StylishEffects',
    'YungsMenuTweaks-1.21.1-NeoForge-2.1.2.jar',
    'MindfulDarkness',
    'DeleteWorldsToTrash',
    'BetterModsButton',
    'BetterAdvancements',
    'AdvancementPlaques',
    'OverflowingBars',
    'ParticleEffects',
    'ResourcePackOverrides',
    'chat_heads',
    'continuity',
    'enchdesc',
    'entity_model_features',
    'entityculling',
    'Iceberg',
    'prickle',
    'txnilib',
    'Controlling',
    'ConfiguredDefaults',
    'MouseTweaks',
    'appleskin',
    'better-compatability-checker',
    'midnightlib',
    'sound-physics-remastered',
    'tc-veinglow',
    'lavatrident',
    'skinlayers3d',
    'entity_texture_features',
    'colorwheel',
    'SimpleRPC',
    'eatinganimation',
    'inventoryhud',
]

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.1.100', username='blackdamage', password=PASSWORD)
sftp = ssh.open_sftp()

remote_files = sftp.listdir(MODS_DIR)
removed = []

for rf in remote_files:
    for pat in CLIENT_ONLY_MODS:
        rf_lower = rf.lower()
        pat_lower = pat.lower()
        if rf_lower == pat_lower or rf_lower.startswith(pat_lower.replace('.jar', '').lower()):
            print(f"  REMOVE (client-only): {rf}")
            sftp.remove(f"{MODS_DIR}/{rf}")
            removed.append(rf)
            break

print(f"\nRemoved {len(removed)} client-only mods.")

sftp.close()

print("\nRestarting server...")
stdin, stdout, stderr = ssh.exec_command("sudo -S systemctl restart minecraft")
stdin.write(PASSWORD + "\n")
stdin.flush()
stdout.read()

print("Waiting 90s for boot...")
time.sleep(90)

stdin, stdout, stderr = ssh.exec_command(
    "tail -n 60 /home/blackdamage/minecraft-neoforge-1211/logs/latest.log"
)
log = stdout.read().decode('utf-8', errors='replace')
print("\n=== SERVER LOG ===")
print(log)

fatal = [l for l in log.splitlines() if 'FATAL' in l or 'Failed to start' in l]
if fatal:
    print("\n⚠️  STILL CRASHING - remaining errors:")
    for l in fatal:
        print(f"  {l}")
elif 'Done' in log or 'For help, type' in log:
    print("\n✅ Server fully loaded and running!")
else:
    print("\n⏳ Still loading (no fatal errors visible yet).")

ssh.close()
