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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

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
    private const val INITIAL_STATUS = "Yedek durumu henüz doğrulanmadı."
    private val _status = MutableStateFlow(INITIAL_STATUS)
    private var statusOwner: String? = null
    val status = _status.asStateFlow()
    // Compose observes readiness instead of decrypting Android Keystore data on every frame.
    private val _unlockedOwner = MutableStateFlow<String?>(null)
    val unlockedOwner = _unlockedOwner.asStateFlow()
    private val _conflicts = MutableStateFlow<Set<String>>(emptySet())
    val conflicts = _conflicts.asStateFlow()
    private var conflictRemote: JsonElement? = null
    private var conflictOwner: String? = null

    /** Auth must not replace the singleton client's JWT in the middle of an upload for another owner. */
    suspend fun <T> changeSession(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { action() }
    }

    private suspend fun operation(context: Context, block: suspend () -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                block()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LinkageError) {
                _status.value = AccountErrorFormatter.userMessage(e)
                Result.failure(e)
            } catch (e: Exception) {
                _status.value = AccountErrorFormatter.userMessage(e)
                Result.failure(e)
            } finally {
                _unlockedOwner.value = userId()?.takeIf { hasLocalVault(context) }
                statusOwner = userId()
            }
        }
    }

    /** Kullanıcının elle yönettiği otomatik yedekleme anahtarı (varsayılan: açık). */
    @Volatile
    var autoBackupEnabled: Boolean = false
        private set

    /**
     * Otomatik yedeği aç/kapa. Açılışta bekleyen işler kuyruklanır; kapanışta
     * periyodik + bekleyen WorkManager işleri iptal edilir. Hesap ekranındaki
     * "Şimdi eşitle" de yalnızca seçili veri kategorilerini eşitler.
     */
    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        autoBackupEnabled = enabled
        val uid = userId() ?: return
        if (enabled && _unlockedOwner.value == uid) {
            VaultBackupWorker.periodic(context, uid)
            VaultBackupWorker.enqueue(context, uid)
        } else {
            VaultBackupWorker.cancel(context, uid)
        }
    }

    private fun scheduleBackup() {
        if (restoring) return
        if (!autoBackupEnabled) return
        val uid = userId() ?: return
        if (_unlockedOwner.value != uid) return
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

    private fun forgetLocalVaultUnlocked(context: Context) {
        LocalVaultKeyStore.owner(context)?.let { VaultBackupWorker.cancel(context, it) }
        LocalVaultKeyStore.clear(context)
        _unlockedOwner.value = null
        _conflicts.value = emptySet()
        conflictRemote = null
        conflictOwner = null
        _status.value = KitsugiStrings.get(R.string.kasa_kilitli_giris_yaparak_acabilirsin)
    }

    /** Keep the only local decryption key if flushing or server sign-out fails. */
    suspend fun signOutAndForget(context: Context, signOut: suspend () -> Unit): Result<Unit> = operation(context) {
        val uid = userId()
        val enabled = SettingsDataStore(context).settingsFlow.first().accountBackupEnabled
        if (uid != null && enabled && hasLocalVault(context)) backupUnlocked(context, uid)
        signOut()
        forgetLocalVaultUnlocked(context)
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
    suspend fun onSignedIn(context: Context, password: String, expectedOwner: String): Result<Unit> = operation(context) {
        restoring = true
        try {
            val uid = userId() ?: throw AccountSessionChangedException()
            if (uid != expectedOwner) throw AccountSessionChangedException()
            // A prior account's conflicts must not be shown under this identity.
            _conflicts.value = emptySet()
            conflictRemote = null
            conflictOwner = null
            autoBackupEnabled = SettingsDataStore(context).settingsFlow.first().accountBackupEnabled
            _status.value = KitsugiStrings.get(R.string.account_vault_checking)
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
                if (autoBackupEnabled) {
                    VaultBackupWorker.periodic(context, uid)
                    VaultBackupWorker.enqueue(context, uid)
                    backupUnlocked(context, uid)
                }
            } else {
                val meta = blob(metaJson)
                // After an interrupted password change either wrap can unlock the SAME vault key.
                val pendingKey = meta.pending?.let { runCatching { unwrap(password, it.asBlob()) }.getOrNull() }
                val key = pendingKey ?: unwrap(password, meta)
                val cachedKey = runCatching { LocalVaultKeyStore.load(context, uid) }.getOrNull()
                if (cachedKey != null && cachedKey.contentEquals(key) && LocalVaultKeyStore.baseline(context, uid) != null) {
                    // Preserve unsent offline token rotations on this device instead of restoring stale cloud tokens.
                    if (autoBackupEnabled) backupUnlocked(context, uid)
                } else {
                    val remoteJson = fetchRow(uid, ROW_PAYLOAD)
                    val remote = payload(remoteJson, key)
                    if (remoteJson != null) restore(context, remote.toString().toByteArray(Charsets.UTF_8))
                    LocalVaultKeyStore.save(context, uid, key)
                    val hashes = VaultMerge.hashes(VaultMerge.groups(remote))
                    LocalVaultKeyStore.saveBaseline(context, uid, hashes, hashes)
                    if (remoteJson == null) { if (autoBackupEnabled) backupUnlocked(context, uid) }
                    else _status.value = KitsugiStrings.get(R.string.bagli_hesaplarin_sifreli_yedegi_geri_yuklendi)
                }
                // Only a successfully authenticated NEW password may finalize a pending transition.
                // An old-password login must not discard a staged new wrap while Auth update is in flight.
                if (pendingKey != null) compareAndSwap(ROW_META, metaJson, blobJson(meta.pending!!.asBlob()))
            }
            _conflicts.value = emptySet()
            if (!autoBackupEnabled) _status.value = KitsugiStrings.get(R.string.account_vault_ready_backup_off)
            if (autoBackupEnabled) {
                VaultBackupWorker.periodic(context, uid)
                VaultBackupWorker.enqueue(context, uid)
            }
        } finally { restoring = false }
    }

    /** Mevcut token ve taşınabilir ayarları şifreler. Kilitli kasa başarı sayılmaz. */
    suspend fun backupNow(
        context: Context, expectedOwner: String? = null, automatic: Boolean = false
    ): Result<Unit> = operation(context) {
        // Check again INSIDE the session lock: the preference may have changed
        // while an already-running worker was waiting for an auth operation.
        if (automatic && !SettingsDataStore(context).settingsFlow.first().accountBackupEnabled) return@operation
        backupUnlocked(context, expectedOwner ?: userId() ?: throw VaultLockedException())
    }

    private suspend fun backupUnlocked(context: Context, uid: String, preferLocal: Boolean? = null) {
        if (userId() != uid) throw AccountSessionChangedException()
        val key = localVaultKey(context) ?: throw VaultLockedException()
        val base = LocalVaultKeyStore.baseline(context, uid) ?: throw VaultLockedException()
        val local = VaultMerge.groups(json.parseToJsonElement(snapshot(context).toString(Charsets.UTF_8)).jsonArray)
        // CAS retries refetch and merge. Never resend a stale encrypted full snapshot blindly.
        repeat(4) {
            val remoteJson = fetchRow(uid, ROW_PAYLOAD)
            if (preferLocal != null && remoteJson != conflictRemote) {
                _conflicts.value = emptySet()
                error(KitsugiStrings.get(R.string.bulut_yedegi_secim_ekrani_acildiktan_sonra_degisti_simdi_esitle))
            }
            val remote = VaultMerge.groups(payload(remoteJson, key))
            val merged = VaultMerge.merge(local, remote, base.local, base.remote, preferLocal)
            if (merged.conflicts.isNotEmpty() && preferLocal == null) {
                conflictRemote = remoteJson
                conflictOwner = uid
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
                conflictOwner = null
                _status.value = KitsugiStrings.get(R.string.sifreli_hesap_yedegi_guncel_son_basari) +
                    java.text.DateFormat.getDateTimeInstance().format(java.util.Date(LocalVaultKeyStore.lastSuccess(context)))
                return
            }
        }
        error("Yedek başka cihazda güncelleniyor; işlem yeniden denenecek.")
    }

    /** Explicit user decision; no service credentials are displayed. Other service groups still merge. */
    suspend fun resolveConflict(context: Context, keepThisDevice: Boolean): Result<Unit> = operation(context) {
        check(_conflicts.value.isNotEmpty() && conflictOwner == userId()) { "Çözülecek çakışma yok" }
        backupUnlocked(context, userId() ?: throw VaultLockedException(), keepThisDevice)
        if (!keepThisDevice) _status.value = KitsugiStrings.get(R.string.cakisan_servislerin_bulut_kopyasi_korundu_bu_cihazda_da_kullanma)
    }

    /**
     * Token dosyasında izinli bir anahtar değiştiğinde yedeği (çok sık yazmamak için 3 sn
     * gecikmeyle) günceller. Uygulama açılışında bir kez çağrılır.
     */
    fun startAutoBackup(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        tokenPrefs(app).registerOnSharedPreferenceChangeListener(autoBackupListener)
        scope.launch {
            try {
                val auth = KitsugiAccountClient.client.auth
                auth.awaitInitialization()
                val owners = auth.sessionStatus
                    // A transient refresh/network failure is not a sign-out.
                    .filter { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }
                    .map { (it as? SessionStatus.Authenticated)?.session?.user?.id }
                    .distinctUntilChanged()
                val enabledFlow = SettingsDataStore(app).settingsFlow
                    .map { it.accountBackupEnabled }.distinctUntilChanged()
                var previousOwner: String? = null
                combine(owners, enabledFlow) { owner, enabled -> owner to enabled }.collect { (owner, enabled) ->
                    mutex.withLock {
                        // A queued observer emission must not mutate the next account's state.
                        if (owner != userId()) return@withLock
                        autoBackupEnabled = enabled
                        previousOwner?.takeIf { it != owner }?.let { VaultBackupWorker.cancel(app, it) }
                        if (conflictOwner != owner) {
                            _conflicts.value = emptySet()
                            conflictRemote = null
                            conflictOwner = null
                        }
                        val wasReady = _unlockedOwner.value
                        val ready = owner?.takeIf { hasLocalVault(app) }
                        _unlockedOwner.value = ready
                        // Do not replace a just-finished unlock failure with a generic
                        // "locked" message merely because the auth observer ran afterwards.
                        if (statusOwner != owner || _status.value == INITIAL_STATUS) {
                            _status.value = KitsugiStrings.get(if (ready == null)
                                R.string.kasa_kilitli_giris_yaparak_acabilirsin
                            else if (!enabled) R.string.account_vault_ready_backup_off
                            else R.string.account_vault_ready_check_pending)
                        } else if (ready != null && !enabled) {
                            _status.value = KitsugiStrings.get(R.string.account_vault_ready_backup_off)
                        } else if (ready != null && wasReady != ready) {
                            _status.value = KitsugiStrings.get(R.string.account_vault_ready_check_pending)
                        }
                        statusOwner = owner
                        if (owner != null) {
                            if (enabled && ready != null) {
                                VaultBackupWorker.periodic(app, owner)
                                VaultBackupWorker.enqueue(app, owner, startup = true)
                            } else VaultBackupWorker.cancel(app, owner)
                        }
                        previousOwner = owner
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LinkageError) {
                _status.value = AccountErrorFormatter.userMessage(e)
            } catch (e: Exception) {
                _status.value = AccountErrorFormatter.userMessage(e)
            }
        }
        if (settingsObserver == null) settingsObserver = scope.launch {
            try {
                SettingsDataStore(app).cloudSettingsFlow.distinctUntilChanged().drop(1).collect { scheduleBackup() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _status.value = KitsugiStrings.get(R.string.ayar_degisiklikleri_izlenemedi_periyodik_yedek_tekrar_deneyecek) }
        }
    }

    /** Durable two-phase wrap transition. Never roll back on ambiguous network failures. */
    suspend fun changePassword(context: Context, currentPassword: String, newPassword: String): Result<Unit> = operation(context) {
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
        _status.value = KitsugiStrings.get(R.string.sifre_degistirildi_bagli_hesap_yedeginin_anahtari_korundu)
    }
}
