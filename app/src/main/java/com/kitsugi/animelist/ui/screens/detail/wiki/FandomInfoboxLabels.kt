package com.kitsugi.animelist.ui.screens.detail.wiki

import com.kitsugi.animelist.R
import java.util.Locale

/**
 * Fandom bilgi kutusundaki İngilizce **etiketleri**, **grup başlıklarını** ve sık görülen
 * **kısa değerleri** arayüz dilindeki string kaynağına eşler (saf fonksiyon).
 *
 * Wiki'ler şablon alanlarını kendi alışkanlıklarıyla adlandırır ("Grad rank", "Former
 * affiliation", "Titan kills"…). Eşlenemeyen etiket/değer özgün biçimiyle kalır; serbest
 * metin değerler (ör. akraba listesi) için arayüzdeki çeviri düğmesi kullanılır.
 *
 * Eşleme hem etikete hem de şablon parametresine (`source`, ör. `former_affiliation`)
 * bakar; böylece etiket HTML süslemeli olsa da yakalanır.
 */
object FandomInfoboxLabels {

    /** Bilgi kutusu alan etiketi → string kaynağı; bilinmiyorsa null. */
    fun labelResId(label: String, source: String? = null): Int? {
        labelRes(normalize(label))?.let { return it }
        val fromSource = source?.let { normalize(it.replace('_', ' ')) } ?: return null
        return labelRes(fromSource)
    }

    /** Grup başlığı ("Biological Information", "Debut"…) → string kaynağı; bilinmiyorsa null. */
    fun headerResId(header: String): Int? = when (normalize(header)) {
        "biological information", "biological info", "biology", "biographical information",
        "biographical info", "personal information", "personal info", "personal details",
        "personal", "general information", "general info", "basic information", "basic info",
        "profile", "character information", "character info", "characteristics",
        "physical description", "physical information", "physical", "physical attributes",
        "statistics", "stats", "vital statistics", "character profile",
        "profile information", "info", "information", "details" -> R.string.fandom_ib_group_personal
        "professional information", "professional info", "professional status",
        "professional details", "occupation information", "career", "career information",
        "military information", "military info", "service information",
        "work information", "job information", "employment",
        "employment information" -> R.string.fandom_ib_group_professional
        "other information", "other info", "miscellaneous", "misc", "additional information",
        "additional info", "extra information", "trivia", "extra info", "extra",
        "additional", "supplementary", "supplementary information",
        "additional details" -> R.string.fandom_ib_group_other
        "debut", "debuts", "first appearance", "first appearances", "appearances",
        "media", "appearance information", "media appearances",
        "initial appearance" -> R.string.fandom_ib_group_debut
        "voice actors", "voice actor", "voice", "voiced by", "voice acting", "cast",
        "portrayal", "portrayed by", "actors", "dub", "dubs", "dubbing",
        "voice cast", "cast and voices" -> R.string.fandom_ib_group_voice
        "relatives", "family", "family information", "relationships",
        "relations", "family and relationships", "family & relationships" -> R.string.fandom_ib_group_family
        "abilities", "powers", "powers and abilities", "skills", "combat information",
        "combat", "equipment", "weapons", "powers & abilities", "abilities & powers",
        "skills and abilities", "fighting style", "fighting styles",
        "martial arts" -> R.string.fandom_ib_group_abilities
        "affiliation", "affiliations", "allegiance", "organization", "organizations",
        "status information", "status" -> R.string.fandom_ib_group_affiliation
        "production information", "production", "production info", "release information",
        "release info", "publication", "publication information", "publication info",
        "broadcast information", "broadcast", "animation production",
        "production and staff", "staff", "production crew", "crew" -> R.string.fandom_ib_group_production
        "chronology", "episode chronology", "chapter chronology", "navigation",
        "episode guide", "chapter guide", "series chronology",
        "publication chronology", "release chronology", "reading order",
        "watch order", "order" -> R.string.fandom_ib_group_chronology
        "image gallery", "gallery", "images", "photos", "pictures", "art gallery",
        "concept art", "artwork" -> R.string.fandom_ib_group_gallery
        "education", "school", "schools", "university", "college", "academy",
        "education information", "school information" -> R.string.fandom_ib_group_education
        "novel", "novels", "light novel", "light novels", "novel series",
        "web novel", "web novels", "light novel series", "visual novel",
        "visual novels" -> R.string.fandom_ib_group_novel
        "manga", "manhwa", "manhua", "manga series", "manhwa series", "comic",
        "comics", "comic series", "strip", "strips", "four-panel", "4-koma",
        "yonkoma" -> R.string.fandom_ib_group_manga
        "webtoon", "webtoons", "webcomic", "webcomics", "digital comic",
        "digital comics" -> R.string.fandom_ib_group_webtoon
        "anime", "anime series", "anime television series", "television series",
        "tv series", "series", "show", "animated series", "animation" -> R.string.fandom_ib_group_anime
        "film", "films", "movie", "movies", "anime film", "anime films",
        "animated film", "animated films", "feature film", "compilation film",
        "film series" -> R.string.fandom_ib_group_film
        "ova", "ovas", "ona", "onas", "original video animation",
        "original net animation", "special", "specials" -> R.string.fandom_ib_group_ova
        "video game", "video games", "game", "games", "video game series",
        "mobile game", "mobile games", "rpg" -> R.string.fandom_ib_group_game
        "stage play", "stage plays", "theater", "theatre", "musical", "musicals",
        "stage", "play", "plays", "kabuki", "opera", "stage adaptation",
        "stage adaptations", "drama cd", "drama cds", "radio drama",
        "radio show", "audio drama", "audio dramas" -> R.string.fandom_ib_group_stage
        "live action", "live-action", "live action series", "live-action series",
        "live action film", "live-action film", "live action drama",
        "live-action drama", "drama", "dramas", "tv drama", "korean drama",
        "jdrama", "kdrama" -> R.string.fandom_ib_group_live
        "related works", "related media", "other works", "adaptations",
        "media adaptations", "media adaptation", "franchise", "spin-off",
        "spin-offs", "sequel", "sequels", "prequel", "prequels", "remake",
        "remakes", "crossover", "crossovers", "collaboration",
        "collaborations" -> R.string.fandom_ib_group_related
        "music", "soundtrack", "soundtracks", "discography", "theme songs",
        "character songs", "albums", "album", "singles", "single", "cd", "cds",
        "ost" -> R.string.fandom_ib_group_music
        "reception", "critical reception", "critical response", "accolades",
        "awards", "reviews" -> R.string.fandom_section_reception
        "merchandise", "merchandising", "figures", "products", "goods",
        "collectibles" -> R.string.fandom_section_merchandise
        "overview", "about", "summary", "introduction", "intro" -> R.string.fandom_section_overview
        else -> null
    }

