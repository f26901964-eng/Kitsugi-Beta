# -*- coding: utf-8 -*-
import os
import urllib.request

local_mods_dir = r"C:\Users\Administrator\AppData\Roaming\.sklauncher\instances\neoforge-1-21-1\mods"
url = "https://cdn.modrinth.com/data/Ua7DFN59/versions/ZB22DE9q/YungsApi-1.21.1-NeoForge-5.1.6.jar"
target_file_516 = os.path.join(local_mods_dir, "YungsApi-1.21.1-NeoForge-5.1.6.jar")
target_file_517 = os.path.join(local_mods_dir, "YungsApi-1.21.1-NeoForge-5.1.7.jar")

print("Downloading YungsApi 5.1.6...")
urllib.request.urlretrieve(url, target_file_516)
print("Downloaded successfully.")

if os.path.exists(target_file_517):
    os.remove(target_file_517)
    print("Deleted YungsApi 5.1.7.")
else:
    print("YungsApi 5.1.7 not found.")

print("Local mods folder updated!")
