import paramiko
import os
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

NEW_MODS_DIR = r"C:\Users\Administrator\Downloads\Kitsugi_Mods_1.21.1_NeoForge"
SERVER_MODS = "/home/blackdamage/minecraft-neoforge-1211/mods"

CLIENT_ONLY = {
    "badoptimizations", "immediatelyfast", "moreculling", "bobby", "reeses",
    "sodium-neoforge", "sodium-extra", "fpsreducer", "entityculling",
    "lambdynamiclights", "continuity", "iris-neoforge", "iris_shader_folder",
    "inventoryhud", "inventoryprofilesnext", "libipn", "clientsort",
    "controlling", "mousetweaks", "playeranimationlib", "player-animation-lib",
    "playeranimatorapi", "chat_heads", "notenoughanimations", "skinlayers3d",
    "eating-animation", "eatinganimation", "overflowingbars", "highlighter",
    "entity_model_features", "entity_texture_features", "particleeffects",
    "particle_effects", "particle", "midnightlib", "colorwheel", "sound-physics",
    "ambientso", "audio improvements", "melody", "fancymenu", "drippyloadingscreen",
    "betterworldloading", "konkrete", "essential_", "txnilib", "simpledrpc",
    "simplerpc", "simplediscordrichpresence", "rebind_narrator", "longercha",
    "distraction_free_recipes", "nvidium", "offlineskins", "customskinloader",
    "reeses-sodium", "appleskin", "justenoughprofessions", "betteradvancements",
    "advancementplaques", "yungsmenutweaks", "bettermodsbutton", "resourcepackoverrides",
    "stylisheffects", "mindfulda", "configureddefaults", "deleteworlds",
    "leavesbe", "tesseraui", "logbegone", "enchdesc", "modpack-update-checker"
}

def is_client_only(filename: str) -> bool:
    fl = filename.lower()
    for prefix in CLIENT_ONLY:
        if fl.startswith(prefix.lower()):
            return True
    return False

print("--- LOCAL NEW_MODS_DIR STATS ---")
local_files = {f: os.path.getsize(os.path.join(NEW_MODS_DIR, f)) for f in os.listdir(NEW_MODS_DIR) if f.endswith(".jar")}
print(f"Local files count: {len(local_files)}")

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    ssh.connect('192.168.1.100', username='blackdamage', password='Gameras6060', timeout=10)
    sftp = ssh.open_sftp()
    
    server_files = {}
    for f in sftp.listdir(SERVER_MODS):
        if f.endswith(".jar"):
            try:
                attr = sftp.stat(f"{SERVER_MODS}/{f}")
                server_files[f] = attr.st_size
            except:
                pass
    sftp.close()
    ssh.close()
    
    print(f"Server files count: {len(server_files)}")
    
    missing_from_server = []
    for f, sz in local_files.items():
        if not is_client_only(f):
            if f not in server_files:
                missing_from_server.append((f, "Missing"))
            elif server_files[f] != sz:
                missing_from_server.append((f, f"Size mismatch (Local: {sz}, Server: {server_files[f]})"))
                
    print(f"\nMissing or mismatched server-target mods ({len(missing_from_server)}):")
    for f, reason in missing_from_server:
        print(f"  - {f} ({reason})")
        
except Exception as e:
    print(f"Error: {e}")