    /**
     * Kısa, sayılabilir değerler ("Male", "Deceased", "Unknown"…) → string kaynağı.
     * Serbest metinler null döner (çevrilmez; çeviri düğmesine bırakılır).
     */
    fun valueResId(value: String): Int? = when (normalize(value).trimEnd('.')) {
        "male", "man", "men", "boy" -> R.string.fandom_ib_value_male
        "female", "woman", "women", "girl" -> R.string.fandom_ib_value_female
        "unknown", "n/a", "none", "not available", "unspecified", "undisclosed",
        "tba", "tbd", "?" -> R.string.fandom_ib_value_unknown
        "alive", "living", "active" -> R.string.fandom_ib_value_alive
        "deceased", "dead", "died", "killed" -> R.string.fandom_ib_value_deceased
        "presumed dead", "presumed deceased" -> R.string.fandom_ib_value_presumed_dead
        "missing", "missing in action", "mia", "disappeared" -> R.string.fandom_ib_value_missing
        "retired", "inactive", "former" -> R.string.fandom_ib_value_retired
        "human", "humans" -> R.string.fandom_ib_value_human
        "demon", "demons", "devil", "devils" -> R.string.fandom_ib_value_demon
        "soldier", "soldiers" -> R.string.fandom_ib_value_soldier
        "student", "students" -> R.string.fandom_ib_value_student
        "teacher", "instructor", "sensei" -> R.string.fandom_ib_value_teacher
        "yes" -> R.string.fandom_ib_value_yes
        "no" -> R.string.fandom_ib_value_no
        "manga", "manga chapter" -> R.string.fandom_ib_value_manga
        "anime", "anime episode" -> R.string.fandom_ib_value_anime
        "light novel", "novel" -> R.string.fandom_ib_value_light_novel
        "movie", "film" -> R.string.fandom_ib_value_movie
        "ongoing", "airing", "publishing", "in progress" -> R.string.fandom_ib_value_ongoing
        "completed", "finished", "ended", "concluded" -> R.string.fandom_ib_value_completed
        "hiatus", "on hiatus" -> R.string.fandom_ib_value_hiatus
        "cancelled", "canceled", "discontinued" -> R.string.fandom_ib_value_cancelled
        "main character", "main", "protagonist", "main protagonist" -> R.string.fandom_ib_value_protagonist
        "antagonist", "main antagonist", "villain" -> R.string.fandom_ib_value_antagonist
        "supporting character", "supporting", "secondary character", "minor character",
        "minor" -> R.string.fandom_ib_value_supporting
        "single", "unmarried" -> R.string.fandom_ib_value_single
        "married" -> R.string.fandom_ib_value_married
        "divorced" -> R.string.fandom_ib_value_divorced
        "widowed", "widower", "widow" -> R.string.fandom_ib_value_widowed
        "engaged", "betrothed" -> R.string.fandom_ib_value_engaged
        "dating", "in a relationship", "relationship", "partnered" -> R.string.fandom_ib_value_dating
        "sealed", "sealed away" -> R.string.fandom_ib_value_sealed
        "imprisoned", "jailed", "incarcerated" -> R.string.fandom_ib_value_imprisoned
        "captured", "taken prisoner", "pow" -> R.string.fandom_ib_value_captured
        "comatose", "in a coma" -> R.string.fandom_ib_value_comatose
        "unconscious", "unresponsive" -> R.string.fandom_ib_value_unconscious
        "resurrected", "revived", "brought back" -> R.string.fandom_ib_value_resurrected
        "reincarnated", "reborn" -> R.string.fandom_ib_value_reincarnated
        "ascended", "ascension" -> R.string.fandom_ib_value_ascended
        "awakened", "awoke", "awoken" -> R.string.fandom_ib_value_awakened
        "possessed", "taken over" -> R.string.fandom_ib_value_possessed
        "transformed", "changed" -> R.string.fandom_ib_value_transformed
        "evolved", "evolution" -> R.string.fandom_ib_value_evolved
        "merged", "fused" -> R.string.fandom_ib_value_merged
        "banished", "exiled" -> R.string.fandom_ib_value_banished
        "non-binary", "nonbinary", "non binary" -> R.string.fandom_ib_value_non_binary
        "intersex" -> R.string.fandom_ib_value_intersex
        "agender" -> R.string.fandom_ib_value_agender
        "genderfluid", "gender-fluid" -> R.string.fandom_ib_value_genderfluid
        "trans male", "transgender male", "trans man", "ftm" -> R.string.fandom_ib_value_trans_male
        "trans female", "transgender female", "trans woman", "mtf" -> R.string.fandom_ib_value_trans_female
        "undead", "living dead" -> R.string.fandom_ib_value_undead
        "vampire", "vampires" -> R.string.fandom_ib_value_vampire
        "werewolf", "werewolves", "lycanthrope" -> R.string.fandom_ib_value_werewolf
        "elf", "elves" -> R.string.fandom_ib_value_elf
        "dwarf", "dwarves", "dwarfs" -> R.string.fandom_ib_value_dwarf
        "dragon", "dragons" -> R.string.fandom_ib_value_dragon
        "angel", "angels" -> R.string.fandom_ib_value_angel
        "god", "gods", "deity", "deities" -> R.string.fandom_ib_value_god
        "goddess", "goddesses" -> R.string.fandom_ib_value_goddess
        "spirit", "spirits" -> R.string.fandom_ib_value_spirit
        "ghost", "ghosts" -> R.string.fandom_ib_value_ghost
        "zombie", "zombies" -> R.string.fandom_ib_value_zombie
        "alien", "aliens", "extraterrestrial" -> R.string.fandom_ib_value_alien
        "robot", "robots" -> R.string.fandom_ib_value_robot
        "android", "androids" -> R.string.fandom_ib_value_android
        "cyborg", "cyborgs" -> R.string.fandom_ib_value_cyborg
        "ai", "artificial intelligence", "a.i." -> R.string.fandom_ib_value_ai
        "clone", "clones" -> R.string.fandom_ib_value_clone
        "mutant", "mutants" -> R.string.fandom_ib_value_mutant
        "humanoid", "humanoids" -> R.string.fandom_ib_value_humanoid
        "monster", "monsters", "beast", "beasts", "creature", "creatures" -> R.string.fandom_ib_value_monster
        "animal", "animals" -> R.string.fandom_ib_value_animal
        "yokai", "yōkai", "youkai" -> R.string.fandom_ib_value_yokai
        "hunter", "hunters" -> R.string.fandom_ib_value_hunter
        "mercenary", "mercenaries" -> R.string.fandom_ib_value_mercenary
        "assassin", "assassins" -> R.string.fandom_ib_value_assassin
        "knight", "knights" -> R.string.fandom_ib_value_knight
        "samurai" -> R.string.fandom_ib_value_samurai
        "ninja", "ninjas", "shinobi" -> R.string.fandom_ib_value_ninja
        "mage", "mages", "magus" -> R.string.fandom_ib_value_mage
        "witch", "witches" -> R.string.fandom_ib_value_witch
        "wizard", "wizards", "sorcerer", "sorcerers" -> R.string.fandom_ib_value_wizard
        "hero", "heroes" -> R.string.fandom_ib_value_hero
        "king", "kings" -> R.string.fandom_ib_value_king
        "queen", "queens" -> R.string.fandom_ib_value_queen
        "prince", "princes" -> R.string.fandom_ib_value_prince
        "princess", "princesses" -> R.string.fandom_ib_value_princess
        "emperor", "emperors" -> R.string.fandom_ib_value_emperor
        "empress", "empresses" -> R.string.fandom_ib_value_empress
        "president", "presidents" -> R.string.fandom_ib_value_president
        "professor", "professors" -> R.string.fandom_ib_value_professor
        "doctor", "doctors", "physician" -> R.string.fandom_ib_value_doctor
        "scientist", "scientists" -> R.string.fandom_ib_value_scientist
        "engineer", "engineers" -> R.string.fandom_ib_value_engineer
        "programmer", "programmers", "developer", "developers" -> R.string.fandom_ib_value_programmer
        "writer", "writers", "author", "authors", "novelist" -> R.string.fandom_ib_value_writer
        "artist", "artists", "painter", "illustrator" -> R.string.fandom_ib_value_artist
        "actor", "actors", "actress", "actresses" -> R.string.fandom_ib_value_actor
        "idol", "idols" -> R.string.fandom_ib_value_idol
        "singer", "singers", "vocalist" -> R.string.fandom_ib_value_singer
        "musician", "musicians", "composer" -> R.string.fandom_ib_value_musician
        "chef", "cooks", "cook" -> R.string.fandom_ib_value_chef
        "merchant", "merchants", "trader", "shopkeeper" -> R.string.fandom_ib_value_merchant
        "priest", "priests", "cleric" -> R.string.fandom_ib_value_priest
        "monk", "monks", "nun", "nuns" -> R.string.fandom_ib_value_monk
        "adventurer", "adventurers" -> R.string.fandom_ib_value_adventurer
        "detective", "detectives", "investigator" -> R.string.fandom_ib_value_detective
        "police officer", "police", "officer", "policeman" -> R.string.fandom_ib_value_police_officer
        "lawyer", "lawyers", "attorney" -> R.string.fandom_ib_value_lawyer
        "politician", "politicians" -> R.string.fandom_ib_value_politician
        "office worker", "office lady", "salaryman", "businessman" -> R.string.fandom_ib_value_office_worker
        "high school student", "highschool student" -> R.string.fandom_ib_value_high_school_student
        "middle school student", "junior high student" -> R.string.fandom_ib_value_middle_school_student
        "college student", "university student" -> R.string.fandom_ib_value_college_student
        "criminal", "criminals", "outlaw" -> R.string.fandom_ib_value_criminal
        "thief", "thieves", "burglar" -> R.string.fandom_ib_value_thief
        "pirate", "pirates" -> R.string.fandom_ib_value_pirate
        "bounty hunter", "bounty hunters" -> R.string.fandom_ib_value_bounty_hunter
        "freelancer", "freelance" -> R.string.fandom_ib_value_freelancer
        "ceo", "chief executive officer" -> R.string.fandom_ib_value_ceo
        "manager", "managers", "supervisor" -> R.string.fandom_ib_value_manager
        "secretary", "secretaries" -> R.string.fandom_ib_value_secretary
        "librarian", "librarians" -> R.string.fandom_ib_value_librarian
        "journalist", "journalists", "reporter" -> R.string.fandom_ib_value_journalist
        "photographer", "photographers" -> R.string.fandom_ib_value_photographer
        "model", "models" -> R.string.fandom_ib_value_model
        "designer", "designers" -> R.string.fandom_ib_value_designer
        "architect", "architects" -> R.string.fandom_ib_value_architect
        "pilot", "pilots" -> R.string.fandom_ib_value_pilot
        "driver", "drivers", "chauffeur" -> R.string.fandom_ib_value_driver
        "mechanic", "mechanics" -> R.string.fandom_ib_value_mechanic
        "blacksmith", "blacksmiths" -> R.string.fandom_ib_value_blacksmith
        "alchemist", "alchemists" -> R.string.fandom_ib_value_alchemist
        "summoner", "summoners" -> R.string.fandom_ib_value_summoner
        "healer", "healers" -> R.string.fandom_ib_value_healer
        "archer", "archers", "gunner" -> R.string.fandom_ib_value_archer
        "swordsman", "swordsmen", "swordswoman" -> R.string.fandom_ib_value_swordsman
        "captain", "captains" -> R.string.fandom_ib_value_captain
        "commander", "commanders" -> R.string.fandom_ib_value_commander
        "leader", "leaders", "chief" -> R.string.fandom_ib_value_leader
        "farmer", "farmers" -> R.string.fandom_ib_value_farmer
        "bartender", "bartenders" -> R.string.fandom_ib_value_bartender
        "barista", "baristas" -> R.string.fandom_ib_value_barista
        "waiter", "waitress", "waiters" -> R.string.fandom_ib_value_waiter
        "nurse", "nurses" -> R.string.fandom_ib_value_nurse
        "firefighter", "firefighters" -> R.string.fandom_ib_value_firefighter
        "good", "lawful good", "chaotic good", "neutral good" -> R.string.fandom_ib_value_good
        "evil", "lawful evil", "chaotic evil", "neutral evil" -> R.string.fandom_ib_value_evil
        "neutral", "true neutral" -> R.string.fandom_ib_value_neutral
        else -> null
    }

