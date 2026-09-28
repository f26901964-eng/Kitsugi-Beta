import os

path = r"C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\mods"
if os.path.exists(path):
    files = os.listdir(path)
    print("Files in local mods directory:")
    for f in sorted(files):
        print(f" - {f}")
else:
    print("Directory does not exist!")
