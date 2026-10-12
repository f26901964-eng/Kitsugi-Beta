package com.kitsugi.animelist.data.remote.malweb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Cihaz üzeri MAL HTML ayrıştırıcısı testleri. HTML parçaları gerçek MAL sayfalarından
 * (jikan-fixtures / jikan-edge) kırpılmış, kullanıcı adları anonimleştirilmiştir.
 */
class MalWebParserTest {

    /** 11 Eki 2026 12:00 (Pasifik) — göreli tarihler buna göre çözülür. */
    private val now = ZonedDateTime.of(2026, 10, 11, 12, 0, 0, 0, ZoneId.of("America/Los_Angeles"))
        .toInstant().toEpochMilli()

    private val profileHtml = """
        <html><head><title>
        TestUser&#039;s Profile - MyAnimeList.net
        </title></head><body>
        <div id="content"><div class="user-profile">
          <div class="user-image mb8">
            <img class="lazyload" data-src="https://cdn.myanimelist.net/s/common/userimages/abc_225w?s=772b9b">
          </div>
          <ul class="user-status border-top pb8 mb4">
            <li class="clearfix"><span class="user-status-title di-ib fl-l fw-b">Last Online</span><span class="user-status-data di-ib fl-r">Jul 15, 2:22 AM</span></li><li class="clearfix"><span class="user-status-title di-ib fl-l fw-b">Gender</span><span class="user-status-data di-ib fl-r">Male</span></li><li class="clearfix"><span class="user-status-title di-ib fl-l fw-b">Birthday</span><span class="user-status-data di-ib fl-r">Jul 14, 2000</span></li><li class="clearfix"><span class="user-status-title di-ib fl-l fw-b">Location</span><span class="user-status-data di-ib fl-r">Brazil</span></li><li class="clearfix"><span class="user-status-title di-ib fl-l fw-b">Joined</span><span class="user-status-data di-ib fl-r">May 17, 2016</span></li>
          </ul>
          <div class="user-profile-sns">
            <a href="http://steamcommunity.com/id/TestUser/" target="_blank" class="icon-profile-sns ww-bw">TestUser</a>
            <a href="https://myanimelist.net/rss.php?type=rw&u=TestUser">RSS</a>
          </div>
        </div>
        <div class="user-profile-about js-truncate-outer"><div class="profile-about-user js-truncate-inner">
          <table><tr><td><div class="word-break">Merhaba <b>MAL</b></div></td></tr></table>
        </div></div>
        <div class="stats anime">
          <div class="stat-score di-t w100 pt8">
            <div class="di-tc al pl8 fs12 fw-b"><span class="fn-grey2 fw-n">Days: </span>65.8</div>
            <div class="di-tc ar pr8 fs12 fw-b"><span class="fn-grey2 fw-n">Mean Score: </span><span class="score-label score-6">6.37</span></div>
          </div>
          <ul class="stats-status fl-l"><li class="clearfix mb12"><a href="#" class="di-ib fl-l lh10 circle anime watching">Watching</a><span class="di-ib fl-r lh10">2</span></li><li class="clearfix mb12"><a href="#" class="di-ib fl-l lh10 circle anime completed">Completed</a><span class="di-ib fl-r lh10">273</span></li><li class="clearfix mb12"><a href="#" class="circle anime on_hold">On-Hold</a><span class="di-ib fl-r lh10">8</span></li><li class="clearfix mb12"><a href="#" class="circle anime dropped">Dropped</a><span class="di-ib fl-r lh10">39</span></li><li class="clearfix"><a href="#" class="circle anime plan_to_watch">Plan to Watch</a><span class="di-ib fl-r lh10">38</span></li></ul>
          <ul class="stats-data fl-r"><li class="clearfix mb12"><span class="di-ib fl-l fn-grey2">Total Entries</span><span class="di-ib fl-r">360</span></li><li class="clearfix mb12"><span class="di-ib fl-l fn-grey2">Rewatched</span><span class="di-ib fl-r">20</span></li><li class="clearfix mb12"><span class="di-ib fl-l fn-grey2">Episodes</span><span class="di-ib fl-r">4,111</span></li></ul>
        </div>
        <div class="stats manga">
          <div class="stat-score di-t w100 pt8">
            <div class="di-tc al pl8 fs12 fw-b"><span class="fn-grey2 fw-n">Days: </span>98.5</div>
            <div class="di-tc ar pr8 fs12 fw-b"><span class="fn-grey2 fw-n">Mean Score: </span><span class="score-label score-6">6.47</span></div>
          </div>
          <ul class="stats-status fl-l"><li><a href="#" class="circle manga reading">Reading</a><span>31</span></li><li><a href="#" class="circle manga completed">Completed</a><span>60</span></li><li><a href="#">Plan to Read</a><span>22</span></li></ul>
          <ul class="stats-data fl-r"><li><span class="fn-grey2">Total Entries</span><span>227</span></li><li><span class="fn-grey2">Reread</span><span>4</span></li><li><span class="fn-grey2">Chapters</span><span>13,936</span></li><li><span class="fn-grey2">Volumes</span><span>526</span></li></ul>
        </div>
        <div class="updates anime">
          <div class="statistics-updates di-b w100 mb8">
            <a href="https://myanimelist.net/anime/58567/Solo_Leveling_S2" class="fl-l di-ib mr8 image"><img class="lazyload" data-src="https://cdn.myanimelist.net/r/80x120/images/anime/1448/147351.jpg?s=0b2e"></a>
            <div class="data">
              <a href="https://myanimelist.net/anime/58567/Solo_Leveling_S2">Solo Leveling Season 2</a>
              <div class="clearfix graph-content pt8"><span class="fl-r fn-grey2">Apr 14, 2025 9:21 PM</span><div class="graph fl-l"></div></div>
              <div class="fn-grey2">
                Completed
                <span class="text anime completed">13</span>/13
                &middot; Scored
                <span class="text anime completed score-label score-6">6</span></div>
            </div>
          </div>
        </div>
        <div class="updates manga">
          <div class="statistics-updates di-b w100 mb8">
            <a href="https://myanimelist.net/manga/150000/Some_Manga" class="image"><img data-src="https://cdn.myanimelist.net/r/80x120/images/manga/1/1.jpg?s=1"></a>
            <div class="data">
              <a href="https://myanimelist.net/manga/150000/Some_Manga">Some Manga</a>
              <div class="clearfix graph-content pt8"><span class="fl-r fn-grey2">3 hours ago</span></div>
              <div class="fn-grey2">Reading <span class="text manga reading">12</span>/- &middot; Scored <span class="text manga reading score-label score-0">-</span></div>
            </div>
          </div>
        </div>
        <div class="user-favorites">
          <div class="fav-slide-block mb12" id="anime_favorites"><ul class="fav-slide">
            <li class="btn-fav" title="Nodame Cantabile"><a href="https://myanimelist.net/anime/1698/Nodame_Cantabile" class="link bg-center"><span class="title fs10">Nodame Cantabile</span><span class="users">TV&middot;2007</span><img src="https://cdn.myanimelist.net/images/spacer.gif" data-src="https://cdn.myanimelist.net/r/140x220/images/anime/9/11986.jpg?s=e586" class="image lazyload"></a></li>
          </ul></div>
          <div class="fav-slide-block mb12" id="character_favorites"><ul class="fav-slide">
            <li class="btn-fav" title="Lelouch"><a href="https://myanimelist.net/character/417/Lelouch_Lamperouge" class="link bg-center"><span class="title fs10">Lamperouge, Lelouch</span><img data-src="https://cdn.myanimelist.net/r/140x220/images/characters/8/406163.jpg?s=1" class="image lazyload"></a></li>
          </ul></div>
        </div>
        </div></body></html>
    """.trimIndent()

