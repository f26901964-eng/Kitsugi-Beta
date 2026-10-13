package com.kitsugi.animelist.data.fandom

/**
 * Maps the highly variable labels used by Fandom infoboxes/section headings to stable
 * relationship types. Unknown labels remain visible as RELATED rather than being discarded.
 */
internal object FandomRelationClassifier {

    fun forInfoboxLabel(label: String): FandomRelationType = classify(label)

    fun forSection(title: String): FandomRelationType? =
        classify(title).takeUnless { it == FandomRelationType.RELATED }

    fun languageHint(label: String): String? {
        val raw = label.lowercase()
        val normalized = " ${FandomNameVariants.normalizeForCompare(label)} "
        return when {
            " japanese " in normalized || " nihongo " in normalized || "日本" in raw || word(normalized, "jp") -> "ja"
            " korean " in normalized || "한국" in raw || word(normalized, "kr") -> "ko"
            " chinese " in normalized || "中文" in raw || "配音" in raw || word(normalized, "cn") -> "zh"
            " english " in normalized || " ingles " in normalized || " inglese " in normalized || word(normalized, "en") -> "en"
            " french " in normalized || " francais " in normalized || " francaise " in normalized ||
                " francaises " in normalized || word(normalized, "fr") -> "fr"
            " german " in normalized || " deutsch " in normalized || word(normalized, "de") -> "de"
            " spanish " in normalized || " espanol " in normalized || word(normalized, "es") -> "es"
            " italian " in normalized || " italiano " in normalized || word(normalized, "it") -> "it"
            " portuguese " in normalized || " portugues " in normalized || word(normalized, "pt") -> "pt"
            " russian " in normalized || "русск" in raw || word(normalized, "ru") -> "ru"
            " turkish " in normalized || " turkce " in normalized || " turk " in normalized || word(normalized, "tr") -> "tr"
            " arabic " in normalized || "العربية" in raw || word(normalized, "ar") -> "ar"
            else -> null
        }
    }

    private fun classify(label: String): FandomRelationType {
        val raw = label.lowercase()
        val text = " ${FandomNameVariants.normalizeForCompare(label)} "
        return when {
            containsAny(raw, "声優", "聲優", "성우", "配音", "озвуч") ||
                phrase(
                    text,
                    "seiyuu", "voice actor", "voice actress", "voice cast", "voiced by", "voice of", "voices",
                    "actor de voz", "actores de voz", "actriz de voz", "actrices de voz", "reparto de voces",
                    "acteur vocal", "acteurs vocaux", "actrice vocale", "voix", "doublage", "comediens de doublage", "comedien de doublage",
                    "doblaje", "doppiaggio", "doubleur", "doubleurs", "synchronsprecher", "synchronsprecherin", "synchronsprecherinnen",
                    "doppiatore", "doppiatori", "doppiatrice", "dublagem", "dublador", "dubladores",
                    "seslendirme sanatcisi", "seslendirme kadrosu", "seslendiren", "seslendirenler", "seslendirmen", "seiyuu", "cv", "va"
                ) -> FandomRelationType.VOICE_ACTOR

            containsAny(raw, "登場人物", "キャラクター", "등장인물", "角色", "персонаж", "герои") ||
                phrase(
                    text,
                    "character", "characters", "main character", "protagonist", "protagonists",
                    "personajes", "personaje", "personnages", "personnage", "figuren", "hauptfiguren",
                    "personaggi", "personagem", "personagens", "personagens principais", "principais personagens",
                    "karakter", "karakterler", "karakter kadrosu"
                ) -> FandomRelationType.CHARACTER

            containsAny(raw, "出演者", "배우", "キャスト") ||
                phrase(
                    text,
                    "cast", "casting", "actor", "actors", "actress", "actresses", "performer", "performers", "starring",
                    "reparto", "elenco", "actores", "actrices", "acteur", "acteurs", "actrice", "schauspieler",
                    "besetzung", "attori", "attrici", "cast principal", "oyuncu", "oyuncular"
                ) -> FandomRelationType.CAST

            containsAny(raw, "制作スタッフ", "スタッフ", "制作陣", "제작진") ||
                phrase(
                    text,
                    "staff", "crew", "production", "director", "producer", "writer", "author", "creator",
                    "studio", "personnel", "team", "teams", "affiliation", "affiliations", "unit", "group", "groups",
                    "equipo", "equipo de produccion", "personal de produccion", "equipe", "equipe technique", "production team",
                    "mitarbeiter", "produktion", "staff tecnico", "produzione", "equipa", "producao", "organizacao", "ekip"
                ) -> FandomRelationType.CREW

            containsAny(raw, "作曲", "音楽", "음악", "音乐", "музык") ||
                phrase(text, "music", "musica", "musique", "musik", "muzik", "soundtrack", "banda sonora", "colonna sonora", "composer", "theme song", "opening", "ending") ->
                FandomRelationType.MUSIC

            else -> FandomRelationType.RELATED
        }
    }

    private fun phrase(text: String, vararg candidates: String): Boolean =
        candidates.any { " $it " in text }

    private fun word(text: String, candidate: String): Boolean = " $candidate " in text

    private fun containsAny(text: String, vararg candidates: String): Boolean =
        candidates.any { text.contains(it) }
}
