package com.kitsugi.animelist.data.remote.malweb

/**
 * OAuth hesabı ile MAL web oturumunun aynı kişiye ait olup olmadığını karşılaştırır.
 *
 * İki kimlik farklı mekanizmalarla taşınır:
 *  - **OAuth:** `ExternalAuthManager` içindeki MAL erişim jetonu; `users/@me` çağrısı bağlı
 *    hesabın kullanıcı adını verir (`oauthUsername`).
 *  - **Web oturumu:** WebView çerez deposundaki `is_signed_in=1`; bildirim sayfasının
 *    HTML'inden oturum sahibinin kullanıcı adı okunur (`webUsername`).
 *
 * Normal akışta ikisi aynı in-app WebView oturumundan doğar; ancak kullanıcı WebView'de başka
 * bir MAL hesabı bırakmışsa web oturumu farklı bir kişiye ait olabilir. Bu durumda bildirimler
 * yanlış hesabın bildirimleri olacağı için gösterilmez.
 *
 * Saf Kotlin'dir (Android bağımlılığı yok) → `app/src/test` içinde doğrudan test edilir.
 */
internal object MalAccountMatch {

    enum class Status {
        /** İki kimlik de biliniyor ve aynı hesabı gösteriyor. Bildirimler güvenle gösterilir. */
        MATCHED,

        /** İki kimlik farklı hesaplara ait. Bildirimler gösterilmez, yeniden giriş istenir. */
        MISMATCHED,

        /** Bir kimlik okunamadı (ör. sayfa yapısı değişti). Doğrulama yapılamadı. */
        UNVERIFIED,

        /** Bağlı OAuth hesabı yok; yalnızca web oturumuyla çalışılıyor. Doğrulanacak eş yok. */
        NO_OAUTH_ACCOUNT
    }

    /**
     * @param oauthUsername `users/@me` çağrısından gelen kullanıcı adı; yoksa `null`.
     * @param webUsername Bildirim sayfasının HTML'inden okunan oturum sahibi; yoksa `null`.
     * @param oauthAvailable MAL OAuth jetonu mevcut mu? Yalnızca `oauthUsername == null` iken
     *   anlamlıdır: jeton yoksa `NO_OAUTH_ACCOUNT`, jeton var ama ad okunamadıysa `UNVERIFIED`.
     */
    fun status(oauthUsername: String?, webUsername: String?, oauthAvailable: Boolean): Status {
        val oauth = oauthUsername?.trim()?.takeIf(String::isNotBlank)
            ?: return if (oauthAvailable) Status.UNVERIFIED else Status.NO_OAUTH_ACCOUNT
        val web = webUsername?.trim()?.takeIf(String::isNotBlank) ?: return Status.UNVERIFIED
        return if (oauth.equals(web, ignoreCase = true)) Status.MATCHED else Status.MISMATCHED
    }
}