    @Test
    fun parsesProfileBasics() {
        val p = MalWebParser.parseProfile(profileHtml, now)
        assertNotNull(p)
        p!!
        assertEquals("TestUser", p.username)
        assertEquals("https://myanimelist.net/profile/TestUser", p.url)
        assertEquals("https://cdn.myanimelist.net/s/common/userimages/abc_225w", p.image.imageUrl)
        assertEquals("Male", p.gender)
        assertEquals("Brazil", p.location)
        assertEquals("2000-07-14T00:00:00-07:00", p.birthdayIso)
        assertEquals("2016-05-17T00:00:00-07:00", p.joinedIso)
        assertEquals("2026-07-15T02:22:00-07:00", p.lastOnlineIso)
        assertEquals(listOf(MalWebParser.External("TestUser", "http://steamcommunity.com/id/TestUser/")), p.external)
        assertTrue(p.aboutHtml!!.contains("<b>MAL</b>"))
    }

    @Test
    fun parsesProfileStatistics() {
        val p = MalWebParser.parseProfile(profileHtml, now)!!
        val a = p.animeStats
        assertEquals(65.8, a.daysWatched, 0.001)
        assertEquals(6.37, a.meanScore, 0.001)
        assertEquals(2, a.watching)
        assertEquals(273, a.completed)
        assertEquals(8, a.onHold)
        assertEquals(39, a.dropped)
        assertEquals(38, a.planToWatch)
        assertEquals(360, a.totalEntries)
        assertEquals(20, a.rewatched)
        assertEquals(4111, a.episodesWatched)
        val m = p.mangaStats
        assertEquals(98.5, m.daysRead, 0.001)
        assertEquals(31, m.reading)
        assertEquals(22, m.planToRead)
        assertEquals(13936, m.chaptersRead)
        assertEquals(526, m.volumesRead)
        assertEquals(4, m.reread)
    }

