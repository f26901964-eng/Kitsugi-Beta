package com.kitsugi.animelist.data.account

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Bağlı servis hesaplarının (AniList, MAL, Kitsu, Shikimori, Bangumi, Simkl) token'larını
 * uçtan uca şifreli olarak Kitsugi hesabına yedekler ve yeni cihazda geri yükler.
 *
 * Güvenlik modeli:
 *  • Buluta yalnızca ŞİFRELİ veri gider. Supabase bile token'ları okuyamaz.
 *  • Rastgele bir "kasa anahtarı" (vault key) üretilir. Bu anahtar, Kitsugi şifresinden
 *    PBKDF2 ile türetilen anahtarla sarılır (wrap) ve `vault_meta` satırında saklanır.
 *  • Token yedeği kasa anahtarıyla AES-256-GCM şifrelenir (`linked_accounts_vault` satırı).
 *  • Kitsugi şifresi hiçbir yerde saklanmaz.
 *  • Şifre unutulursa yedek çözülemez; servislere yeniden giriş gerekir.
 */
object LinkedAccountVault {

    private const val TAG = "LinkedAccountVault"

    // Token'ların bulunduğu mevcut SharedPreferences dosyası (diğer auth sınıflarıyla aynı)
    private const val TOKEN_PREFS = "MyWebViewPrefs"
    // Kasa anahtarının cihaz kopyası (oturum kapanınca silinir)
    private const val LOCAL_PREFS = "kitsugi_vault"
    private const val LOCAL_VAULT_KEY = "vault_key_b64"

    private const val ROW_META = "vault_meta"
    private const val ROW_PAYLOAD = "linked_accounts_vault"
    private const val TABLE = "user_data"

    private const val PBKDF2_ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    /** Yedeklenecek anahtar önekleri. Geçici alanlar (code_verifier, pending_redirect) HARİÇ. */
    private val ALLOWED_PREFIXES = listOf(
        "anilist_access_token",
        "mal_access_token", "mal_refresh_token", "mal_token_expires_at",
        "simkl_access_token",
        "kitsu_access_token", "kitsu_refresh_token", "kitsu_user_id", "kitsu_username",
        "shikimori_access_token", "shikimori_refresh_token", "shikimori_token_expires_at",
        "shikimori_user_id", "shikimori_username", "shikimori_client_id", "shikimori_client_secret",
        "bangumi_access_token", "bangumi_refresh_token", "bangumi_token_expires_at",
        "bangumi_token_type", "bangumi_token_scope", "bangumi_user_id", "bangumi_username",
        "bangumi_nickname", "bangumi_avatar_url", "bangumi_client_id", "bangumi_client_secret"
    )

    private fun isAllowed(key: String): Boolean = key in ALLOWED_PREFIXES

    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var pendingBackup: Job? = null