    private fun labelRes(key: String): Int? = when (key) {
        "name", "full name", "real name", "character name", "english name",
        "english" -> R.string.fandom_ib_name
        "alias", "aliases", "alias(es)", "nickname", "nicknames", "nickname(s)", "other names",
        "other name", "also known as", "aka", "a.k.a", "a.k.a.", "epithet", "epithets",
        "title", "titles", "title(s)", "moniker", "monikers" -> R.string.fandom_ib_alias
        "kanji", "japanese", "japanese name", "kana", "日本語", "name in japanese",
        "japanese title" -> R.string.fandom_ib_japanese
        "romaji", "rōmaji", "romanji", "romanization", "romanized", "romanized name",
        "hepburn", "rōmaji name" -> R.string.fandom_ib_romaji
        "hangul", "korean", "korean name", "한글", "hanja" -> R.string.fandom_ib_korean
        "chinese", "chinese name", "hanzi", "pinyin", "中文" -> R.string.fandom_ib_chinese
        "species", "race", "race/species", "species/race", "kind", "type of being",
        "classification" -> R.string.fandom_ib_species
        "gender", "sex" -> R.string.fandom_ib_gender
        "age", "ages", "age(s)" -> R.string.fandom_ib_age
        "height", "heights" -> R.string.fandom_ib_height
        "weight", "weights" -> R.string.fandom_ib_weight
        "birthday", "birthdate", "birth date", "date of birth", "dob", "born",
        "birth" -> R.string.fandom_ib_birthday
        "birthplace", "birth place", "place of birth", "hometown", "home town",
        "origin", "place of origin", "homeland", "home" -> R.string.fandom_ib_birthplace
        "death", "deathdate", "death date", "date of death", "died", "death day",
        "deathday" -> R.string.fandom_ib_death_date
        "place of death", "death place", "deathplace" -> R.string.fandom_ib_death_place
        "cause of death", "cause of death(s)", "death cause" -> R.string.fandom_ib_death_cause
        "zodiac", "zodiac sign", "star sign", "astrological sign", "horoscope",
        "sign" -> R.string.fandom_ib_zodiac
        "blood type", "bloodtype", "blood" -> R.string.fandom_ib_blood_type
        "hair color", "hair colour", "hair", "haircolor" -> R.string.fandom_ib_hair_color
        "eye color", "eye colour", "eyes", "eyecolor" -> R.string.fandom_ib_eye_color
        "skin color", "skin colour", "skin", "skin tone" -> R.string.fandom_ib_skin_color
        "relatives", "relative(s)", "family", "family members", "family member(s)",
        "relations", "kin" -> R.string.fandom_ib_relatives
        "residence", "residences", "residence(s)", "current residence", "lives in",
        "location", "current location", "base of operations", "base" -> R.string.fandom_ib_residence
        "nationality", "nationalities", "country", "country of origin",
        "citizenship" -> R.string.fandom_ib_nationality
        "ethnicity", "ethnic group", "heritage", "bloodline", "lineage",
        "clan" -> R.string.fandom_ib_ethnicity
        "status", "current status", "life status", "living status",
        "state", "series status", "publication status", "airing status",
        "manga status", "anime status" -> R.string.fandom_ib_status
        "affiliation", "affiliations", "affiliation(s)", "current affiliation",
        "current affiliations", "allegiance", "allegiances", "faction", "factions",
        "group", "groups", "organization", "organizations", "organisation", "team",
        "teams", "member of" -> R.string.fandom_ib_affiliation
        "former affiliation", "former affiliations", "former affiliation(s)",
        "previous affiliation", "previous affiliations", "past affiliation",
        "past affiliations", "former team", "former teams",
        "former organization" -> R.string.fandom_ib_former_affiliation
        "occupation", "occupations", "occupation(s)", "job", "jobs", "profession",
        "professions", "role", "roles", "position", "positions", "current occupation",
        "work" -> R.string.fandom_ib_occupation
        "former occupation", "former occupations", "former occupation(s)",
        "previous occupation", "previous occupations", "past occupation",
        "former position", "former positions", "former job" -> R.string.fandom_ib_former_occupation
        "rank", "ranks", "rank(s)", "military rank", "grade", "grades", "grad rank",
        "graduation rank", "graduating rank", "class rank", "level", "tier",
        "ranking" -> R.string.fandom_ib_rank
        "squad", "squads", "unit", "units", "division", "divisions", "branch",
        "regiment", "corps", "platoon" -> R.string.fandom_ib_squad
        "partner", "partners", "partner(s)", "comrades", "allies", "ally",
        "companions" -> R.string.fandom_ib_partner
        "teacher", "teachers", "mentor", "mentors", "master", "masters",
        "trained by" -> R.string.fandom_ib_mentor
        "student", "students", "apprentice", "apprentices", "disciple",
        "disciples" -> R.string.fandom_ib_student
        "enemies", "enemy", "rivals", "rival", "nemesis" -> R.string.fandom_ib_enemies
        "kills", "kill count", "total kills", "titan kills", "titans killed", "victims",
        "people killed", "number of kills", "notable kills" -> R.string.fandom_ib_kills
        "abilities", "ability", "abilities(s)", "powers", "power", "powers and abilities",
        "skills", "skill", "special abilities", "techniques", "technique", "quirk",
        "quirks", "magic", "cursed technique", "devil fruit", "nen type", "nen", "stand",
        "breathing style", "kekkei genkai", "nature type", "jutsu", "semblance",
        "alignment" -> R.string.fandom_ib_abilities
        "weapon", "weapons", "weapon(s)", "weapon of choice", "equipment", "gear",
        "arms", "tools", "signature weapon" -> R.string.fandom_ib_weapon
        "titan", "titan form", "titans", "titan type", "form", "forms", "transformation",
        "transformations", "alter ego", "alter egos" -> R.string.fandom_ib_form
        "debut", "first appearance", "first appearances", "debut appearance",
        "appears in", "appearances", "introduced in" -> R.string.fandom_ib_debut
        "chapter", "manga debut", "manga", "first chapter", "chapter debut",
        "debut chapter" -> R.string.fandom_ib_debut_chapter
        "episode", "anime debut", "anime", "first episode", "episode debut",
        "debut episode" -> R.string.fandom_ib_debut_episode
        "movie", "film", "movie debut", "film debut", "movies" -> R.string.fandom_ib_debut_movie
        "game", "games", "game debut", "video game", "video games",
        "video game debut" -> R.string.fandom_ib_debut_game
        "novel", "light novel", "novel debut", "light novel debut",
        "novels" -> R.string.fandom_ib_debut_novel
        "last appearance", "final appearance", "last seen", "final chapter",
        "final episode" -> R.string.fandom_ib_last_appearance
        "voice actor", "voice actors", "voice actor(s)", "voiced by", "seiyuu", "seiyū",
        "cv", "voice", "voices", "voice cast", "voices by", "voicing" -> R.string.fandom_ib_voice_actor
        "japanese voice", "japanese voice actor", "japanese va", "japanese voice actors",
        "japanese seiyuu", "japanese (seiyuu)", "japanese (voice)", "jp voice",
        "voice actor (japanese)", "ja" -> R.string.fandom_ib_voice_japanese
        "english voice", "english voice actor", "english va", "english voice actors",
        "english dub", "english (dub)", "english (voice)", "en voice",
        "voice actor (english)", "en" -> R.string.fandom_ib_voice_english
        "other voices", "other voice actors", "other languages", "other dubs",
        "other dubbing" -> R.string.fandom_ib_voice_other
        "actor", "actors", "actor(s)", "portrayed by", "played by", "live action",
        "live-action actor", "live action actor", "live-action" -> R.string.fandom_ib_actor
        "creator", "creators", "created by", "author", "authors", "writer", "writers",
        "written by", "mangaka", "original creator", "story by", "original story",
        "story", "novel by", "source author" -> R.string.fandom_ib_creator
        "screenplay", "screenplay by", "script", "script by", "teleplay",
        "series composition", "scenario", "written for screen by",
        "writing" -> R.string.fandom_ib_screenplay
        "artist", "artists", "illustrator", "illustrators", "art", "art by",
        "character design", "character designer", "illustrated by", "drawn by",
        "artwork by", "illustration by", "illustration", "illustrations",
        "artwork", "original character design" -> R.string.fandom_ib_artist
        "director", "directors", "directed by", "chief director", "series director",
        "supervising director", "general director", "direction", "directing",
        "director(s)" -> R.string.fandom_ib_director
        "studio", "studios", "animation studio", "production studio",
        "anime studio", "animation production", "animation", "animated by" -> R.string.fandom_ib_studio
        "produced by", "producer", "producers", "producer(s)", "executive producer",
        "executive producers", "animation producer", "production producer",
        "chief producer" -> R.string.fandom_ib_producer
        "publisher", "publishers", "published by", "imprint", "original publisher",
        "english publisher", "english-language publisher", "publication",
        "publishing", "publisher (english)" -> R.string.fandom_ib_publisher
        "magazine", "serialization", "serialized in", "serialization magazine",
        "publication magazine", "magazine (serialization)" -> R.string.fandom_ib_magazine
        "label", "record label", "music label", "label (music)" -> R.string.fandom_ib_record_label
        "network", "networks", "channel", "broadcaster", "aired on",
        "original network", "original channel", "broadcast network",
        "original broadcast network", "tv network", "television network",
        "station", "tv station", "broadcast station", "original station",
        "broadcast channel", "channel(s)" -> R.string.fandom_ib_network
        "streaming", "streaming platform", "streaming platforms",
        "streaming service", "streaming services", "digital streaming",
        "available on" -> R.string.fandom_ib_streaming
        "genre", "genres", "genre(s)" -> R.string.fandom_ib_genre
        "demographic", "demographics", "target demographic", "demographic group",
        "audience", "target audience", "readership" -> R.string.fandom_ib_demographic
        "release date", "released", "release", "air date", "aired", "airdate", "original run",
        "publication date", "published", "broadcast date", "premiere", "premiered",
        "date", "first aired", "last aired", "original air date", "release dates",
        "air dates", "broadcast period", "broadcast run", "run period",
        "original release", "release date(s)" -> R.string.fandom_ib_release_date
        "episodes", "episode count", "number of episodes", "no. of episodes",
        "total episodes" -> R.string.fandom_ib_episodes
        "chapters", "chapter count", "number of chapters", "no. of chapters",
        "total chapters" -> R.string.fandom_ib_chapters
        "volumes", "volume", "volume count", "number of volumes",
        "no. of volumes" -> R.string.fandom_ib_volumes
        "runtime", "running time", "duration", "length" -> R.string.fandom_ib_runtime
        "season", "seasons", "season(s)", "arc", "arcs", "saga", "sagas",
        "part" -> R.string.fandom_ib_season
        "no. of seasons", "number of seasons", "season count", "total seasons",
        "seasons count", "no of seasons" -> R.string.fandom_ib_season_count
        "previous", "previous episode", "previous chapter", "prev", "preceded by",
        "←" -> R.string.fandom_ib_previous
        "next", "next episode", "next chapter", "followed by", "→" -> R.string.fandom_ib_next
        "opening", "opening theme", "op", "opening song" -> R.string.fandom_ib_opening
        "ending", "ending theme", "ed", "ending song" -> R.string.fandom_ib_ending
        "theme song", "theme songs", "soundtrack", "insert song",
        "song", "theme music", "main theme", "main theme song", "insert songs",
        "songs" -> R.string.fandom_ib_theme_song
        "music", "music by", "composed by", "composer", "composers", "score by",
        "music composition", "theme music by", "composition" -> R.string.fandom_ib_music
        "likes", "like", "favorite", "favorites", "favourite", "favourites",
        "favorite food", "favourite food", "hobbies", "hobby", "interests" -> R.string.fandom_ib_likes
        "dislikes", "dislike", "hates", "fears", "fear", "weakness",
        "weaknesses" -> R.string.fandom_ib_dislikes
        "goal", "goals", "dream", "dreams", "motive", "motives", "objective",
        "objectives", "ambition" -> R.string.fandom_ib_goal
        "personality", "personality type", "mbti", "temperament" -> R.string.fandom_ib_personality
        "quote", "quotes", "catchphrase", "motto" -> R.string.fandom_ib_quote
        "image", "images", "picture", "caption" -> R.string.fandom_ib_image
        "wiki", "website", "official site", "official website", "links", "external links",
        "homepage", "site", "web", "url", "official blog", "blog" -> R.string.fandom_ib_website
        "notes", "note", "trivia", "misc", "miscellaneous", "other" -> R.string.fandom_ib_notes
        "type", "types", "category", "categories", "class", "classes",
        "variant", "variants" -> R.string.fandom_ib_type
        "leader", "leaders", "led by", "commander", "captain", "head", "boss",
        "founder", "founders" -> R.string.fandom_ib_leader
        "members", "member", "notable members", "known members", "roster",
        "membership" -> R.string.fandom_ib_members
        "headquarters", "hq", "headquarter", "main base" -> R.string.fandom_ib_headquarters
        "purpose", "goals/purpose", "function", "aim", "mission" -> R.string.fandom_ib_purpose
        "user", "users", "wielder", "wielders", "owner", "owners", "current user",
        "current users", "known users", "holder", "holders" -> R.string.fandom_ib_users
        "former users", "previous users", "former user", "previous user", "former wielder",
        "former owners", "former holders", "previous holders" -> R.string.fandom_ib_former_users
        "manga debut chapter", "first seen" -> R.string.fandom_ib_debut
        "rating", "age rating", "content rating", "tv rating", "mpaa", "mpaa rating",
        "film rating", "certification", "age certification", "parental guide" -> R.string.fandom_ib_rating
        "awards", "award", "accolades", "honors", "honours", "prizes",
        "nominations", "awards and nominations", "award list" -> R.string.fandom_ib_awards
        "language", "languages", "original language", "original language(s)",
        "native language", "language(s)", "original languages" -> R.string.fandom_ib_language
        "source material", "source", "based on", "original work", "original source",
        "source work" -> R.string.fandom_ib_source
        "years active", "active years", "years", "period of activity",
        "career span" -> R.string.fandom_ib_years_active
        "known for", "notable works", "famous works", "popular works",
        "signature works", "works", "notable work" -> R.string.fandom_ib_known_for
        "spouse", "spouses", "husband", "wife", "marriage", "married to",
        "consort" -> R.string.fandom_ib_spouse
        "children", "child", "sons", "daughters", "offspring", "kids" -> R.string.fandom_ib_children
        "parents", "parent", "father", "mother", "mom", "dad" -> R.string.fandom_ib_parents
        "siblings", "sibling", "brothers", "sisters", "brother", "sister" -> R.string.fandom_ib_siblings
        "sound director", "sound direction", "audio director",
        "recording director" -> R.string.fandom_ib_sound_director
        "distributor", "distributors", "distribution", "distributed by",
        "distributor(s)" -> R.string.fandom_ib_distributor
        "first published", "first publication", "initial release",
        "original publication", "first release", "first released" -> R.string.fandom_ib_first_published
        "english title", "title (english)", "english-language title",
        "localized title", "localised title", "international title",
        "english title(s)" -> R.string.fandom_ib_english_title
        "literal title", "translated title", "literal translation",
        "translation of title", "title translation" -> R.string.fandom_ib_literal_title
        "translator", "translators", "translated by", "translation",
        "localization", "localisation", "adapter", "english adaptation" -> R.string.fandom_ib_translator
        "box office", "box-office", "box office gross", "gross", "gross revenue",
        "ticket sales" -> R.string.fandom_ib_box_office
        "budget", "production budget", "cost", "production cost" -> R.string.fandom_ib_budget
        "education", "school", "schools", "university", "college", "alma mater",
        "academy", "education information", "school information" -> R.string.fandom_ib_group_education
        "webtoon service", "webtoon · service", "webtoon platform", "novel service",
        "web novel service", "digital service", "platform", "service",
        "hosting platform", "serialization platform", "publishing platform",
        "serialization service" -> R.string.fandom_ib_service
        "licensed by", "license", "licensor", "english license", "english licensor",
        "north american license", "license (english)", "licensed by (english)" -> R.string.fandom_ib_licensed
        else -> null
    }

    private fun normalize(raw: String): String =
        raw.trim()
            .lowercase(Locale.US)
            .replace(Regex("\\s+"), " ")
            .trimEnd(':', ' ')
}
