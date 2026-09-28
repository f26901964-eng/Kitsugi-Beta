import os
import json
import zipfile
import re
import traceback
import sys
import io

# Ensure UTF-8 output on Windows terminal
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

# Paths
UPDATE_DIR = r"C:\Users\Administrator\Downloads\MC-1211-ModPaketi-DOSYALAR (6)"
MANIFEST_PATH = os.path.join(UPDATE_DIR, "mod_manifest.json")
MODS_SERVER = os.path.join(UPDATE_DIR, "mods-server")
MODS_CLIENT = os.path.join(UPDATE_DIR, "mods-client")

# System dependencies to ignore
SYSTEM_MOD_IDS = {
    'minecraft', 'neoforge', 'forge', 'fabric', 'java', 'fabricapi',
    'forgified-fabric-api', 'neoforged', 'c2me', 'connector', 'fabricloader', 'fabric-api'
}

def clean_mod_id(val):
    # Remove quotes, comments, trailing whitespace
    val = val.split('#')[0].strip()
    return val.strip('"').strip("'").strip().lower()

def parse_toml_dependencies(toml_content):
    dependencies = []
    current_dep = None
    lines = toml_content.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i].split('#')[0].strip()  # Strip comments first
        if not line:
            i += 1
            continue
        m = re.match(r'^\[\[dependencies\.([^\]]+)\]\]', line)
        if m:
            dep_id = m.group(1).strip().strip('"').strip("'").lower()
            current_dep = {"modId": dep_id, "mandatory": True}
            dependencies.append(current_dep)
        elif line.startswith('[') and not line.startswith('[[dependencies.'):
            current_dep = None
        elif current_dep and '=' in line:
            parts = line.split('=', 1)
            key = parts[0].strip()
            val = parts[1].strip()
            if key == "mandatory":
                current_dep["mandatory"] = (val.lower() == "true")
            elif key == "modId":
                current_dep["modId"] = clean_mod_id(val)
        i += 1
    return dependencies

def parse_jar_metadata(jar_path):
    metadata = {
        "mod_ids": [],
        "dependencies": [],
        "loader": "unknown",
        "filename": os.path.basename(jar_path)
    }
    try:
        with zipfile.ZipFile(jar_path, 'r') as z:
            names = z.namelist()
            if "META-INF/neoforge.mods.toml" in names:
                metadata["loader"] = "neoforge"
                content = z.read("META-INF/neoforge.mods.toml").decode("utf-8", errors="replace")
                own_ids = [clean_mod_id(mid) for mid in re.findall(r'modId\s*=\s*"([^"]+)"', content)]
                metadata["mod_ids"] = list(set(own_ids))
                metadata["dependencies"] = parse_toml_dependencies(content)
            elif "META-INF/mods.toml" in names:
                metadata["loader"] = "forge"
                content = z.read("META-INF/mods.toml").decode("utf-8", errors="replace")
                own_ids = [clean_mod_id(mid) for mid in re.findall(r'modId\s*=\s*"([^"]+)"', content)]
                metadata["mod_ids"] = list(set(own_ids))
                metadata["dependencies"] = parse_toml_dependencies(content)
            elif "fabric.mod.json" in names:
                metadata["loader"] = "fabric"
                try:
                    content = json.loads(z.read("fabric.mod.json").decode("utf-8", errors="replace"))
                    mod_id = content.get("id")
                    if mod_id:
                        metadata["mod_ids"] = [mod_id.lower()]
                    depends = content.get("depends", {})
                    for dep_id in depends.keys():
                        metadata["dependencies"].append({"modId": dep_id.lower(), "mandatory": True})
                except:
                    pass
    except:
        pass
    return metadata

def normalize(text):
    if not text:
        return ""
    # Lowercase and remove all non-alphanumeric characters
    return re.sub(r'[^a-z0-9]', '', text.lower())

