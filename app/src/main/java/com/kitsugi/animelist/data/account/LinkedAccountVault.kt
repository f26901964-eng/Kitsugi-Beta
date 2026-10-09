package com.kitsugi.animelist.data.account

import com.kitsugi.animelist.data.account.VaultCrypto.CipherBlob
import com.kitsugi.animelist.data.account.VaultCrypto.PasswordWrap
import com.kitsugi.animelist.data.account.VaultCrypto.asBlob
import com.kitsugi.animelist.data.account.VaultCrypto.wrap
import com.kitsugi.animelist.data.account.VaultCrypto.unwrap
import com.kitsugi.animelist.data.account.VaultCrypto.encrypt
import com.kitsugi.animelist.data.account.VaultCrypto.decrypt
import com.kitsugi.animelist.data.account.VaultCrypto.b64
import com.kitsugi.animelist.data.account.VaultCrypto.unb64
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.SecureRandom

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

    private const val ROW_META = "vault_meta"
    private const val ROW_PAYLOAD = "linked_accounts_vault"
    private const val TABLE = "user_data"


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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var settingsObserver: Job? = null
    private val mutex = Mutex()
    @Volatile private var restoring = false
    private val _status = MutableStateFlow("Yedek durumu henüz doğrulanmadı.")
    val status = _status.asStateFlow()
    private val _conflicts = MutableStateFlow<Set<String>>(emptySet())
    val conflicts = _conflicts.asStateFlow()
    private var conflictRemote: JsonElement? = null

    /** Auth must not replace the singleton client's JWT in the middle of an upload for another owner. */
    suspend fun <T> changeSession(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { action() }
    }

    private suspend fun operation(block: suspend () -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                block()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = when (e) {
                    is VaultConflictException, is VaultLockedException -> e.message.orEmpty()
                    else -> "İşlem tamamlanamadı. Açık kasa için bekleyen yedek işi yeniden denenir. Kasa açılmadıysa tekrar giriş yap; sunucu güncellemesini de kontrol et."
                }
                Result.failure(e)
            }
        }
    }

    private fun scheduleBackup() {
        if (restoring) return
        val uid = userId() ?: return
        appContext?.let { VaultBackupWorker.enqueue(it, uid) }
    }

    // Listener bir alanda tutulmalı: SharedPreferences listener'ları zayıf referansla saklar,
    // yerel değişkende tutulursa çöp toplayıcı tarafından silinir.
    private val autoBackupListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && !isAllowed(key)) return@OnSharedPreferenceChangeListener
        scheduleBackup()
    }

    @Volatile
    private var appContext: Context? = null

    @Serializable
    private data class MetaRow(
        @SerialName("user_id") val userId: String? = null,
        val key: String,
        val value: JsonElement
    )

    // ─────────────────────────── Token anlık görüntüsü ───────────────────────────

    private fun tokenPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)

    /** Yedeklenecek token'ları tip bilgisiyle JSON dizisine çevirir. */
    private suspend fun snapshot(context: Context): ByteArray {
        val all = tokenPrefs(context).all
        val settings = SettingsDataStore(context).cloudSettingsFlow.first()
        val entries = buildJsonArray {
            all.forEach { (k, v) ->
                if (!isAllowed(k) || v == null) return@forEach
                addJsonObject(k, v)
            }
            settings.forEach { (k, v) -> addJsonObject(k, v, "settings") }
        }
        return entries.toString().toByteArray(Charsets.UTF_8)
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addJsonObject(key: String, value: Any, section: String = "tokens") {
        val (type, v) = when (value) {
            is String -> "s" to JsonPrimitive(value)
            is Long -> "l" to JsonPrimitive(value)
            is Int -> "i" to JsonPrimitive(value)
            is Boolean -> "b" to JsonPrimitive(value)
            is Float -> "f" to JsonPrimitive(value)
            else -> return
        }
        add(buildJsonObject {
            put("section", section)
            put("k", key)
            put("t", type)
            put("v", v)
        })
    }

    /** Yedek verisini token dosyasına geri yazar. Sadece izinli anahtarlara dokunur. */
    private suspend fun restore(context: Context, plain: ByteArray) {
        val arr = json.parseToJsonElement(plain.toString(Charsets.UTF_8)).jsonArray
        val editor = tokenPrefs(context).edit()
        ALLOWED_PREFIXES.forEach { editor.remove(it) }
        val settings = mutableMapOf<String, Any>()
        arr.forEach { el ->
            val obj = el.jsonObject
            val key = obj["k"]?.jsonPrimitive?.content ?: return@forEach
            if (obj["section"]?.jsonPrimitive?.content == "settings") {
                val value = obj["v"]?.jsonPrimitive ?: return@forEach
                settings[key] = when (obj["t"]?.jsonPrimitive?.content) {
                    "s" -> value.content
                    "i" -> value.content.toInt()
                    "b" -> value.content.toBooleanStrict()
                    else -> return@forEach
                }
                return@forEach
            }
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
        // Restore runs under the vault mutex; no upload may race a partial restore.
        check(editor.commit())
        SettingsDataStore(context).restoreCloudSettings(settings)
    }

    // ─────────────────────────── Yerel kasa anahtarı ───────────────────────────

    private fun localVaultKey(context: Context): ByteArray? =
        userId()?.let { LocalVaultKeyStore.load(context, it) }

    fun hasLocalVault(context: Context): Boolean = runCatching {
        val uid = userId() ?: return@runCatching false
        localVaultKey(context) != null && LocalVaultKeyStore.baseline(context, uid) != null
    }.getOrDefault(false)

    suspend fun forgetLocalVault(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            LocalVaultKeyStore.owner(context)?.let { VaultBackupWorker.cancel(context, it) }
            LocalVaultKeyStore.clear(context)
            _conflicts.value = emptySet()
            conflictRemote = null
            _status.value = "Kasa kilitli. Giriş yaparak açabilirsin."
        }
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

    /** Atomic comparison is executed on the server; a client-side read/check is not sufficient. */
    private suspend fun compareAndSwap(key: String, expected: JsonElement?, value: JsonElement): Boolean =
        KitsugiAccountClient.client.postgrest.rpc("kitsugi_vault_cas", buildJsonObject {
            put("p_key", key); put("p_expected", expected ?: JsonNull); put("p_value", value)
        }).decodeAs<Boolean>()

    private fun blobJson(blob: CipherBlob): JsonElement = json.encodeToJsonElement(CipherBlob.serializer(), blob)
    private fun blob(value: JsonElement): CipherBlob = json.decodeFromJsonElement(CipherBlob.serializer(), value)
    private fun payload(value: JsonElement?, key: ByteArray): JsonArray = if (value == null) JsonArray(emptyList())
        else blob(value).let { json.parseToJsonElement(decrypt(key, unb64(it.iv), unb64(it.ct)).toString(Charsets.UTF_8)).jsonArray }

    private fun userId(): String? =
        runCatching { KitsugiAccountClient.client.auth.currentUserOrNull()?.id }.getOrNull()

    /**
     * Giriş sonrası çağrılır (Kitsugi şifresi bu an elimizde).
     *  • Yedek varsa: kasa anahtarını açar, token'ları geri yükler.
     *  • Yoksa: yeni kasa oluşturur ve mevcut token'ları yedekler.
     */
    suspend fun onSignedIn(context: Context, password: String): Result<Unit> = operation {
        restoring = true
        try {
            val uid = userId() ?: error("Oturum bulunamadı")
            LocalVaultKeyStore.owner(context)?.takeIf { it != uid }?.let { VaultBackupWorker.cancel(context, it) }
            val metaJson = fetchRow(uid, ROW_META)
            if (metaJson == null) {
                check(fetchRow(uid, ROW_PAYLOAD) == null) { "Kasa metadatası eksik; mevcut yedek korunuyor" }
                val key = ByteArray(32).also { random.nextBytes(it) }
                check(compareAndSwap(ROW_META, null, blobJson(wrap(password, key)))) {
                    "Başka cihaz kasayı oluşturdu. Tekrar giriş yap."
                }
                LocalVaultKeyStore.save(context, uid, key)
                LocalVaultKeyStore.saveBaseline(context, uid, emptyMap(), emptyMap())
                VaultBackupWorker.periodic(context, uid)
                VaultBackupWorker.enqueue(context, uid)
                backupUnlocked(context, uid)
            } else {
                val meta = blob(metaJson)
                // After an interrupted password change either wrap can unlock the SAME vault key.
                val pendingKey = meta.pending?.let { runCatching { unwrap(password, it.asBlob()) }.getOrNull() }
                val key = pendingKey ?: unwrap(password, meta)
                val cachedKey = runCatching { LocalVaultKeyStore.load(context, uid) }.getOrNull()
                if (cachedKey != null && cachedKey.contentEquals(key) && LocalVaultKeyStore.baseline(context, uid) != null) {
                    // Preserve unsent offline token rotations on this device instead of restoring stale cloud tokens.
                    backupUnlocked(context, uid)
                } else {
                    val remoteJson = fetchRow(uid, ROW_PAYLOAD)
                    val remote = payload(remoteJson, key)
                    if (remoteJson != null) restore(context, remote.toString().toByteArray(Charsets.UTF_8))
                    LocalVaultKeyStore.save(context, uid, key)
                    val hashes = VaultMerge.hashes(VaultMerge.groups(remote))
                    LocalVaultKeyStore.saveBaseline(context, uid, hashes, hashes)
                    if (remoteJson == null) backupUnlocked(context, uid)
                    else _status.value = "Bağlı hesapların şifreli yedeği geri yüklendi."
                }
                // Only a successfully authenticated NEW password may finalize a pending transition.
                // An old-password login must not discard a staged new wrap while Auth update is in flight.
                if (pendingKey != null) compareAndSwap(ROW_META, metaJson, blobJson(meta.pending!!.asBlob()))
            }
            _conflicts.value = emptySet()
            VaultBackupWorker.periodic(context, uid)
            VaultBackupWorker.enqueue(context, uid)
        } finally { restoring = false }
    }

    /** Mevcut token ve taşınabilir ayarları şifreler. Kilitli kasa başarı sayılmaz. */
    suspend fun backupNow(context: Context, expectedOwner: String? = null): Result<Unit> = operation {
        backupUnlocked(context, expectedOwner ?: userId() ?: throw VaultLockedException())
    }

    private suspend fun backupUnlocked(context: Context, uid: String, preferLocal: Boolean? = null) {
        check(userId() == uid) { "Oturum değişti" }
        val key = localVaultKey(context) ?: throw VaultLockedException()
        val base = LocalVaultKeyStore.baseline(context, uid) ?: throw VaultLockedException()
        val local = VaultMerge.groups(json.parseToJsonElement(snapshot(context).toString(Charsets.UTF_8)).jsonArray)
        // CAS retries refetch and merge. Never resend a stale encrypted full snapshot blindly.
        repeat(4) {
            val remoteJson = fetchRow(uid, ROW_PAYLOAD)
            if (preferLocal != null && remoteJson != conflictRemote) {
                _conflicts.value = emptySet()
                error("Bulut yedeği seçim ekranı açıldıktan sonra değişti. Şimdi eşitle ile yeniden kontrol et.")
            }
            val remote = VaultMerge.groups(payload(remoteJson, key))
            val merged = VaultMerge.merge(local, remote, base.local, base.remote, preferLocal)
            if (merged.conflicts.isNotEmpty() && preferLocal == null) {
                conflictRemote = remoteJson
                _conflicts.value = merged.conflicts
                throw VaultConflictException(merged.conflicts)
            }
            val mergedGroups = VaultMerge.groups(merged.payload)
            val alreadyCurrent = preferLocal == null && remoteJson != null && mergedGroups == remote
            val accepted = if (alreadyCurrent) true else {
                val (iv, ct) = encrypt(key, merged.payload.toString().toByteArray(Charsets.UTF_8))
                compareAndSwap(ROW_PAYLOAD, remoteJson, blobJson(CipherBlob(iv = b64(iv), ct = b64(ct))))
            }
            if (accepted) {
                // Keep the two views separate: remote-only changes do not turn an unchanged,
                // stale local session into a fresh local edit on the next upload.
                LocalVaultKeyStore.saveBaseline(context, uid, VaultMerge.hashes(local),
                    VaultMerge.remoteBaselineAfterUpload(local, mergedGroups, base.remote))
                _conflicts.value = emptySet()
                conflictRemote = null
                _status.value = "Şifreli hesap yedeği güncel. Son başarı: " +
                    java.text.DateFormat.getDateTimeInstance().format(java.util.Date(LocalVaultKeyStore.lastSuccess(context)))
                return
            }
        }
        error("Yedek başka cihazda güncelleniyor; işlem yeniden denenecek.")
    }

    /** Explicit user decision; no service credentials are displayed. Other service groups still merge. */
    suspend fun resolveConflict(context: Context, keepThisDevice: Boolean): Result<Unit> = operation {
        check(_conflicts.value.isNotEmpty()) { "Çözülecek çakışma yok" }
        backupUnlocked(context, userId() ?: throw VaultLockedException(), keepThisDevice)
        if (!keepThisDevice) _status.value = "Çakışan servislerin bulut kopyası korundu. Bu cihazda da kullanmak için çıkış yapıp tekrar giriş yap."
    }

    /**
     * Token dosyasında izinli bir anahtar değiştiğinde yedeği (çok sık yazmamak için 3 sn
     * gecikmeyle) günceller. Uygulama açılışında bir kez çağrılır.
     */
    fun startAutoBackup(context: Context) {
        val app = context.applicationContext
        appContext = app
        tokenPrefs(app).registerOnSharedPreferenceChangeListener(autoBackupListener)
        scope.launch {
            try {
                KitsugiAccountClient.client.auth.awaitInitialization()
                userId()?.let {
                    VaultBackupWorker.periodic(app, it)
                    VaultBackupWorker.enqueue(app, it, startup = true)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _status.value = "Oturum başlatılamadı; tekrar giriş yap." }
        }
        if (settingsObserver == null) settingsObserver = scope.launch {
            try {
                SettingsDataStore(app).cloudSettingsFlow.distinctUntilChanged().drop(1).collect { scheduleBackup() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _status.value = "Ayar değişiklikleri izlenemedi; periyodik yedek tekrar deneyecek." }
        }
    }

    /** Durable two-phase wrap transition. Never roll back on ambiguous network failures. */
    suspend fun changePassword(context: Context, currentPassword: String, newPassword: String): Result<Unit> = operation {
        require(newPassword.length >= 8) { "Yeni şifre en az 8 karakter olmalı." }
        val uid = userId() ?: throw VaultLockedException()
        val auth = KitsugiAccountClient.client.auth
        val email = auth.currentUserOrNull()?.email ?: throw VaultLockedException()
        // Reauthenticate, not just trust an old cached session or an old password wrap.
        auth.signInWith(Email) { this.email = email; password = currentPassword }
        check(userId() == uid) { "Oturum değişti" }
        val originalJson = fetchRow(uid, ROW_META) ?: throw VaultLockedException()
        val original = blob(originalJson)
        val key = original.pending?.let { runCatching { unwrap(currentPassword, it.asBlob()) }.getOrNull() }
            ?: unwrap(currentPassword, original)
        val newWrap: CipherBlob
        val stagedJson: JsonElement
        if (original.pending != null) {
            // Resume exactly the previous transition. A competing device cannot stage a different password.
            newWrap = original.pending.asBlob()
            check(runCatching { unwrap(newPassword, newWrap).contentEquals(key) }.getOrDefault(false)) {
                "Yarım kalan şifre değişikliği var. Önceki denemede seçtiğin yeni şifreyi kullan."
            }
            stagedJson = originalJson
        } else {
            require(currentPassword != newPassword) { "Yeni şifre mevcut şifreden farklı olmalı." }
            newWrap = wrap(newPassword, key)
            stagedJson = blobJson(original.copy(pending = PasswordWrap(newWrap.salt, newWrap.iv, newWrap.ct)))
            check(compareAndSwap(ROW_META, originalJson, stagedJson)) { "Başka cihaz şifre işlemi yapıyor. Tekrar dene." }
        }
        // If process/network dies here, both old and new password wraps remain available.
        // Neither password is stored locally, in WorkManager, or in the database.
        if (currentPassword != newPassword) auth.updateUser { password = newPassword }
        check(compareAndSwap(ROW_META, stagedJson, blobJson(newWrap))) {
            "Şifre güncellendi; kasa geçişini tamamlamak için yeni şifrenle tekrar giriş yap."
        }
        _status.value = "Şifre değiştirildi; bağlı hesap yedeğinin anahtarı korundu."
    }
}
