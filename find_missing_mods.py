import os
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

server_dir = r'C:\Users\Administrator\Downloads\MC-1211-ModPaketi-DOSYALAR (6)\mods-server'
client_dir = r'C:\Users\Administrator\Downloads\MC-1211-ModPaketi-DOSYALAR (6)\mods-client'

all_files = sorted(os.listdir(server_dir) + os.listdir(client_dir))

missing_slugs = [
    'architectury-api',
    'awesome-dungeon-edition-ocean',
    'elevatormod',
    'every-compat',
    'gravestone-mod',
    'iron-chests',
    'repurposed-structures-forge',
    'simple-storage-network',
    'towns-and-towers',
    'simple-discord-rich-presence',
    'better-compatibility-checker',
    'enchantment-descriptions'
]

print('=== MATCHING FILENAMES FOR MISSING SLUGS ===')
for slug in missing_slugs:
    keywords = [kw for kw in slug.split('-') if len(kw) > 3]
    matches = []
    for f in all_files:
        f_lower = f.lower()
        if all(kw in f_lower for kw in keywords):
            matches.append(f)
    if not matches:
        # Try looser matching (any keyword)
        matches = [f for f in all_files if any(kw in f.lower() for kw in keywords)]
    print(f'Slug: {slug} -> Matches: {matches}')
