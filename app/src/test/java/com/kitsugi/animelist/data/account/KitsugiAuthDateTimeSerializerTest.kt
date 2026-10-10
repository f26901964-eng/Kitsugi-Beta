package com.kitsugi.animelist.data.account

import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class KitsugiAuthDateTimeSerializerTest {

    @Test
    fun supabaseAuthUserTimestampsCanBeDecoded() {
        val createdAt = "2026-10-10T00:00:00Z"
        val user = Json.decodeFromString<UserInfo>(
            """{"aud":"authenticated","id":"user-id","created_at":"$createdAt"}"""
        )

        assertEquals(Instant.parse(createdAt), user.createdAt)
    }
}
