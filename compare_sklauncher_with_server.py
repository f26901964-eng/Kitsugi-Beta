import paramiko
import os
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

LOCAL_MODS = r"C:\Users\Administrator\AppData\Roaming\.sklauncher\instances\neoforge-1-21-1\mods"
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

print("--- COMPARING ACTIVE SKLAUNCHER MODS WITH SERVER ---")
local_files = {f: os.path.getsize(os.path.join(LOCAL_MODS, f)) for f in os.listdir(LOCAL_MODS) if f.endswith(".jar")}
print(f"Local (SKLauncher) files count: {len(local_files)}")

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
    
    # 1. Missing on server but should be there (not client only)
    missing_on_server = []
    for f, sz in local_files.items():
        if not is_client_only(f) and f not in server_files:
            missing_on_server.append(f)
            
    # 2. Mismatched size
    mismatched = []
    for f, sz in local_files.items():
        if f in server_files and server_files[f] != sz:
            mismatched.append((f, sz, server_files[f]))
            
    # 3. Extra files on server (not in local and not server only)
    # Note: server only list:
    SERVER_ONLY = {"lithium", "ferritecore", "modernfix", "alternate_current", "structure_layout_optimizer", "spark", "chunky", "livepanel", "structureessentials"}
    def is_server_only(filename: str) -> bool:
        fl = filename.lower()
        for prefix in SERVER_ONLY:
            if fl.startswith(prefix.lower()):
                return True
        return False

    extra_on_server = []
    for f in server_files:
        if f not in local_files and not is_server_only(f):
            extra_on_server.append(f)
            
    print(f"\nMissing on server ({len(missing_on_server)}):")
    for f in missing_on_server:
        print(f"  - {f}")
        
    print(f"\nMismatched size ({len(mismatched)}):")
    for f, loc_sz, ser_sz in mismatched:
        print(f"  - {f} (Local: {loc_sz}, Server: {ser_sz})")
        
    print(f"\nExtra on server ({len(extra_on_server)}):")
    for f in extra_on_server:
        print(f"  - {f}")
        
except Exception as e:
    print(f"Error: {e}")
