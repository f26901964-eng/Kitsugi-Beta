# -*- coding: utf-8 -*-
"""
sync_sklauncher_to_server.py
============================
Syncs the active SKLauncher instance mods directly to the server.
"""

import os
import sys
import io
import time
import paramiko

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

# Config
LOCAL_MODS     = r"C:\Users\Administrator\AppData\Roaming\.sklauncher\instances\neoforge-1-21-1\mods"
SSH_HOST       = "192.168.1.100"
SSH_USER       = "blackdamage"
SSH_PASS       = "Gameras6060"
SERVER_DIR     = "/home/blackdamage/minecraft-neoforge-1211"
REMOTE_MODS    = f"{SERVER_DIR}/mods"

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

SERVER_ONLY = {
    "lithium", "ferritecore", "modernfix", "alternate_current",
    "structure_layout_optimizer", "spark", "chunky", "livepanel",
    "structureessentials"
}

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
    return exit_status, '\n'.join(lines).strip()

def exec_ssh(ssh, cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd)
    stdout.channel.recv_exit_status()
    return stdout.read().decode('utf-8', errors='replace').strip()

def main():
    print("=== SYNCHRONIZATION ANALYSIS ===")
    if not os.path.exists(LOCAL_MODS):
        print(f"ERROR: Local SKLauncher mods folder not found at: {LOCAL_MODS}")
        return

    local_files = {f: os.path.getsize(os.path.join(LOCAL_MODS, f)) for f in os.listdir(LOCAL_MODS) if f.endswith(".jar")}
    print(f"Local mods found: {len(local_files)}")

    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    
    try:
        print(f"Connecting to SSH: {SSH_HOST}...")
        ssh.connect(SSH_HOST, username=SSH_USER, password=SSH_PASS, timeout=15)
        print("Connected successfully!")
        
        sftp = ssh.open_sftp()
        server_files = {}
        for f in sftp.listdir(REMOTE_MODS):
            if f.endswith(".jar"):
                try:
                    attr = sftp.stat(f"{REMOTE_MODS}/{f}")
                    server_files[f] = attr.st_size
                except:
                    pass
        print(f"Server mods found: {len(server_files)}")
        
        # Calculate changes
        def get_base_name(filename):
            name = filename.replace(".jar", "")
            parts = name.split("-")
            base_parts = []
            for p in parts:
                if any(c.isdigit() for c in p) or p.lower() in ["v", "mc", "neoforge", "fabric"]:
                    break
                base_parts.append(p)
            if not base_parts:
                return parts[0].lower()
            return "-".join(base_parts).lower()

        # Target for server: all local mods except client-only ones
        server_target = set(f for f in local_files if not is_client_only(f))
        
        to_delete = []
        for f in server_files:
            # If the server file matches a server-only mod, keep it
            if is_server_only(f):
                continue
                
            base = get_base_name(f)
            # Find any local mod matching this base name
            local_matches = [lf for lf in local_files if get_base_name(lf) == base]
            
            if local_matches:
                # If the exact same file is present locally (even if marked client-only),
                # keep it on the server to prevent deleting dependencies!
                if f in local_files:
                    continue
                # If it's a version mismatch (different version of local mod), delete it
                else:
                    to_delete.append(f)
            else:
                # If the mod is not present locally at all, delete it
                to_delete.append(f)
                
        to_upload = []
        for f in server_target:
            if f not in server_files or server_files[f] != local_files[f]:
                to_upload.append(f)
                
        print(f"\nPlan:")
        print(f"  - Upload: {len(to_upload)} mods")
        for f in to_upload:
            print(f"    + {f}")
        print(f"  - Delete: {len(to_delete)} mods")
        for f in to_delete:
            print(f"    - {f}")
            
        if not to_upload and not to_delete:
            print("\nEverything is already in sync!")
            sftp.close()
            ssh.close()
            return
            
        # Execute sync
        print("\nStopping Minecraft service...")
        status, out = exec_sudo(ssh, "sudo systemctl stop minecraft")
        print(f"Service stopped (exit code: {status})")
        time.sleep(3)
        
        # Delete old mods
        if to_delete:
            print("\nDeleting outdated/extra mods from server...")
            for f in to_delete:
                try:
                    sftp.remove(f"{REMOTE_MODS}/{f}")
                    print(f"  Deleted: {f}")
                except Exception as e:
                    print(f"  Failed to delete {f}: {e}")
                    
        # Upload new mods
        if to_upload:
            print("\nUploading mods to server...")
            for i, f in enumerate(to_upload, 1):
                src = os.path.join(LOCAL_MODS, f)
                dst = f"{REMOTE_MODS}/{f}"
                size_mb = os.path.getsize(src) / (1024 * 1024)
                print(f"  [{i}/{len(to_upload)}] Uploading {f} ({size_mb:.2f} MB)...")
                try:
                    sftp.put(src, dst)
                    print(f"    Done")
                except Exception as e:
                    print(f"    Failed: {e}")
                    
        sftp.close()
        
        print("\nStarting Minecraft service...")
        status, out = exec_sudo(ssh, "sudo systemctl start minecraft")
        print(f"Service started (exit code: {status})")
        
        print("\nWaiting for server boot logs (60s)...")
        time.sleep(10)
        for tick in range(1, 6):
            time.sleep(10)
            log = exec_ssh(ssh, f"tail -n 20 {SERVER_DIR}/logs/latest.log 2>/dev/null")
            status_out = exec_ssh(ssh, "systemctl is-active minecraft 2>/dev/null")
            print(f"\n--- Tick {tick}/5 | Status: {status_out} ---")
            print(log)
            if "done" in log.lower() and "for help" in log.lower():
                print("\nServer successfully started!")
                break
                
        ssh.close()
        print("\nSync completed successfully!")
        
    except Exception as e:
        print(f"SSH/SFTP Error: {e}")

if __name__ == '__main__':
    main()