    @Test
    fun parsesFavoritesAndUpdates() {
        val p = MalWebParser.parseProfile(profileHtml, now)!!
        val fav = p.favoriteAnime.single()
        assertEquals(1698, fav.malId)
        assertEquals("Nodame Cantabile", fav.title)
        assertEquals("TV", fav.type)
        assertEquals(2007, fav.startYear)
        assertEquals("https://cdn.myanimelist.net/images/anime/9/11986.jpg", fav.image.imageUrl)
        assertEquals(417, p.favoriteCharacters.single().malId)
        assertTrue(p.favoriteManga.isEmpty())

        val up = p.animeUpdates.single()
        assertEquals(58567, up.malId)
        assertEquals("Completed", up.status)
        assertEquals(13, up.progress)
        assertEquals(13, up.total)
        assertEquals(6, up.score)
        assertEquals("2025-04-14T21:21:00-07:00", up.dateIso)
        assertEquals("https://cdn.myanimelist.net/images/anime/1448/147351.jpg", up.image.imageUrl)

        val mu = p.mangaUpdates.single()
        assertEquals("Reading", mu.status)
        assertEquals(12, mu.progress)
        assertNull(mu.total)
        assertEquals(0, mu.score)
        assertEquals("2026-10-11T09:00:00-07:00", mu.dateIso)
    }

    @Test
    fun rejectsNonProfilePages() {
        assertNull(MalWebParser.parseProfile("<html><head><title>Just a moment...</title></head><body>captcha</body></html>", now))
    }

    private val friendsHtml = """
        <html><head><title>TestUser's Friends - MyAnimeList.net</title></head><body><div id="content">
        <div class="mt4 mb8"><a href="https://myanimelist.net/profile/TestUser/friends?p=2">Next</a></div>
        <div class="boxlist-container friend mb16">
          <div class="boxlist col-3">
            <div class="di-tc"><a href="https://myanimelist.net/profile/Alice"><img class="lazyload image" src="https://cdn.myanimelist.net/images/spacer.gif" data-src="https://cdn.myanimelist.net/images/userimages/4601909.jpg?t=1664643600" alt="Alice" /></a></div>
            <div class="di-tc va-t pl8 data">
              <div class="title"><a href="https://myanimelist.net/profile/Alice">Alice</a></div>
              <div class="di-ib mt4 fn-grey2">22 seconds ago</div>
            </div>
          </div>
          <div class="boxlist col-3">
            <div class="di-tc"><a href="https://myanimelist.net/profile/Bob"><img class="lazyload image" data-src="https://cdn.myanimelist.net/images/userimages/thumbs/1836432_thumb.jpg?t=1" alt="Bob" /></a></div>
            <div class="di-tc va-t pl8 data">
              <div class="title"><a href="https://myanimelist.net/profile/Bob">Bob</a></div>
              <div class="di-ib mt4 fn-grey2">10 minutes ago</div>
              <div class="di-ib mt4 fn-grey2">Friends since May 28, 2014 5:41 AM</div>
            </div>
          </div>
        </div></div></body></html>
    """.trimIndent()

