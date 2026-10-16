package com.kitsugi.animelist.ui.screens.detail.wiki

import com.kitsugi.animelist.R
import java.util.Locale

/**
 * İngilizce Fandom bölüm başlıklarını arayüz dilindeki karşılığına eşler (saf fonksiyon).
 *
 * Wiki'ler başlıkları kendi alışkanlıklarıyla yazar ("Role in the Movie", "Appearances in
 * Other Media"…); eşlenemeyen başlıklar arayüzde özgün biçimiyle kalır. Liste, cihaz
 * ekranlarında görülen gerçek başlıklardan derlenir; yeni bir kalıp görüldüğünde buraya eklenir.
 */
object FandomSectionTitles {

    /** @return [raw] başlığı için string kaynağı; bilinmiyorsa null. */
    fun resId(raw: String): Int? = when (normalize(raw)) {
        "appearance", "appearances and design" -> R.string.fandom_section_appearance
        "personality", "personality and traits", "traits", "character traits",
        "personality traits" -> R.string.fandom_section_personality
        "history" -> R.string.fandom_section_history
        "background", "backstory", "early life", "origin story",
        "history and background" -> R.string.fandom_section_background
        "abilities", "abilities and powers", "powers and abilities", "powers & abilities",
        "abilities & powers", "skills", "skills and abilities", "powers", "techniques",
        "techniques and abilities", "powers and skills" -> R.string.fandom_section_abilities
        "relationships", "relationship", "personal relationships", "romance",
        "love interests", "love interest", "romantic relationships" -> R.string.fandom_section_relationships
        "trivia", "trivia and facts" -> R.string.fandom_section_trivia
        "quotes" -> R.string.fandom_section_quotes
        "plot", "story", "story and history", "premise", "story premise",
        "narrative" -> R.string.fandom_section_plot
        "synopsis" -> R.string.fandom_section_synopsis
        "battles & events", "battles and events", "battles", "major battles" -> R.string.fandom_section_battles
        "equipment", "weapons and equipment" -> R.string.fandom_section_equipment
        "etymology", "name origin", "origin of name", "name and origin", "meaning of the name",
        "name meaning", "name etymology", "title meaning", "name origin and meaning" -> R.string.fandom_section_etymology
        "biography" -> R.string.fandom_section_biography
        "overview", "about", "profile", "summary", "introduction", "intro",
        "about the series" -> R.string.fandom_section_overview
        "role in the movie", "role in the film", "movie role", "film role" -> R.string.fandom_section_role_movie
        "role in the series", "role in the tv series", "role in the anime",
        "role in the manga", "role in the show", "series role" -> R.string.fandom_section_role_series
        "appearances in other media", "other media appearances", "in other media",
        "appearances in other media and adaptations", "other media",
        "appearances in other works", "collaborations", "crossovers",
        "collaboration events", "crossover events", "guest appearances",
        "tie-ins", "tie-in" -> R.string.fandom_section_other_media
        "appearances", "media appearances", "anime appearances",
        "manga appearances", "list of appearances" -> R.string.fandom_section_appearances
        "legacy", "influence and legacy", "impact", "cultural impact", "influence",
        "legacy and influence", "influence and impact", "cultural influence",
        "impact and legacy" -> R.string.fandom_section_legacy
        "creation", "conception", "creation and conception", "design",
        "design and conception", "development", "origin and creation",
        "concept", "initial concept", "conception and creation",
        "origin and development", "writing and creation" -> R.string.fandom_section_creation
        "people killed", "kills", "kill list", "victims", "known victims", "notable kills",
        "titans killed", "list of kills", "killed" -> R.string.fandom_section_people_killed
        "gallery", "image gallery", "images", "pictures", "screenshots" -> R.string.fandom_section_gallery
        "references", "reference", "citations", "sources", "footnotes",
        "notes and references" -> R.string.fandom_section_references
        "see also", "related articles", "related pages", "related" -> R.string.fandom_section_see_also
        "navigation", "site navigation", "external links", "links" -> R.string.fandom_section_navigation
        "notes", "note", "author's notes" -> R.string.fandom_section_notes
        "family", "family tree", "family members" -> R.string.fandom_section_family
        "voice actors", "voice actor", "voice acting", "cast", "voice cast",
        "voiced by" -> R.string.fandom_section_voice_actors
        "in the anime", "anime", "anime version", "anime adaptation",
        "in the tv anime" -> R.string.fandom_section_in_anime
        "in the manga", "manga", "manga version", "in the comic",
        "comic version" -> R.string.fandom_section_in_manga
        "in the light novel", "light novel", "in the novel", "novel",
        "web novel", "in the web novel", "web novel version",
        "light novel version" -> R.string.fandom_section_in_novel
        "in the games", "in the game", "video games", "games", "game appearances",
        "in video games", "tabletop games", "board games", "card games",
        "trading card game", "tcg", "tabletop", "board game", "card game",
        "pachinko", "pachislot", "arcade", "arcade games", "mobile games",
        "mobile game" -> R.string.fandom_section_in_games
        "anime and manga differences", "manga and anime differences",
        "differences between anime and manga", "differences from the manga",
        "differences from the anime", "adaptation differences", "anime/manga differences",
        "changes in the anime" -> R.string.fandom_section_differences
        "fights", "fights and events", "battles and fights", "matches", "duels",
        "notable fights" -> R.string.fandom_section_battles
        "statistics", "stats", "parameters", "attributes",
        "power stats" -> R.string.fandom_section_statistics
        "weapons", "weapons and gear", "gear", "arsenal", "tools",
        "equipment and weapons" -> R.string.fandom_section_equipment
        "goals", "motivation", "motivations", "goal", "objectives",
        "ambitions" -> R.string.fandom_section_goals
        "reception", "critical reception", "popularity", "reception and popularity",
        "popularity polls", "popularity poll", "critical response",
        "critical responses", "response", "reaction", "reactions",
        "reception and response", "reviews", "review", "critical reviews",
        "audience reception" -> R.string.fandom_section_reception
        "merchandise", "merchandising", "figures", "products" -> R.string.fandom_section_merchandise
        "music", "songs", "theme songs", "character songs", "soundtrack",
        "discography", "soundtracks", "ost", "original soundtrack", "score",
        "music production", "theme music", "albums", "album", "singles" -> R.string.fandom_section_music
        "names in other languages", "in other languages", "name in other languages",
        "international names", "foreign names", "names" -> R.string.fandom_section_other_languages
        "achievements", "accomplishments", "feats", "records",
        "awards" -> R.string.fandom_section_achievements
        "accolades", "accolade", "awards and nominations", "awards & nominations",
        "awards and accolades", "honors", "honours", "prizes", "award list",
        "list of awards", "award nominations" -> R.string.fandom_section_accolades
        "videos", "video", "trailers", "clips" -> R.string.fandom_section_videos
        "episodes", "episode list", "list of episodes", "episode guide" -> R.string.fandom_section_episodes
        "chapters", "chapter list", "list of chapters", "volumes",
        "chapter guide", "manga chapters", "chapters and volumes",
        "volumes and chapters", "manga volumes", "list of manga chapters",
        "list of volumes", "tankōbon", "tankobon", "chapter listings",
        "volume listings" -> R.string.fandom_section_chapters
        "characters", "main characters", "character list", "list of characters",
        "cast of characters", "protagonists", "protagonist", "heroes", "hero",
        "main cast", "principal characters" -> R.string.fandom_section_characters
        "plot overview", "plot summary", "story arc", "story arcs", "story overview",
        "arcs" -> R.string.fandom_section_plot
        "setting", "world", "universe", "world setting", "locations",
        "places", "worldbuilding", "world building", "lore", "mythology",
        "mythos", "cosmology" -> R.string.fandom_section_setting
        "terminology", "terms", "glossary", "concepts" -> R.string.fandom_section_terminology
        "production", "production history", "production notes", "staff",
        "production staff", "pre-production", "preproduction",
        "post-production", "postproduction", "production process",
        "behind the scenes", "making of", "making" -> R.string.fandom_section_production
        "release", "release history", "releases", "broadcast", "publication",
        "publication history", "release dates", "broadcast history",
        "airing history", "television broadcast", "tv broadcast",
        "broadcasting", "airing", "telecast", "streaming release" -> R.string.fandom_section_release
        "media", "adaptations", "other adaptations",
        "related media" -> R.string.fandom_section_media
        "current status", "status", "whereabouts" -> R.string.fandom_section_status
        "death", "demise", "fate" -> R.string.fandom_section_death
        "past", "childhood", "origins", "origin", "early years" -> R.string.fandom_section_background
        "beliefs", "philosophy", "ideology", "views" -> R.string.fandom_section_beliefs
        "titan form", "titan forms", "forms", "transformations", "transformation",
        "alternate forms", "modes" -> R.string.fandom_section_forms
        "weaknesses", "limitations", "weakness" -> R.string.fandom_section_weaknesses
        "quotes and dialogue", "notable quotes", "memorable quotes",
        "dialogue" -> R.string.fandom_section_quotes
        "chronology", "timeline" -> R.string.fandom_section_chronology
        "live-action series", "live action series", "live-action drama",
        "live action drama", "live-action television series", "tv drama",
        "drama series", "korean drama", "kdrama", "jdrama", "drama",
        "dramas" -> R.string.fandom_section_live_action_series
        "live-action film", "live action film", "live-action films",
        "live action films", "live-action movie", "live action movies",
        "live-action adaptation", "live action adaptation",
        "live-action adaptations", "live action", "live-action" -> R.string.fandom_section_live_action_film
        "continuation", "continuations", "sequel", "sequels", "follow-up",
        "follow up", "follow-ups", "followups", "successor",
        "next generation" -> R.string.fandom_section_continuation
        "spin-off", "spin-offs", "spin off", "spin offs", "spinoff", "spinoffs",
        "side story", "side stories", "gaiden" -> R.string.fandom_section_spinoff
        "prequel", "prequels", "prequels and sequels" -> R.string.fandom_section_prequel
        "remake", "remakes", "reboot", "reboots", "revival", "reimagining",
        "new version" -> R.string.fandom_section_remake
        "edition", "editions" -> R.string.fandom_section_editions
        "first edition" -> R.string.fandom_section_edition_first
        "second edition" -> R.string.fandom_section_edition_second
        "third edition" -> R.string.fandom_section_edition_third
        "fourth edition" -> R.string.fandom_section_edition_fourth
        "fifth edition" -> R.string.fandom_section_edition_fifth
        "sixth edition" -> R.string.fandom_section_edition_sixth
        "seventh edition" -> R.string.fandom_section_edition_seventh
        "eighth edition" -> R.string.fandom_section_edition_eighth
        "ninth edition" -> R.string.fandom_section_edition_ninth
        "tenth edition" -> R.string.fandom_section_edition_tenth
        "revised edition", "revised and expanded edition" -> R.string.fandom_section_edition_revised
        "new edition", "newly revised edition" -> R.string.fandom_section_edition_new
        "special edition", "special editions" -> R.string.fandom_section_edition_special
        "deluxe edition" -> R.string.fandom_section_edition_deluxe
        "anniversary edition", "10th anniversary edition",
        "20th anniversary edition" -> R.string.fandom_section_edition_anniversary
        "omnibus edition", "omnibus" -> R.string.fandom_section_edition_omnibus
        "complete edition", "complete collection" -> R.string.fandom_section_edition_complete
        "collector's edition", "collector edition" -> R.string.fandom_section_edition_collector
        "other characters", "others", "other cast", "remaining characters",
        "other notable characters" -> R.string.fandom_section_other_characters
        "supporting characters", "supporting cast", "secondary characters",
        "minor characters", "recurring characters",
        "guest characters" -> R.string.fandom_section_supporting_characters
        "antagonists", "villains", "villain", "antagonist", "enemies", "foes",
        "adversaries", "rivals" -> R.string.fandom_section_antagonists
        "organizations", "organisations", "groups", "factions", "guilds",
        "clans", "teams", "companies", "corporations", "agencies",
        "associations", "societies", "cults", "sects" -> R.string.fandom_section_organizations
        "themes", "theme", "motifs", "motif", "symbolism", "analysis",
        "interpretation", "meaning", "messages", "message",
        "allegory" -> R.string.fandom_section_themes
        "home media", "home video", "dvd", "dvds", "blu-ray", "blu ray",
        "blu-ray releases", "vhs", "digital release", "digital releases",
        "distribution" -> R.string.fandom_section_home_media
        "casting", "cast selection", "voice casting", "auditions" -> R.string.fandom_section_casting
        "filming", "principal photography", "shooting",
        "location shooting" -> R.string.fandom_section_filming
        "visual effects", "special effects", "sfx", "vfx", "cgi" -> R.string.fandom_section_vfx
        "screenplay", "script", "writing", "series composition",
        "screenplay and writing" -> R.string.fandom_section_screenplay
        "webtoon", "webtoons", "webcomic", "webcomics", "manhwa",
        "manhua" -> R.string.fandom_section_webtoon
        "in the webtoon", "webtoon version", "in the webcomic",
        "webcomic version", "in the manhwa", "manhwa version" -> R.string.fandom_section_in_webtoon
        "in the film", "in the movie", "in the films", "in the movies",
        "film version", "movie version", "in film", "in movies" -> R.string.fandom_section_in_film
        "further reading", "bibliography", "reading list",
        "recommended reading", "related literature" -> R.string.fandom_section_further_reading
        "events", "event", "concerts", "concert", "exhibitions", "exhibition",
        "expos", "expo", "festivals", "festival", "stage events",
        "campaigns", "campaign" -> R.string.fandom_section_events
        "translation", "translations", "localization", "localisation",
        "english release", "english adaptation" -> R.string.fandom_section_translation
        "controversies", "controversy", "criticism", "criticisms", "disputes",
        "dispute", "incidents", "incident", "backlash", "scandals",
        "scandal", "controversies and criticisms" -> R.string.fandom_section_controversies
        "fandom", "fan community", "fans", "fanbase", "fan base", "community",
        "fandom and community", "cult following",
        "fan following" -> R.string.fandom_section_fandom
        "inspiration", "inspirations", "influences", "sources of inspiration",
        "inspired by", "real-life inspiration",
        "real life inspiration" -> R.string.fandom_section_inspiration
        "cultural references", "cultural reference",
        "references in other media", "pop culture references", "allusions",
        "allusion", "homage", "homages", "parodies", "parody" -> R.string.fandom_section_cultural_ref
        "promotion", "marketing", "promotional activities",
        "promotional events", "advertising", "publicity",
        "promotion and marketing" -> R.string.fandom_section_promotion
        "box office", "box-office", "box office performance", "earnings",
        "gross", "ticket sales" -> R.string.fandom_section_box_office
        "viewership", "ratings", "audience ratings", "tv ratings",
        "nielsen ratings", "audience share", "viewing figures" -> R.string.fandom_section_viewership
        else -> null
    }

    private fun normalize(raw: String): String =
        raw.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
}
