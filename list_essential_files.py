import os

essential_dir = r"C:\Users\Administrator\AppData\Roaming\.sklauncher\instances\neoforge-1-21-1\essential"
if os.path.exists(essential_dir):
    print("Files in root of essential folder:")
    for item in os.listdir(essential_dir):
        path = os.path.join(essential_dir, item)
        if os.path.isfile(path):
            print(f"[FILE] {item}")
        else:
            print(f"[DIR] {item}")
else:
    print("essential folder does not exist")
