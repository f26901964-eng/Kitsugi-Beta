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
        "statistics", "stats", "vital statistics" -> R.string.fandom_ib_group_personal
        "professional information", "professional info", "professional status",
        "professional details", "occupation information", "career", "career information",
        "military information", "military info", "service information" -> R.string.fandom_ib_group_professional
        "other information", "other info", "miscellaneous", "misc", "additional information",
        "additional info", "extra information", "trivia" -> R.string.fandom_ib_group_other
        "debut", "debuts", "first appearance", "first appearances", "appearances",
        "media", "appearance information" -> R.string.fandom_ib_group_debut
        "voice actors", "voice actor", "voice", "voiced by", "voice acting", "cast",
        "portrayal", "portrayed by", "actors" -> R.string.fandom_ib_group_voice
        "relatives", "family", "family information", "relationships",
        "relations" -> R.string.fandom_ib_group_family
        "abilities", "powers", "powers and abilities", "skills", "combat information",
        "combat", "equipment", "weapons" -> R.string.fandom_ib_group_abilities
        "affiliation", "affiliations", "allegiance", "organization", "organizations",
        "status information", "status" -> R.string.fandom_ib_group_affiliation
        "production information", "production", "production info", "release information",
        "release info", "publication", "publication information", "publication info",
        "broadcast information", "broadcast" -> R.string.fandom_ib_group_production
        "chronology", "episode chronology", "chapter chronology", "navigation",
        "episode guide", "chapter guide" -> R.string.fandom_ib_group_chronology
        "image gallery", "gallery", "images" -> R.string.fandom_ib_group_gallery
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
        "state" -> R.string.fandom_ib_status
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
        "cv", "voice", "voices" -> R.string.fandom_ib_voice_actor
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
        "written by", "mangaka", "original creator" -> R.string.fandom_ib_creator
        "artist", "artists", "illustrator", "illustrators", "art", "art by",
        "character design", "character designer" -> R.string.fandom_ib_artist
        "director", "directors", "directed by" -> R.string.fandom_ib_director
        "studio", "studios", "animation studio", "produced by", "producer",
        "producers" -> R.string.fandom_ib_studio
        "publisher", "publishers", "published by", "magazine", "serialized in",
        "serialization", "imprint", "label" -> R.string.fandom_ib_publisher
        "network", "networks", "channel", "broadcaster", "aired on",
        "streaming" -> R.string.fandom_ib_network
        "genre", "genres", "genre(s)", "demographic", "demographics" -> R.string.fandom_ib_genre
        "release date", "released", "release", "air date", "aired", "airdate", "original run",
        "publication date", "published", "broadcast date", "premiere", "premiered",
        "date" -> R.string.fandom_ib_release_date
        "episodes", "episode count", "number of episodes", "no. of episodes",
        "total episodes" -> R.string.fandom_ib_episodes
        "chapters", "chapter count", "number of chapters", "no. of chapters",
        "total chapters" -> R.string.fandom_ib_chapters
        "volumes", "volume", "volume count", "number of volumes",
        "no. of volumes" -> R.string.fandom_ib_volumes
        "runtime", "running time", "duration", "length" -> R.string.fandom_ib_runtime
        "season", "seasons", "season(s)", "arc", "arcs", "saga", "sagas",
        "part" -> R.string.fandom_ib_season
        "previous", "previous episode", "previous chapter", "prev", "preceded by",
        "←" -> R.string.fandom_ib_previous
        "next", "next episode", "next chapter", "followed by", "→" -> R.string.fandom_ib_next
        "opening", "opening theme", "op", "opening song" -> R.string.fandom_ib_opening
        "ending", "ending theme", "ed", "ending song" -> R.string.fandom_ib_ending
        "theme song", "theme songs", "music", "soundtrack", "insert song",
        "song" -> R.string.fandom_ib_theme_song
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
        "homepage" -> R.string.fandom_ib_website
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
        else -> null
    }

    private fun normalize(raw: String): String =
        raw.trim()
            .lowercase(Locale.US)
            .replace(Regex("\\s+"), " ")
            .trimEnd(':', ' ')
}
