CLIENT_ONLY = {
    # === PERFORMANS (client-side only) ===
    "badoptimizations",
    "immediatelyfast",
    "moreculling",
    "bobby",
    "reeses",
    "sodium-neoforge",
    "sodium-extra",
    "fpsreducer",
    "entityculling",
    "lambdynamiclights",
    "continuity",
    # === SHADER / RENDER ===
    "iris-neoforge",
    "iris_shader_folder",
    # === ENVANTER / UI ===
    "inventoryhud",
    "inventoryprofilesnext",
    "libipn",
    "clientsort",
    "controlling",
    "mousetweaks",
    "playeranimationlib",
    "player-animation-lib",
    "playeranimatorapi",
    # === GÖRSEL / HUD ===
    "chat_heads",
    "notenoughanimations",
    "skinlayers3d",
    "eating-animation",
    "eatinganimation",
    "overflowingbars",
    "highlighter",
    "entity_model_features",
    "entity_texture_features",
    "particleeffects",
    "particle_effects",
    "particle",
    "midnightlib",
    "colorwheel",
    # === SES ===
    "sound-physics",
    "ambientso",
    "audio improvements",
    "melody",
    # === MÜZİK / BAŞLANGIÇ EKRANI ===
    "fancymenu",
    "drippyloadingscreen",
    "betterworldloading",
    "konkrete",
    # === YARDIMCI / KÜÇÜK CLIENT MODLAR ===
    "essential_",
    "txnilib",
    "simpledrpc",
    "simplerpc",
    "simplediscordrichpresence",
    "rebind_narrator",
    "longercha",
    "distraction_free_recipes",
    "nvidium",
    "offlineskins",
    "customskinloader",
    "reeses-sodium",
    "appleskin",
    # === BİLGİ / ARAYÜZ ===
    "justenoughprofessions",
    "betteradvancements",
    "advancementplaques",
    "yungsmenutweaks",
    "bettermodsbutton",
    "resourcepackoverrides",
    "stylisheffects",
    "mindfulda",
    "configureddefaults",
    "deleteworlds",
    "leavesbe",
    "tesseraui",
    "logbegone",
    "enchdesc",
    "modpack-update-checker",
}

def is_client_only(filename: str) -> bool:
    fl = filename.lower()
    for prefix in CLIENT_ONLY:
        if fl.startswith(prefix.lower()):
            print(f"Matched prefix: {prefix}")
            return True
    return False

filename = "DistantHorizons-3.2.0-b-1.21.1-fabric-neoforge.jar"
print(f"Checking: {filename}")
res = is_client_only(filename)
print(f"Result: {res}")
