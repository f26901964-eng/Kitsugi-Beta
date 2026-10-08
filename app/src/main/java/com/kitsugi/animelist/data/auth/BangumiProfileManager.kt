package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiFavoriteItem
import com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiUser
import com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiUserCollection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Bangumi profil sekmesinin veri kaynağı.
 *
 * Kapsam (resmi v0 API — https://github.com/bangumi/api/blob/master/open-api/v0.yaml):
 *  - `GET /v0/users/{username}`                         → kullanıcı kimliği, ad, avatar, imza
 *  - `GET /v0/users/{username}/collections`             → tüm tür/durum koleksiyonları (sayfalı)
 *  - `GET /v0/users/{username}/collections/-/characters` → karakter favorileri
 *  - `GET /v0/users/{username}/collections/-/persons`    → kişi favorileri
 *
 * Bildirimler (notification) bu API'de YOKTUR. bgm.tv'nin bildirim sayfası web oturumu (çerez)
 * ister; bu yüzden UI bunu uygulama içi WebView üzerinden açar (bkz. BangumiProfileContent).
 */
object BangumiProfileManager {

    data class Snapshot(
        val user: BangumiUser,
        val collections: List<BangumiUserCollection>,
        val characterFavorites: List<BangumiFavoriteItem>,
        val personFavorites: List<BangumiFavoriteItem>
    )

    /**
     * Bağlı değilse null döner. Koleksiyon çekimi başarısız olursa istisna fırlatılır
     * (üst katman hata durumunu gösterir). Kullanıcı ayrıntısı ve favoriler en iyi çabayla alınır.
     */
    suspend fun fetch(context: Context): Snapshot? {
        if (!BangumiAuthStore.isConnected(context)) return null

        var login = BangumiAuthStore.getUsername(context)?.takeIf { it.isNotBlank() }
        if (login == null && BangumiAuthStore.ensureUserResolved(context)) {
            login = BangumiAuthStore.getUsername(context)?.takeIf { it.isNotBlank() }
        }
        val username = login ?: return null
        val token = BangumiAuthStore.getValidToken(context)

        return coroutineScope {
            val userDeferred = async {
                runCatching { BangumiApiClient.getUser(token, username) }.getOrNull()
            }
            val collectionsDeferred = async {
                val accessToken = token
                    ?: throw IllegalStateException("Bangumi oturumu yenilenemedi; lütfen yeniden bağlanın")
                BangumiApiClient.getAllUserCollections(accessToken, username)
            }
            val charactersDeferred = async {
                runCatching { BangumiApiClient.getUserCharacterFavorites(token, username) }
                    .getOrDefault(emptyList())
            }
            val personsDeferred = async {
                runCatching { BangumiApiClient.getUserPersonFavorites(token, username) }
                    .getOrDefault(emptyList())
            }

            val user = userDeferred.await() ?: BangumiUser(
                id = BangumiAuthStore.getUserId(context),
                username = username,
                nickname = BangumiAuthStore.getNickname(context)?.takeIf { it.isNotBlank() } ?: username,
                avatarUrl = BangumiAuthStore.getAvatarUrl(context),
                userGroup = null,
                sign = null
            )

            Snapshot(
                user = user,
                collections = collectionsDeferred.await(),
                characterFavorites = charactersDeferred.await(),
                personFavorites = personsDeferred.await()
            )
        }
    }
}
