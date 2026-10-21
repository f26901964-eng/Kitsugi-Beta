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
    fun detectsSignedOutLoginPageSoCallerAsksForWebSession() {
        // MAL bazen giriş formunu 200 ile döner; bu "sayfa yapısı hatası" değil, oturum eksikliğidir.
        val loginPage = """
            <html><body><form id="loginForm" action="/login.php?from=%2Fnotification"
                method="POST"><input name="csrf_token" value="x"></form></body></html>
        """.trimIndent()
        assertTrue(MalNotificationsClient.looksSignedOutPage(loginPage))
        assertFalse(MalNotificationsClient.looksSignedOutPage("<html><body>Maintenance</body></html>"))
        assertFalse(MalNotificationsClient.looksSignedOutPage(""))
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
            <a class="header-profile-link" href="https://myanimelist.net/profile/Kitsugi-Fan_2">
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

        assertEquals("Kitsugi-Fan_2", requireNotNull(feed).sessionUsername)
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
    fun parsesHyphenatedAndUnderscoredSessionProfileUrls() {
        assertEquals(
            "A-user_2",
            MalNotificationsClient.parseSessionUsernameFromProfileUrl(
                "https://myanimelist.net/profile/A-user_2"
            )
        )
        assertEquals(
            "A_user-2",
            MalNotificationsClient.parseSessionUsernameFromProfileUrl("/profile/A_user-2/friends")
        )
        assertNull(MalNotificationsClient.parseSessionUsernameFromProfileUrl("https://example.org/profile/A-user_2"))
    }

    @Test
    fun returnsNullForAnonymousPages() {
        assertNull(MalNotificationsClient.parseSessionUsername("<html><body>Log In</body></html>"))
    }

    // ─── Akışın devamı: historyItems + sayfalama ──────────────────────────────

    @Test
    fun mergesHistoryItemsWithRecentItemsAndDeduplicatesById() {
        // MAL okunmuş/eski bildirimleri `historyItems` altında döndürür. Yalnızca `items`
        // okunduğu için kullanıcı "bildirimlerin devamını" hiç göremiyordu.
        val html = """
            <script>window.MAL.notification = {
              "items": [{
                "id": "recent-1",
                "typeIdentifier": "related_anime_add",
                "categoryName": "New Related Anime",
                "anime": {"title": "Umayuru: Full Gate!", "mediaType": "ONA"},
                "url": "https://myanimelist.net/anime/58447/Umayuru_Full_Gate"
              }],
              "historyItems": [{
                "id": "older-1",
                "typeIdentifier": "profile_comment",
                "categoryName": "New profile comment",
                "text": "Eski bildirim",
                "url": "https://myanimelist.net/profile/Someone"
              }, {
                "id": "recent-1",
                "typeIdentifier": "related_anime_add",
                "categoryName": "New Related Anime"
              }]
            };</script>
        """.trimIndent()

        val feed = requireNotNull(MalNotificationsClient.parseHtml(html))

        assertEquals(listOf("recent-1", "older-1"), feed.items.map { it.id })
        assertEquals(1, feed.page)
        assertTrue(feed.hasNextPage)
    }

    @Test
    fun ignoresHistoryEntriesWithoutAnId() {
        // Geçmiş kayıtlarının şeması aynı olmak zorunda değil; kimliksiz kayıt uydurulmaz.
        val html = """
            <script>window.MAL.notification = {"items":[],"historyItems":[{"text":"brace } inside"}]};</script>
        """.trimIndent()

        assertTrue(requireNotNull(MalNotificationsClient.parseHtml(html)).items.isEmpty())
    }

    @Test
    fun parsesPageThatCarriesOnlyHistoryItems() {
        // MAL bazı oturumlarda "items" alanını hiç yazmıyor ve TÜM kayıtları "historyItems"
        // altında döndürüyor. Eski koşul (`items` zorunlu) bu sayfayı "veri yok" sayıyor ve
        // kullanıcı "bildirimlerin devamı hiç gelmiyor" şikâyetini alıyordu.
        val html = """
            <html><body><script>
            window.MAL.notification = {
              "historyItems": [{
                "id": "older-1",
                "typeIdentifier": "related_anime_add",
                "categoryName": "New Related Anime",
                "createdAt": 1791132300,
                "isRead": true,
                "anime": {"title": "Fullmetal Alchemist", "mediaType": "TV"}
              }]
            };
            </script></body></html>
        """.trimIndent()

        val feed = MalNotificationsClient.parseHtml(html, page = 1)

        assertNotNull(feed)
        val item = requireNotNull(feed).items.single()
        assertEquals("older-1", item.id)
        assertEquals("Fullmetal Alchemist", item.animeTitle)
        assertTrue(item.isRead)
        // A short response is not proof that the MAL View more control is absent.
        assertTrue(requireNotNull(feed).hasNextPage)
    }

    @Test
    fun buildsContinuationCandidatesForShortAndTwentyRowFeeds() {
        val candidates = MalNotificationsClient.moreUrlCandidates(page = 2, batchSize = 9)

        assertTrue(candidates.contains("https://myanimelist.net/notification?p=2"))
        assertTrue(candidates.contains("https://myanimelist.net/notification?offset=20"))
        assertTrue(candidates.contains("https://myanimelist.net/notification?offset=9"))
        assertTrue(candidates.contains("https://myanimelist.net/notification?page=2"))
        assertTrue(candidates.contains("https://myanimelist.net/notification/history?page=2"))
        assertEquals(candidates.distinct(), candidates)
        assertTrue(MalNotificationsClient.moreUrlCandidates(page = 1).isEmpty())
    }

    @Test
    fun stripsUiPrefixFromKnownIdsSoDuplicatePagesNeverTriggerEndlessLoops() {
        assertEquals("998877", MalNotificationsClient.rawNotificationId("mal_web_998877"))
        assertEquals("998877", MalNotificationsClient.rawNotificationId("998877"))
        assertEquals(
            setOf("101", "102"),
            MalNotificationsClient.normalizeKnownIds(listOf("mal_web_101", "102", "  mal_web_102  "))
        )
    }

    @Test
    fun parsesSidebarCategoryCountsUrlsAndDeterminesHasNextFromTotalCount() {
        val html = """
            <html><body class="page-common notification">
              <ul class="notificaiton-category-nav">
                <li><a href="https://myanimelist.net/notification" class="active">All Notifications <span class="counter">45</span></a></li>
                <li><a href="/notification?type=related_anime_add">New Related Anime <span class="counter">39</span></a></li>
                <li><a href="/notification?type=on_air">Now Airing <span class="counter">4</span></a></li>
                <li><a href="/notification?type=friend_request">Friend Requests <span class="counter">1</span></a></li>
                <li><a href="/notification?type=profile_comment">Profile Comments <span class="counter">1</span></a></li>
                <li><a href="/notification?type=blog_comment">Blog Comments <span class="counter zero">0</span></a></li>
              </ul>
              <script>window.MAL.notification = {"items": [{"id": "n-1", "typeIdentifier": "related_anime_add"}]};</script>
            </body></html>
        """.trimIndent()

        val feed = requireNotNull(MalNotificationsClient.parseHtml(html))
        assertEquals(45, feed.categoryCounts[MalNotificationCategory.ALL])
        assertEquals(39, feed.categoryCounts[MalNotificationCategory.NEW_RELATED_ANIME])
        assertEquals(4, feed.categoryCounts[MalNotificationCategory.NOW_AIRING])
        assertEquals(1, feed.categoryCounts[MalNotificationCategory.FRIEND_REQUESTS])
        assertEquals(1, feed.categoryCounts[MalNotificationCategory.PROFILE_COMMENTS])
        assertEquals(0, feed.categoryCounts[MalNotificationCategory.BLOG_COMMENTS])
        assertEquals(
            "https://myanimelist.net/notification?type=profile_comment",
            feed.categoryUrls[MalNotificationCategory.PROFILE_COMMENTS]
        )
        assertTrue(feed.hasNextPage)

        val completeHtml = html.replace("<span class=\"counter\">45</span>", "<span class=\"counter\">1</span>")
        val completeFeed = requireNotNull(MalNotificationsClient.parseHtml(completeHtml))
        assertFalse(completeFeed.hasNextPage)
    }

    @Test
    fun extractsNextPageFromNotificationPaginationAndRejectsHashAnchors() {
        val numberedHtml = """
            <html><body class="notification">
              <div class="notification-pagination">
                <span>1</span>
                <a href="https://myanimelist.net/notification?p=2">2</a>
              </div>
              <script>window.MAL.notification = {"items": [{"id": "n-1"}]};</script>
            </body></html>
        """.trimIndent()
        assertEquals(
            "https://myanimelist.net/notification?p=2",
            requireNotNull(MalNotificationsClient.parseHtml(numberedHtml, page = 1)).nextPageUrl
        )

        val hashOnlyHtml = """
            <html><body class="notification">
              <div class="notification-pagination">
                <a href="#">View More</a>
              </div>
              <script>window.MAL.notification = {"items": [{"id": "n-1"}]};</script>
            </body></html>
        """.trimIndent()
        assertNull(requireNotNull(MalNotificationsClient.parseHtml(hashOnlyHtml, page = 1)).nextPageUrl)
        assertNull(MalNotificationsClient.normalizeNotificationPageUrl("#"))
        assertNull(MalNotificationsClient.normalizeNotificationPageUrl("javascript:void(0)"))
    }

    @Test
    fun mergesRenderedDomItemsWithoutDuplicatingEmbeddedJsonItems() {
        val html = """
            <html><body class="page-common notification">
              <div id="content">
                <div class="notification-section">
                  <ol class="notification-item-list">
                    <li class="notification-item related_anime_add">
                      <div class="notification-item-header">
                        <span class="time">Sep 23, 2025 5:40 AM</span>
                        <span class="category">New Related Anime</span>
                      </div>
                      <div class="notification-item-content related_anime_add">
                        <span><a href="https://myanimelist.net/anime/60001/Girls_Band_Cry_Movie_2">Girls Band Cry Movie 2</a> (TV) has just been added to the database.</span>
                      </div>
                    </li>
                    <li class="notification-item profile_comment" data-id="older-26">
                      <div class="notification-item-header">
                        <span class="time">Aug 12, 2025 9:15 PM</span>
                        <span class="category">Profile Comments</span>
                      </div>
                      <div class="notification-item-content profile_comment">
                        <span><a href="https://myanimelist.net/profile/Alice">Alice</a> posted a comment on your profile: <q class="message">Nice list!</q></span>
                      </div>
                    </li>
                  </ol>
                </div>
              </div>
              <script>window.MAL.notification = {"items": [{
                "id": "n-25",
                "typeIdentifier": "related_anime_add",
                "categoryName": "New Related Anime",
                "createdAtForDisplay": "Sep 23, 2025 5:40 AM",
                "url": "https://myanimelist.net/anime/60001/Girls_Band_Cry_Movie_2",
                "anime": {"title": "Girls Band Cry Movie 2", "mediaType": "TV"}
              }]};</script>
            </body></html>
        """.trimIndent()

        val feed = requireNotNull(MalNotificationsClient.parseHtml(html))
        assertEquals(listOf("n-25", "older-26"), feed.items.map { it.id })
        val olderComment = feed.items[1]
        assertEquals("profile_comment", olderComment.typeIdentifier)
        assertEquals("Alice", olderComment.commentUserName)
        assertEquals("Nice list!", olderComment.text)
    }

    @Test
    fun carriesExplicitFalseEndMarkerEvenWhenRowsArePresent() {
        val payload = """{"items":[{"id":"notice-1"}],"hasNextPage":false}"""

        val feed = requireNotNull(MalNotificationsClient.parseNotificationPayload(
            payload = payload,
            page = 1,
            sessionUsername = null
        ))

        assertFalse(feed.hasNextPage)
    }

    @Test
    fun acceptsOnlySameOriginNotificationContinuationUrls() {
        assertEquals(
            "https://myanimelist.net/notification?cursor=older",
            MalNotificationsClient.normalizeNotificationPageUrl("?cursor=older")
        )
        assertEquals(
            "https://www.myanimelist.net/notification/history?cursor=older",
            MalNotificationsClient.normalizeNotificationPageUrl(
                "https://www.myanimelist.net/notification/history?cursor=older"
            )
        )
        assertNull(MalNotificationsClient.normalizeNotificationPageUrl("https://example.org/notification?page=2"))
        assertNull(MalNotificationsClient.normalizeNotificationPageUrl("https://myanimelist.net/anime/1"))
        assertNull(MalNotificationsClient.normalizeNotificationPageUrl("javascript:alert(1)"))
    }

    @Test
    fun carriesTheRequestedPageInTheFeed() {
        val html = """<script>window.MAL.notification = {"items":[]};</script>"""
        val feed = requireNotNull(MalNotificationsClient.parseHtml(html, page = 2))

        assertEquals(2, feed.page)
        assertFalse(feed.hasNextPage)
    }

    @Test
    fun preservesOnlyAnExplicitSafeContinuationUrl() {
        val html = """<script>window.MAL.notification = {
            "items": [{"id": "notice-1"}],
            "pagination": {"next_url": "/notification?cursor=older"}
        };</script>"""

        val feed = requireNotNull(MalNotificationsClient.parseHtml(html))

        assertTrue(feed.hasNextPage)
        assertEquals("https://myanimelist.net/notification?cursor=older", feed.nextPageUrl)
    }

    @Test
    fun browserCanReportThatTheNativeFeedHasReachedItsEnd() {
        val payload = """{"items":[{"id":"notice-1"}],"historyItems":[]}"""

        val feed = requireNotNull(
            MalNotificationsClient.parseNotificationPayload(
                payload = payload,
                page = 2,
                sessionUsername = "MALUser",
                hasNextPageOverride = false
            )
        )

        assertFalse(feed.hasNextPage)
    }

    @Test
    fun treatsMangaLibraryTypesAsMangaNotAnime() {
        // "Novel"/"Manhwa" gibi türler anime sayıldığında manga kimliği anime aramasına
        // gidiyor ve detay sayfası aynı sayıya sahip başka bir animeyi açabiliyordu.
        fun payload(mediaType: String, url: String) = """
            <script>window.MAL.notification = {"items":[{
              "id": "notice-$mediaType",
              "typeIdentifier": "related_anime_add",
              "categoryName": "New Related Anime",
              "anime": {"title": "Some Light Novel", "mediaType": "$mediaType"},
              "url": "$url"
            }]};</script>
        """.trimIndent()

        assertEquals(
            "manga",
            requireNotNull(
                MalNotificationsClient.parseHtml(
                    payload("Light Novel", "https://myanimelist.net/manga/12345/Some")
                )
            ).items.single().mediaType
        )
        assertEquals(
            "manga",
            requireNotNull(
                MalNotificationsClient.parseHtml(
                    payload("Manhwa", "https://myanimelist.net/manga/12346/Some")
                )
            ).items.single().mediaType
        )
        assertEquals(
            "anime",
            requireNotNull(
                MalNotificationsClient.parseHtml(
                    payload("ONA", "https://myanimelist.net/anime/58447/Umayuru")
                )
            ).items.single().mediaType
        )
    }

    @Test
    fun detectsMangaFromTheUrlWhenThePayloadOmitsTheType() {
        val html = """
            <script>window.MAL.notification = {"items":[{
              "id": "notice-11",
              "typeIdentifier": "related_anime_add",
              "categoryName": "New Related Anime",
              "anime": {"title": "Some Manga"},
              "url": "https://myanimelist.net/manga/77777/Some_Manga"
            }]};</script>
        """.trimIndent()

        val item = requireNotNull(MalNotificationsClient.parseHtml(html)).items.single()

        assertEquals("manga", item.mediaType)
        assertEquals(77777, item.mediaId)
    }

    @Test
    fun exposesTheRealMediaTitleInsteadOfTheCategoryName() {
        // Kart başlığı MAL'ın kategori adıdır; detay sayfası medyanın gerçek adıyla açılmalı.
        val html = """
            <script>window.MAL.notification = {"items":[{
              "id": "notice-9",
              "typeIdentifier": "related_anime_add",
              "categoryName": "New Related Anime",
              "anime": {"title": "Umayuru: Full Gate!", "mediaType": "ONA"},
              "url": "https://myanimelist.net/anime/58447/Umayuru_Full_Gate"
            }]};</script>
        """.trimIndent()

        val item = requireNotNull(MalNotificationsClient.parseHtml(html)).items.single()

        assertEquals("New Related Anime", item.displayTitle())
        assertEquals("Umayuru: Full Gate!", item.mediaTitle)
        assertEquals(58447, item.mediaId)
        assertEquals("anime", item.mediaType)
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