def main():
    print("=== Scanning Jars and Compiling Mod Database ===")
    installed_mod_ids = {}
    all_jars = []
    
    # Read server mods
    if os.path.exists(MODS_SERVER):
        for f in os.listdir(MODS_SERVER):
            if f.endswith(".jar"):
                meta = parse_jar_metadata(os.path.join(MODS_SERVER, f))
                meta["side"] = "server/dual"
                all_jars.append(meta)
                for mid in meta["mod_ids"]:
                    installed_mod_ids.setdefault(mid.lower(), []).append(meta)
                    
    # Read client mods
    if os.path.exists(MODS_CLIENT):
        for f in os.listdir(MODS_CLIENT):
            if f.endswith(".jar"):
                meta = parse_jar_metadata(os.path.join(MODS_CLIENT, f))
                meta["side"] = "client"
                all_jars.append(meta)
                for mid in meta["mod_ids"]:
                    installed_mod_ids.setdefault(mid.lower(), []).append(meta)
                    
    print(f"Scanned {len(all_jars)} jar files.")
    print(f"Registered {len(installed_mod_ids)} unique Mod IDs.")
    
    # 1. Dependency Analysis
    missing_deps = {}
    for meta in all_jars:
        for dep in meta["dependencies"]:
            if not dep["mandatory"]:
                continue
            dep_id = dep["modId"].lower()
            if dep_id in SYSTEM_MOD_IDS:
                continue
            if dep_id not in installed_mod_ids:
                missing_deps.setdefault(dep_id, []).append(meta["filename"])
                
    # 2. Manifest vs Jars check
    if os.path.exists(MANIFEST_PATH):
        with open(MANIFEST_PATH, 'r', encoding='utf-8') as f:
            manifest = json.load(f)
            
        manifest_mods = manifest.get("mods", []) + manifest.get("curseforge_only", [])
        
        missing_manifest_jars = []
        for mod in manifest_mods:
            slug = mod["slug"].lower()
            name = mod["name"].lower()
            experimental = mod.get("experimental", False)
            if experimental:
                continue
                
            slug_norm = normalize(slug)
            name_norm = normalize(name)
            
            found = False
            for meta in all_jars:
                fn_norm = normalize(meta["filename"])
                # Normalize check
                if slug_norm in fn_norm or name_norm in fn_norm:
                    found = True
                    break
                for mid in meta["mod_ids"]:
                    mid_norm = normalize(mid)
                    if slug_norm == mid_norm or name_norm == mid_norm:
                        found = True
                        break
                if found:
                    break
            if not found:
                # Special manual mappings for mismatched naming conventions
                special_mappings = {
                    "macaws-furniture": "mcw-furniture",
                    "macaws-lights-and-lamps": "mcw-lights",
                    "macaws-paintings": "mcw-paintings",
                    "macaws-roofs": "mcw-roofs",
                    "macaws-trapdoors": "mcw-trapdoors",
                    "playeranimatorapi": "player-animation-lib",
                    "reap-mod": "reap",
                    "supermartijn642s-config-lib": "supermartijn642configlib",
                    "ambient-sounds-5": "ambient-sounds",
                    "ambientsounds": "ambient-sounds",
                    "just-enough-professions-jep": "just-enough-professions",
                    "just-enough-resources-jer": "just-enough-resources",
                    "simple-discord-rpc": "simple-discord-rich-presence",
                    "3dskinlayers": "skinlayers3d",
                    "bookshelf-lib": "bookshelf",
                    "zetafix": "zetafix",
                    "architectury-api": "architectury",
                    "awesome-dungeon-edition-ocean": "awesomedungeonocean",
                    "elevatormod": "elevatorid",
                    "every-compat": "everycomp",
                    "gravestone-mod": "gravestone",
                    "iron-chests": "ironchest",
                    "repurposed-structures-forge": "repurposed_structures",
                    "simple-storage-network": "storagenetwork",
                    "towns-and-towers": "t_and_t",
                    "simple-discord-rich-presence": "simplerpc",
                    "better-compatibility-checker": "better-compatability-checker",
                    "enchantment-descriptions": "enchdesc"
                }
                mapped_slug = special_mappings.get(slug, slug)
                mapped_slug_norm = normalize(mapped_slug)
                for meta in all_jars:
                    fn_norm = normalize(meta["filename"])
                    if mapped_slug_norm in fn_norm:
                        found = True
                        break
                    for mid in meta["mod_ids"]:
                        mid_norm = normalize(mid)
                        if mapped_slug_norm == mid_norm:
                            found = True
                            break
                    if found:
                        break
                        
            if not found:
                missing_manifest_jars.append(mod)
    else:
        missing_manifest_jars = []
        print("[WARNING] mod_manifest.json not found!")

    # Print Report
    print("\n" + "=" * 60)
    print("                   MODPACK VERIFICATION REPORT")
    print("=" * 60)
    
    # Duplicate Mod IDs
    duplicates = {mid: metas for mid, metas in installed_mod_ids.items() if len(metas) > 1}
    if duplicates:
        print("\n[WARNING] DUPLICATE MODS (Same Mod ID in multiple jars):")
        for mid, metas in duplicates.items():
            print(f"   • '{mid}':")
            for m in metas:
                print(f"     - {m['filename']} ({m['side']} folder)")
    else:
        print("\n[OK] No duplicate Mod IDs detected.")
        
    # Missing Dependencies
    if missing_deps:
        print("\n[ERROR] MISSING DEPENDENCIES (Mandatory libraries not found):")
        for dep_id, required_by in missing_deps.items():
            print(f"   • Mod ID '{dep_id}' is missing!")
            print(f"     Required by: {', '.join(required_by)}")
    else:
        print("\n[OK] All declared mandatory dependencies are satisfied!")
        
    # Missing Manifest Mods
    if os.path.exists(MANIFEST_PATH):
        if missing_manifest_jars:
            print(f"\n[WARNING] MISSING MODS FROM MANIFEST ({len(missing_manifest_jars)} listed in JSON but jar not found):")
            for m in missing_manifest_jars:
                print(f"   • Name: '{m['name']}' (Slug: {m['slug']})")
        else:
            print("\n[OK] All mods listed in mod_manifest.json are present in the folders!")
        
    # Check Fabric usage
    fabric_in_server = [m['filename'] for m in all_jars if m['side'] == 'server/dual' and m['loader'] == 'fabric']
    if fabric_in_server:
        print("\n[WARNING] FABRIC MODS DETECTED ON SERVER (Could cause issues via Connector):")
        for f in fabric_in_server:
            print(f"   • {f}")
    else:
        print("\n[OK] No Fabric mods detected in the server folder.")

    print("\n" + "=" * 60)

if __name__ == "__main__":
    main()
