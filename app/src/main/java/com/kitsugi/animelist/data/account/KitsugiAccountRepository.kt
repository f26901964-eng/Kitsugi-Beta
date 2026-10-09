package com.kitsugi.animelist.data.account

import android.content.Context
import com.kitsugi.animelist.data.local.SearchHistoryDao
import com.kitsugi.animelist.data.local.SearchHistoryEntity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Hesap işlemleri ve veri senkronizasyonu.
 *
 * Kullanıcı verisi `public.user_data` tablosunda (user_id, key, value) olarak tutulur.
 * Şu an senkronize edilen: arama geçmişi (key = "search_history").
 * Eklentiler ve ayarlar aynı mekanizmayla sonraki adımda eklenecek.
 */
object KitsugiAccountRepository {

    private suspend fun <T> accountResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private const val TABLE = "user_data"
    private const val KEY_SEARCH_HISTORY = "search_history"
    private const val SEARCH_HISTORY_LIMIT = 20

    /**
     * Kaynak tarafında tutulmayan bildirimlerin arşivi (MAL/Simkl/Kitsu/Bangumi).
     * Değer: { "sources": { "<kaynak>": [ ArchivedNotif, ... ] } }
     */
    private const val KEY_NOTIFICATIONS = "notifications"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class UserDataRow(
        @SerialName("user_id") val userId: String? = null,
        val key: String,
        val value: JsonElement
    )

    @Serializable
    private data class RemoteSearchEntry(
        val query: String,
        val type: String,
        val timestamp: Long
    )

    private val searchListSerializer: KSerializer<List<RemoteSearchEntry>> =
        ListSerializer(RemoteSearchEntry.serializer())

    private val scope = CoroutineScope(Dispatchers.IO)

    /** Giriş yapılmışsa e-posta, yoksa null. */
    fun currentEmail(): String? =
        runCatching { KitsugiAccountClient.client.auth.currentUserOrNull()?.email }.getOrNull()

    fun isLoggedIn(): Boolean = currentEmail() != null

    /**
     * Kayıt. E-posta doğrulama açıksa oturum hemen açılmaz; sonuç `needsConfirmation = true` döner.
     */
    suspend fun signUp(context: Context, email: String, password: String): Result<Boolean> = accountResult {
        LinkedAccountVault.forgetLocalVault(context)
        val auth = KitsugiAccountClient.client.auth
        LinkedAccountVault.changeSession {
            auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        }
        // true → oturum açıldı, false → e-posta doğrulaması bekleniyor
        val hasSession = auth.currentUserOrNull() != null
        if (hasSession) {
            // Auth başarılı kalır; kasa hatası ayrıca gözlemlenebilir durum akışında gösterilir
            LinkedAccountVault.onSignedIn(context, password)
                .onFailure { android.util.Log.w("KitsugiAccount", "Kasa açılamadı: ${it.javaClass.simpleName}") }
        }
        hasSession
    }

    suspend fun signIn(context: Context, email: String, password: String): Result<Unit> = accountResult {
        LinkedAccountVault.changeSession {
            KitsugiAccountClient.client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        }
        // Bağlı servis token'larını yedekten geri yükle (şifre bu an elimizde)
        LinkedAccountVault.onSignedIn(context, password)
            .onFailure { android.util.Log.w("KitsugiAccount", "Kasa açılamadı: ${it.javaClass.simpleName}") }
        Unit
    }

    suspend fun signOut(context: Context): Result<Unit> = accountResult {
        // Do not erase the only decryption key while this device has unsent account changes.
        if (LinkedAccountVault.hasLocalVault(context)) LinkedAccountVault.backupNow(context).getOrThrow()
        LinkedAccountVault.changeSession { KitsugiAccountClient.client.auth.signOut() }
        LinkedAccountVault.forgetLocalVault(context)
        Unit
    }

    /** Yerel arama geçmişini buluta yazar (son [SEARCH_HISTORY_LIMIT] kayıt). */
    suspend fun pushSearchHistory(dao: SearchHistoryDao): Result<Unit> = accountResult {
        val uid = currentUserId() ?: return@accountResult
        val local = dao.getRecentSearchHistory().first()
        val payload = local.map { RemoteSearchEntry(it.query, it.type, it.timestamp) }
        writeKey(uid, KEY_SEARCH_HISTORY, json.encodeToJsonElement(searchListSerializer, payload))
    }

