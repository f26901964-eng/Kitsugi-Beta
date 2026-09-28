import os
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

NEW_MODS_DIR   = r"C:\Users\Administrator\Downloads\Kitsugi_Mods_1.21.1_NeoForge"
CLIENT_MODS    = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"

print(f"--- Checking {NEW_MODS_DIR} ---")
if os.path.exists(NEW_MODS_DIR):
    dh_files = [f for f in os.listdir(NEW_MODS_DIR) if "distant" in f.lower() or "horizon" in f.lower()]
    print("Files found:", dh_files)
else:
    print("Folder does not exist!")

print(f"\n--- Checking {CLIENT_MODS} ---")
if os.path.exists(CLIENT_MODS):
    dh_files_client = [f for f in os.listdir(CLIENT_MODS) if "distant" in f.lower() or "horizon" in f.lower()]
    print("Files found:", dh_files_client)
else:
    print("Folder does not exist!")