    // Listener bir alanda tutulmalı: SharedPreferences listener'ları zayıf referansla saklar,
    // yerel değişkende tutulursa çöp toplayıcı tarafından silinir.
    private val autoBackupListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || !isAllowed(key)) return@OnSharedPreferenceChangeListener
        pendingBackup?.cancel()
        pendingBackup = scope.launch {
            delay(3_000)
            appContext?.let { backupNow(it) }
        }
    }

    @Volatile
    private var appContext: Context? = null

    @Serializable
    private data class MetaRow(
        @SerialName("user_id") val userId: String? = null,
        val key: String,
        val value: JsonElement
    )

    @Serializable
    private data class CipherBlob(val salt: String = "", val iv: String, val ct: String)

    // ─────────────────────────── Kriptografi ───────────────────────────

    private fun deriveKek(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun encrypt(key: ByteArray, plain: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv to cipher.doFinal(plain)
    }

    private fun decrypt(key: ByteArray, iv: ByteArray, ct: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    // ─────────────────────────── Token anlık görüntüsü ───────────────────────────

    private fun tokenPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)

    /** Yedeklenecek token'ları tip bilgisiyle JSON dizisine çevirir. */
    private fun snapshot(context: Context): ByteArray {
        val all = tokenPrefs(context).all
        val entries = buildJsonArray {
            all.forEach { (k, v) ->
                if (!isAllowed(k) || v == null) return@forEach
                addJsonObject(k, v)
            }
        }
        return entries.toString().toByteArray(Charsets.UTF_8)
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addJsonObject(key: String, value: Any) {
        val (type, v) = when (value) {
            is String -> "s" to JsonPrimitive(value)
            is Long -> "l" to JsonPrimitive(value)
            is Int -> "i" to JsonPrimitive(value)
            is Boolean -> "b" to JsonPrimitive(value)
            is Float -> "f" to JsonPrimitive(value)
            else -> return
        }
        add(buildJsonObject {
            put("k", key)
            put("t", type)
            put("v", v)
        })
    }

    /** Yedek verisini token dosyasına geri yazar. Sadece izinli anahtarlara dokunur. */
    private fun restore(context: Context, plain: ByteArray) {
        val arr = json.parseToJsonElement(plain.toString(Charsets.UTF_8)).jsonArray
        val editor = tokenPrefs(context).edit()
        arr.forEach { el ->
            val obj = el.jsonObject
            val key = obj["k"]?.jsonPrimitive?.content ?: return@forEach
            if (!isAllowed(key)) return@forEach
            val v = obj["v"]?.jsonPrimitive ?: return@forEach
            when (obj["t"]?.jsonPrimitive?.content) {
                "s" -> editor.putString(key, v.content)
                "l" -> editor.putLong(key, v.content.toLong())
                "i" -> editor.putInt(key, v.content.toInt())
                "b" -> editor.putBoolean(key, v.content.toBoolean())
                "f" -> editor.putFloat(key, v.content.toFloat())
            }
        }
        // Yedekten gelen değerleri yazarken auto-backup dinleyicisi tetiklenir; bu zararsız (aynı içerik).
        editor.commit()
    }

    // ─────────────────────────── Yerel kasa anahtarı ───────────────────────────

    private fun localPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)

    private fun localVaultKey(context: Context): ByteArray? =
        localPrefs(context).getString(LOCAL_VAULT_KEY, null)?.let { unb64(it) }

    private fun saveLocalVaultKey(context: Context, key: ByteArray) {
        localPrefs(context).edit().putString(LOCAL_VAULT_KEY, b64(key)).apply()
    }

    fun hasLocalVault(context: Context): Boolean = localVaultKey(context) != null

    /** Oturum kapanınca yerel kasa anahtarını siler (token'lar cihazda kalır). */
    fun forgetLocalVault(context: Context) {
        localPrefs(context).edit().remove(LOCAL_VAULT_KEY).apply()
    }

    // ─────────────────────────── Bulut işlemleri ───────────────────────────

    private suspend fun fetchRow(uid: String, key: String): JsonElement? {
        val rows = KitsugiAccountClient.client.from(TABLE)
            .select(columns = Columns.list("user_id", "key", "value")) {
                filter {
                    eq("user_id", uid)
                    eq("key", key)
                }
            }
            .decodeList<MetaRow>()
        return rows.firstOrNull()?.value
    }

    private suspend fun upsertRow(uid: String, key: String, value: JsonElement) {
        KitsugiAccountClient.client.from(TABLE).upsert(MetaRow(userId = uid, key = key, value = value))
    }

    private fun userId(): String? =
        runCatching { KitsugiAccountClient.client.auth.currentUserOrNull()?.id }.getOrNull()

    /**
     * Giriş sonrası çağrılır (Kitsugi şifresi bu an elimizde).
     *  • Yedek varsa: kasa anahtarını açar, token'ları geri yükler.
     *  • Yoksa: yeni kasa oluşturur ve mevcut token'ları yedekler.
     */
    suspend fun onSignedIn(context: Context, password: String): Result<Unit> = runCatching {
        val uid = userId() ?: error("Oturum bulunamadı")
        val metaJson = fetchRow(uid, ROW_META)

        if (metaJson == null) {
            // İlk kez: yeni kasa anahtarı oluştur, şifreyle sar, yedek al
            val vaultKey = ByteArray(32).also { random.nextBytes(it) }
            val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
            val kek = deriveKek(password, salt)
            val (iv, ct) = encrypt(kek, vaultKey)
            upsertRow(uid, ROW_META, json.encodeToJsonElement(CipherBlob.serializer(), CipherBlob(b64(salt), b64(iv), b64(ct))))
            saveLocalVaultKey(context, vaultKey)
            backupNow(context)
        } else {
            val meta = json.decodeFromJsonElement(CipherBlob.serializer(), metaJson)
            val kek = deriveKek(password, unb64(meta.salt))
            val vaultKey = decrypt(kek, unb64(meta.iv), unb64(meta.ct))
            saveLocalVaultKey(context, vaultKey)

            val payloadJson = fetchRow(uid, ROW_PAYLOAD)
            if (payloadJson != null) {
                val blob = json.decodeFromJsonElement(CipherBlob.serializer(), payloadJson)
                val plain = decrypt(vaultKey, unb64(blob.iv), unb64(blob.ct))
                restore(context, plain)
                Log.i(TAG, "Bağlı hesap token'ları geri yüklendi")
            }
        }
    }

    /** Mevcut token'ları kasa anahtarıyla şifreleyip buluta yazar. Kasa yoksa hiçbir şey yapmaz. */
    suspend fun backupNow(context: Context): Result<Unit> = runCatching {
        val uid = userId() ?: return@runCatching
        val vaultKey = localVaultKey(context) ?: return@runCatching
        val (iv, ct) = encrypt(vaultKey, snapshot(context))
        upsertRow(uid, ROW_PAYLOAD, json.encodeToJsonElement(CipherBlob.serializer(), CipherBlob("", b64(iv), b64(ct))))
    }

    /**
     * Token dosyasında izinli bir anahtar değiştiğinde yedeği (çok sık yazmamak için 3 sn
     * gecikmeyle) günceller. Uygulama açılışında bir kez çağrılır.
     */
    fun startAutoBackup(context: Context) {
        val app = context.applicationContext
        appContext = app
        tokenPrefs(app).registerOnSharedPreferenceChangeListener(autoBackupListener)
    }

    /**
     * Şifre değiştiğinde kasa anahtarını YENİ şifreyle yeniden sarar. Veri yeniden şifrelenmez.
     */
    suspend fun rewrapWithNewPassword(context: Context, newPassword: String): Result<Unit> = runCatching {
        val uid = userId() ?: error("Oturum bulunamadı")
        val vaultKey = localVaultKey(context) ?: error("Yerel kasa anahtarı yok")
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val kek = deriveKek(newPassword, salt)
        val (iv, ct) = encrypt(kek, vaultKey)
        upsertRow(uid, ROW_META, json.encodeToJsonElement(CipherBlob.serializer(), CipherBlob(b64(salt), b64(iv), b64(ct))))
    }
}