    @Test
    fun parsesFriends() {
        val page = MalWebParser.parseFriends(friendsHtml, now)!!
        assertTrue(page.hasNextPage)
        assertEquals(listOf("Alice", "Bob"), page.friends.map { it.username })
        val bob = page.friends[1]
        assertEquals("https://cdn.myanimelist.net/images/userimages/1836432.jpg", bob.image.imageUrl)
        assertEquals("2026-10-11T11:50:00-07:00", bob.lastOnlineIso)
        assertEquals("2014-05-28T05:41:00-07:00", bob.friendsSinceIso)
        assertNull(page.friends[0].friendsSinceIso)
    }

    private val historyHtml = """
        <html><head><title>TestUser's History - MyAnimeList.net</title></head><body><div id="content">
        <div><table border="0" cellpadding="0" cellspacing="0" width="100%">
          <tr><td colspan="2"><div class="normal_header">Today <span><small>(2)</small></span></div></td></tr>
          <tr><td class="borderClass"><a href="/anime.php?id=1676">
                  Cosmo Warrior Zero
                </a>
                  ep.
                  <strong>4</strong>
              </td><td class="borderClass" align="right">&nbsp;7 hours ago
              </td></tr>
          <tr><td class="borderClass"><a href="/manga.php?id=13">One Piece</a> chap. <strong>1058</strong></td><td class="borderClass" align="right">&nbsp;Jan 11, 6:54 AM <a href="#">Remove</a></td></tr>
          <tr><td colspan="2">&nbsp;</td></tr>
        </table></div></div></body></html>
    """.trimIndent()

    @Test
    fun parsesHistory() {
        val items = MalWebParser.parseHistory(historyHtml, now)!!
        assertEquals(2, items.size)
        val first = items[0]
        assertEquals(1676, first.malId)
        assertEquals("anime", first.kind)
        assertEquals("Cosmo Warrior Zero", first.title)
        assertEquals(4, first.increment)
        assertEquals("2026-10-11T05:00:00-07:00", first.dateIso)
        val second = items[1]
        assertEquals(13, second.malId)
        assertEquals("manga", second.kind)
        assertEquals(1058, second.increment)
        assertEquals("2026-01-11T06:54:00-08:00", second.dateIso)
    }

    @Test
    fun parsesContentRatingDistribution() {
        val html = """
            <html><head><title>TestUser's Anime Statistics - MyAnimeList.net</title></head><body>
            <div id="statistics-anime-rating"><div class="stats-graph">
              <ul>
                <li><span class="rating">PG-13 - Teens 13 or older</span><span class="count">1,234</span><span class="percentage">72.5%</span></li>
                <li><span class="rating">R - 17+ (violence &amp; profanity)</span><span class="count">42</span><span class="percentage">2.5%</span></li>
              </ul>
            </div></div></body></html>
        """.trimIndent()

        val stats = MalWebParser.parseDetailedStatistics(html, isAnime = true)!!
        assertEquals(listOf("PG-13 - Teens 13 or older", "R - 17+ (violence & profanity)"), stats.ratingDistribution.map { it.rating })
        assertEquals(listOf(1234, 42), stats.ratingDistribution.map { it.count })
        assertEquals(listOf(72.5, 2.5), stats.ratingDistribution.map { it.percentage })
    }

    @Test
    fun parsesDates() {
        assertEquals("2026-10-11T12:00:00-07:00", MalWebDates.toIso("Now", now))
        assertEquals("2026-10-10T20:55:00-07:00", MalWebDates.toIso("Yesterday, 8:55 PM", now))
        assertEquals("2026-10-11T08:01:00-07:00", MalWebDates.toIso("Today, 8:01 AM", now))
        assertEquals("2026-10-09T12:00:00-07:00", MalWebDates.toIso("2 days ago", now))
        // Yıl yok ve tarih gelecekte kalıyorsa geçen yıl kabul edilir.
        assertEquals("2025-12-24T10:00:00-08:00", MalWebDates.toIso("Dec 24, 10:00 AM", now))
        assertNull(MalWebDates.toIso("Unknown", now))
        assertNull(MalWebDates.toIso("", now))
        assertFalse(MalWebDates.toIso("Oct 1, 2020", now).isNullOrEmpty())
    }
}
