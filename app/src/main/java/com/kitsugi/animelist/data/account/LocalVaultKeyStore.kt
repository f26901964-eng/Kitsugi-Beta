package com.kitsugi.animelist.data.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.*

/** Device-bound encrypted cache. The password and wrapping key are never persisted. */
internal object LocalVaultKeyStore {
    private const val ALIAS = "kitsugi_vault_device_key"
    private fun prefs(context: Context) = context.getSharedPreferences("kitsugi_vault", Context.MODE_PRIVATE)
    private fun deviceKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(context: Context, uid: String, key: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey())
        cipher.updateAAD(uid.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(key)
        check(prefs(context).edit().clear().putString("owner", uid)
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("encrypted_key", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
    fun load(context: Context, uid: String): ByteArray? {
        val prefs = prefs(context)
        // Legacy plaintext keys have no owner binding: require password login, never silently migrate.
        if (prefs.contains("vault_key_b64")) { clear(context); return null }
        if (prefs.getString("owner", null) != uid) return null
        val encrypted = prefs.getString("encrypted_key", null) ?: return null
        val iv = prefs.getString("iv", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        cipher.updateAAD(uid.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
    }
    data class Baseline(val local: Map<String, String>, val remote: Map<String, String>)

    fun baseline(context: Context, uid: String): Baseline? {
        val prefs = prefs(context)
        if (prefs.getString("owner", null) != uid) return null
        val raw = prefs.getString("baseline", null) ?: return null
        val obj = Json.parseToJsonElement(raw).jsonObject
        fun read(name: String) = obj.getValue(name).jsonObject.mapValues { it.value.jsonPrimitive.content }
        return Baseline(read("local"), read("remote"))
    }

    fun saveBaseline(context: Context, uid: String, local: Map<String, String>, remote: Map<String, String>) {
        check(prefs(context).getString("owner", null) == uid)
        // Only SHA-256 fingerprints, never plaintext tokens. One atomic commit for both views.
        val value = buildJsonObject {
            put("local", JsonObject(local.mapValues { JsonPrimitive(it.value) }))
            put("remote", JsonObject(remote.mapValues { JsonPrimitive(it.value) }))
        }
        check(prefs(context).edit().putString("baseline", value.toString())
            .putLong("last_success", System.currentTimeMillis()).commit())
    }

    fun owner(context: Context): String? = prefs(context).getString("owner", null)

    fun lastSuccess(context: Context): Long = prefs(context).getLong("last_success", 0)

    fun clear(context: Context) { check(prefs(context).edit().clear().commit()) }
}
