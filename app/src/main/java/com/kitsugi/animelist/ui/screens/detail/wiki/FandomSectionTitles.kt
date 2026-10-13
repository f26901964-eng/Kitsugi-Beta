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
        "personality", "personality and traits" -> R.string.fandom_section_personality
        "history" -> R.string.fandom_section_history
        "background", "backstory", "early life" -> R.string.fandom_section_background
        "abilities", "abilities and powers", "powers and abilities", "powers & abilities",
        "abilities & powers", "skills", "skills and abilities", "powers", "techniques",
        "techniques and abilities", "powers and skills" -> R.string.fandom_section_abilities
        "relationships", "relationship", "personal relationships" -> R.string.fandom_section_relationships
        "trivia", "trivia and facts" -> R.string.fandom_section_trivia
        "quotes" -> R.string.fandom_section_quotes
        "plot", "story", "story and history" -> R.string.fandom_section_plot
        "synopsis" -> R.string.fandom_section_synopsis
        "battles & events", "battles and events", "battles", "major battles" -> R.string.fandom_section_battles
        "equipment", "weapons and equipment" -> R.string.fandom_section_equipment
        "etymology", "name origin", "origin of name", "name and origin", "meaning of the name",
        "name meaning" -> R.string.fandom_section_etymology
        "biography" -> R.string.fandom_section_biography
        "overview", "about", "profile", "summary" -> R.string.fandom_section_overview
        "role in the movie", "role in the film", "movie role", "film role" -> R.string.fandom_section_role_movie
        "role in the series", "role in the tv series", "role in the anime",
        "role in the manga", "role in the show", "series role" -> R.string.fandom_section_role_series
        "appearances in other media", "other media appearances", "in other media",
        "appearances in other media and adaptations", "other media",
        "appearances in other works" -> R.string.fandom_section_other_media
        "appearances", "media appearances", "anime appearances",
        "manga appearances", "list of appearances" -> R.string.fandom_section_appearances
        "legacy", "influence and legacy" -> R.string.fandom_section_legacy
        "creation", "conception", "creation and conception", "design",
        "design and conception", "development", "origin and creation" -> R.string.fandom_section_creation
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
        "in the anime", "anime", "anime version", "anime adaptation" -> R.string.fandom_section_in_anime
        "in the manga", "manga", "manga version" -> R.string.fandom_section_in_manga
        "in the light novel", "light novel", "in the novel", "novel",
        "web novel" -> R.string.fandom_section_in_novel
        "in the games", "in the game", "video games", "games", "game appearances",
        "in video games" -> R.string.fandom_section_in_games
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
        "popularity polls", "popularity poll" -> R.string.fandom_section_reception
        "merchandise", "merchandising", "figures", "products" -> R.string.fandom_section_merchandise
        "music", "songs", "theme songs", "character songs", "soundtrack",
        "discography" -> R.string.fandom_section_music
        "names in other languages", "in other languages", "name in other languages",
        "international names", "foreign names", "names" -> R.string.fandom_section_other_languages
        "achievements", "accomplishments", "feats", "records",
        "awards" -> R.string.fandom_section_achievements
        "videos", "video", "trailers", "clips" -> R.string.fandom_section_videos
        "episodes", "episode list", "list of episodes", "episode guide" -> R.string.fandom_section_episodes
        "chapters", "chapter list", "list of chapters", "volumes",
        "chapter guide" -> R.string.fandom_section_chapters
        "characters", "main characters", "character list", "list of characters",
        "cast of characters" -> R.string.fandom_section_characters
        "plot overview", "plot summary", "story arc", "story arcs", "story overview",
        "arcs" -> R.string.fandom_section_plot
        "setting", "world", "universe", "world setting", "locations",
        "places" -> R.string.fandom_section_setting
        "terminology", "terms", "glossary", "concepts" -> R.string.fandom_section_terminology
        "production", "production history", "production notes", "staff",
        "production staff" -> R.string.fandom_section_production
        "release", "release history", "releases", "broadcast", "publication",
        "publication history", "release dates" -> R.string.fandom_section_release
        "media", "adaptations", "other adaptations", "spin-offs", "spin offs",
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
        else -> null
    }

    private fun normalize(raw: String): String =
        raw.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
}
