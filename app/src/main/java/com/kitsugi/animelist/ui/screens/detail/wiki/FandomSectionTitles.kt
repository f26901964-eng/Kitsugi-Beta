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
        else -> null
    }

    private fun normalize(raw: String): String =
        raw.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
}
