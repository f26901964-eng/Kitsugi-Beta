package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FandomRelationClassifierTest {

    @Test
    fun `infobox and section labels classify characters people crew and music`() {
        assertEquals(FandomRelationType.CHARACTER, FandomRelationClassifier.forInfoboxLabel("登場人物"))
        assertEquals(FandomRelationType.VOICE_ACTOR, FandomRelationClassifier.forInfoboxLabel("Japanese voice actor"))
        assertEquals("ja", FandomRelationClassifier.languageHint("Japanese voice actor"))
        assertEquals("en", FandomRelationClassifier.languageHint("English VA"))
        assertEquals(FandomRelationType.CAST, FandomRelationClassifier.forInfoboxLabel("Main cast"))
        assertEquals(FandomRelationType.CREW, FandomRelationClassifier.forInfoboxLabel("Production staff"))
        assertEquals(FandomRelationType.MUSIC, FandomRelationClassifier.forInfoboxLabel("Theme song composer"))
        assertEquals(FandomRelationType.VOICE_ACTOR, FandomRelationClassifier.forSection("Seiyuu"))
        assertEquals(FandomRelationType.VOICE_ACTOR, FandomRelationClassifier.forSection("Actores de voz"))
        assertEquals("fr", FandomRelationClassifier.languageHint("Voix françaises"))
        assertEquals(FandomRelationType.CHARACTER, FandomRelationClassifier.forSection("Personnages"))
        assertEquals(FandomRelationType.CREW, FandomRelationClassifier.forSection("Equipo de producción"))
        assertEquals(FandomRelationType.CHARACTER, FandomRelationClassifier.forSection("Characters"))
        assertNull(FandomRelationClassifier.forSection("Trivia"))
    }
}
