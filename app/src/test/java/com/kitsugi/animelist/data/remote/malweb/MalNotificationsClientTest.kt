package com.kitsugi.animelist.data.remote.malweb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MalNotificationsClientTest {
    @Test
    fun parsesMALNotificationAssignmentFromPageScript() {
        val html = """
            <html><body><script>
            window.MAL.notification = {
              "items": [{
                "id": "notice-123",
                "typeIdentifier": "related_anime_add",
                "categoryName": "New Related Anime",
                "createdAt": 1791132300,
                "createdAtForDisplay": "Oct 4, 4:45 PM",
                "isRead": false,
                "isDeleted": false,
                "url": "https://myanimelist.net/anime/5114/Fullmetal_Alchemist",
                "anime": {"title": "Fullmetal Alchemist", "mediaType": "TV"}
              }],
              "historyItems": []
            };
            </script></body></html>
        """.trimIndent()

        val feed = MalNotificationsClient.parseHtml(html)

        assertNotNull(feed)
        val item = requireNotNull(feed).items.single()
        assertEquals("notice-123", item.id)
        assertEquals("New Related Anime", item.displayTitle())
        assertEquals("Fullmetal Alchemist • TV", item.displayBody())
        assertEquals(5114, item.mediaId)
        assertEquals("anime", item.mediaType)
        assertEquals("https://myanimelist.net/anime/5114/Fullmetal_Alchemist", item.targetUrl)
        assertFalse(item.isRead)
    }

    @Test
    fun handlesQuotedBracesAndEmptyNotificationList() {
        val html = """
            <script>window.MAL.notification = {"items":[],"historyItems":[{"text":"brace } inside"}]};</script>
        """.trimIndent()

        val feed = MalNotificationsClient.parseHtml(html)

        assertNotNull(feed)
        assertTrue(requireNotNull(feed).items.isEmpty())
    }

    @Test
    fun returnsNullForUnauthenticatedOrMalformedPages() {
        assertNull(MalNotificationsClient.parseHtml("<html><body>Log In</body></html>"))
        assertNull(MalNotificationsClient.parseHtml("<script>window.MAL.notification = {invalid};</script>"))
    }

    @Test
    fun onlyAcceptsSafeMALItemUrlsNotTheNotificationLandingPage() {
        val external = notification(id = "1", url = "https://example.org/something")
        val relative = notification(id = "2", url = "/profile/User/friends")
        val notificationLandingPage = notification(id = "3", url = "https://myanimelist.net/notification")

        assertNull(external.targetUrl)
        assertEquals("https://myanimelist.net/profile/User/friends", relative.targetUrl)
        assertNull(notificationLandingPage.targetUrl)
    }

    // ─── Oturum sahibinin kullanıcı adı (OAuth ⇄ web oturumu eşlemesi) ────────

    @Test
    fun readsSessionOwnerFromTheHeaderProfileLink() {
        val html = """
            <html><body>
            <a class="header-profile-link" href="https://myanimelist.net/profile/KitsugiFan">
                <img src="https://cdn.myanimelist.net/images/userimages/1.jpg"/>
            </a>
            <script>
            window.MAL.notification = {"items":[{
                "id": "notice-1",
                "friendName": "SomeoneElse",
                "friendProfileUrl": "https://myanimelist.net/profile/SomeoneElse"
            }]};
            </script>
            </body></html>
        """.trimIndent()

        val feed = MalNotificationsClient.parseHtml(html)

        assertEquals("KitsugiFan", requireNotNull(feed).sessionUsername)
    }

    @Test
    fun fallsBackToTheMostFrequentProfileLinkWhenHeaderMarkupChanges() {
        val html = """
            <html><body>
            <a href="https://myanimelist.net/profile/RealOwner">Profilim</a>
            <a href="https://myanimelist.net/profile/RealOwner/friends">Arkadaşlarım</a>
            <a href="/profile/OtherUser">OtherUser</a>
            <script>window.MAL.notification = {"items":[]};</script>
            </body></html>
        """.trimIndent()

        assertEquals("RealOwner", MalNotificationsClient.parseSessionUsername(html))
    }

    @Test
    fun neverTakesTheSessionOwnerFromNotificationPayloadLinks() {
        // Bildirim gönderenleri oturum sahibi SANILMAMALI: JSON içindeki profil
        // bağlantıları taramadan çıkarılır.
        val html = """
            <script>window.MAL.notification = {"items":[{
                "id": "notice-1",
                "friendName": "Stranger",
                "friendProfileUrl": "https://myanimelist.net/profile/Stranger"
            }]};</script>
        """.trimIndent()

        assertNull(MalNotificationsClient.parseSessionUsername(html))
    }

    @Test
    fun skipsReservedProfilePathsAndNumericIds() {
        assertNull(MalNotificationsClient.parseSessionUsername("<a href=\"/profile/login\">Giriş</a>"))
        assertNull(MalNotificationsClient.parseSessionUsername("<a href=\"/profile/12345\">id</a>"))
    }

    @Test
    fun returnsNullForAnonymousPages() {
        assertNull(MalNotificationsClient.parseSessionUsername("<html><body>Log In</body></html>"))
    }

    private fun notification(id: String, url: String) = MalNotificationsClient.Notification(
        id = id,
        typeIdentifier = "friend_request",
        categoryName = null,
        createdAt = null,
        createdAtForDisplay = null,
        isRead = false,
        isDeleted = false,
        isApproved = true,
        url = url,
        animeTitle = null,
        animeMediaType = null,
        animeUrl = null,
        senderName = null,
        senderProfileUrl = null,
        pageUrl = null,
        pageTitle = null,
        commentUserName = null,
        commentUserProfileUrl = null,
        commentUserImageUrl = null,
        text = null,
        date = null,
        friendName = null,
        friendProfileUrl = null,
        friendImageUrl = null,
        quoteUserName = null,
        quoteUserProfileUrl = null,
        postedUserName = null,
        postedUserProfileUrl = null,
        topicUrl = null,
        topicTitle = null,
        message = null,
        animeItems = emptyList()
    )
}
