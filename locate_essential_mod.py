import os

mods_dir = r"C:\Users\Administrator\AppData\Roaming\.sklauncher\instances\neoforge-1-21-1\mods"
for root, dirs, files in os.walk(mods_dir):
    for file in files:
        if "essential" in file.lower():
            print(os.path.join(root, file))
