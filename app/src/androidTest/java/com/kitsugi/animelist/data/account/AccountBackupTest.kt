package com.kitsugi.animelist.data.account

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsugi.animelist.data.settings.SettingsDataStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountBackupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun cleanup() { LocalVaultKeyStore.clear(context) }

    @Test fun ktorEngineAndTimeoutHaveCompatibleAbi() {
        val client = HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 10_000 } }
        client.close()
    }

    @Test fun localKeyIsEncryptedAndBoundToOwner() {
        val key = ByteArray(32) { it.toByte() }
        LocalVaultKeyStore.save(context, "owner-a", key)
        assertArrayEquals(key, LocalVaultKeyStore.load(context, "owner-a"))
        assertNull(LocalVaultKeyStore.load(context, "owner-b"))
        assertFalse(context.getSharedPreferences("kitsugi_vault", 0).contains("vault_key_b64"))
        LocalVaultKeyStore.clear(context)
        assertNull(LocalVaultKeyStore.load(context, "owner-a"))
    }

    @Test fun legacyUnboundKeyRequiresNewLogin() {
        context.getSharedPreferences("kitsugi_vault", 0).edit()
            .putString("vault_key_b64", "legacy").commit()
        assertNull(LocalVaultKeyStore.load(context, "owner-a"))
        assertFalse(context.getSharedPreferences("kitsugi_vault", 0).contains("vault_key_b64"))
    }

    @Test fun settingsRestoreRejectsSecretsAndWrongTypes() = runBlocking {
        val settings = SettingsDataStore(context)
        val original = settings.cloudSettingsFlow.first()
        settings.restoreCloudSettings(mapOf("selected_theme_id" to "mint", "subtitle_bold" to true))
        settings.restoreCloudSettings(mapOf("selected_theme_id" to 123, "tmdb_user_api_key" to "not-a-real-key"))
        val snapshot = settings.cloudSettingsFlow.first()
        assertEquals("mint", snapshot["selected_theme_id"])
        assertEquals(true, snapshot["subtitle_bold"])
        assertFalse(snapshot.containsKey("tmdb_user_api_key"))
        settings.restoreCloudSettings(original)
    }
}