    /**
     * Buluttaki arama geçmişini yerelle birleştirir: aynı sorgu için en yeni zaman damgası kazanır,
     * sonra sonuç hem yerel veritabanına hem buluta yazılır.
     */
    suspend fun pullAndMergeSearchHistory(dao: SearchHistoryDao): Result<Unit> = accountResult {
        val uid = currentUserId() ?: return@accountResult
        val remote = fetchRemoteSearchHistory(uid)
        val local = dao.getRecentSearchHistory().first().map {
            RemoteSearchEntry(it.query, it.type, it.timestamp)
        }

        val merged = (local + remote)
            .groupBy { it.query }
            .map { (_, entries) -> entries.maxBy { it.timestamp } }
            .sortedByDescending { it.timestamp }
            .take(SEARCH_HISTORY_LIMIT)

        merged.forEach { e ->
            dao.insertSearchQuery(SearchHistoryEntity(query = e.query, timestamp = e.timestamp, type = e.type))
        }
        writeKey(uid, KEY_SEARCH_HISTORY, json.encodeToJsonElement(searchListSerializer, merged))
    }

    /**
     * Arama geçmişi değiştiğinde çağrılır. Giriş yoksa hiçbir şey yapmaz.
     * Ağ işlemi arka planda yapılır, arama akışını bekletmez.
     */
    fun onSearchHistoryChanged(dao: SearchHistoryDao) {
        if (!isLoggedIn()) return
        scope.launch { pushSearchHistory(dao) }
    }

    // ─── Bildirim arşivi (kaynak tarafında tutulmayan bildirimlerin yedeği) ────

    /**
     * Yerel bildirim arşivini buluta yazar.
     *
     * MyAnimeList/Simkl/Kitsu/Bangumi gibi API'lerinde kişisel bildirim ucu olmayan
     * kaynaklar için Kitsugi'nin ürettiği bildirimler (yayın takvimi + izleme listesi
     * eşleşmeleri) sunucuda hiçbir yerde saklanmaz; bu yedek sayesinde hem cihazda
     * hem hesapta kalıcı olurlar.
     */
    suspend fun pushNotificationArchive(context: Context): Result<Unit> = accountResult {
        val uid = currentUserId() ?: return@accountResult
        writeKey(uid, KEY_NOTIFICATIONS, com.kitsugi.animelist.data.notifications.NotificationArchiveStore.toJsonElement(context))
    }

    /**
     * Buluttaki bildirim arşivini yerelle birleştirir.
     *
     * Birleştirme kuralı arşiv deposundadır ([com.kitsugi.animelist.data.notifications.NotificationArchiveStore.mergeLists]):
     * aynı id için en yeni kayıt kazanır, kaynak başına üst sınıra kadar tutulur.
     * Sonuç hem yerel dosyaya hem buluta geri yazılır.
     */
    suspend fun pullAndMergeNotificationArchive(context: Context): Result<Unit> = accountResult {
        val uid = currentUserId() ?: return@accountResult
        val rows = KitsugiAccountClient.client.from(TABLE)
            .select(columns = Columns.list("user_id", "key", "value")) {
                filter {
                    eq("user_id", uid)
                    eq("key", KEY_NOTIFICATIONS)
                }
            }
            .decodeList<UserDataRow>()
        val remote = com.kitsugi.animelist.data.notifications.NotificationArchiveStore.fromJsonElement(rows.firstOrNull()?.value)
        // Birleştirme yerel dosyaya zaten yazıldı; buluta aynı hâli geri yaz.
        com.kitsugi.animelist.data.notifications.NotificationArchiveStore.mergeSourcesBlocking(context, remote)
        writeKey(uid, KEY_NOTIFICATIONS, com.kitsugi.animelist.data.notifications.NotificationArchiveStore.toJsonElement(context))
    }

    private suspend fun fetchRemoteSearchHistory(uid: String): List<RemoteSearchEntry> {
        val rows = KitsugiAccountClient.client.from(TABLE)
            .select(columns = Columns.list("user_id", "key", "value")) {
                filter {
                    eq("user_id", uid)
                    eq("key", KEY_SEARCH_HISTORY)
                }
            }
            .decodeList<UserDataRow>()
        val value = rows.firstOrNull()?.value ?: return emptyList()
        return runCatching { json.decodeFromJsonElement(searchListSerializer, value) }
            .getOrDefault(emptyList())
    }

    private suspend fun writeKey(uid: String, key: String, value: JsonElement) {
        KitsugiAccountClient.client.from(TABLE).upsert(
            UserDataRow(userId = uid, key = key, value = value)
        )
    }

    private fun currentUserId(): String? =
        runCatching { KitsugiAccountClient.client.auth.currentUserOrNull()?.id }.getOrNull()
}
