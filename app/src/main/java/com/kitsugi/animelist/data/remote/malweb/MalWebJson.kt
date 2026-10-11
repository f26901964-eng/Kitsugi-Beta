package com.kitsugi.animelist.data.remote.malweb

import org.json.JSONArray
import org.json.JSONObject

/**
 * [MalWebParser] çıktısını Jikan v4 JSON şemasına çevirir. Böylece uygulamadaki mevcut Jikan
 * tüketicileri (profil, istatistik, favori, arkadaş ekranları) hiçbir değişiklik gerektirmeden
 * cihaz üzerindeki veriyi okuyabilir.
 */
object MalWebJson {

    private fun images(img: MalWebParser.Images, withSizes: Boolean): JSONObject {
        val jpg = JSONObject().put("image_url", img.imageUrl.ifEmpty { JSONObject.NULL })
        val webp = JSONObject().put("image_url", img.webpUrl.ifEmpty { JSONObject.NULL })
        if (withSizes && img.imageUrl.isNotEmpty()) {
            jpg.put("small_image_url", img.smallUrl).put("large_image_url", img.largeUrl)
            webp.put("small_image_url", img.smallUrl.replace(".jpg", ".webp"))
                .put("large_image_url", img.largeUrl.replace(".jpg", ".webp"))
        }
        return JSONObject().put("jpg", jpg).put("webp", webp)
    }

    private fun Any?.orNull(): Any = this ?: JSONObject.NULL

    fun statistics(p: MalWebParser.Profile): JSONObject {
        val a = p.animeStats
        val m = p.mangaStats
        return JSONObject()
            .put(
                "anime", JSONObject()
                    .put("days_watched", a.daysWatched)
                    .put("mean_score", a.meanScore)
                    .put("watching", a.watching)
                    .put("completed", a.completed)
                    .put("on_hold", a.onHold)
                    .put("dropped", a.dropped)
                    .put("plan_to_watch", a.planToWatch)
                    .put("total_entries", a.totalEntries)
                    .put("rewatched", a.rewatched)
                    .put("episodes_watched", a.episodesWatched)
            )
            .put(
                "manga", JSONObject()
                    .put("days_read", m.daysRead)
                    .put("mean_score", m.meanScore)
                    .put("reading", m.reading)
                    .put("completed", m.completed)
                    .put("on_hold", m.onHold)
                    .put("dropped", m.dropped)
                    .put("plan_to_read", m.planToRead)
                    .put("total_entries", m.totalEntries)
                    .put("reread", m.reread)
                    .put("chapters_read", m.chaptersRead)
                    .put("volumes_read", m.volumesRead)
            )
    }

    private fun favorite(f: MalWebParser.Favorite): JSONObject {
        val o = JSONObject()
            .put("mal_id", f.malId)
            .put("url", f.url)
            .put("images", images(f.image, withSizes = true))
        if (f.kind == "character" || f.kind == "person") {
            o.put("name", f.title)
        } else {
            o.put("title", f.title).put("type", f.type.orNull()).put("start_year", f.startYear.orNull())
        }
        return o
    }

    fun favorites(p: MalWebParser.Profile): JSONObject = JSONObject()
        .put("anime", JSONArray(p.favoriteAnime.map(::favorite)))
        .put("manga", JSONArray(p.favoriteManga.map(::favorite)))
        .put("characters", JSONArray(p.favoriteCharacters.map(::favorite)))
        .put("people", JSONArray(p.favoritePeople.map(::favorite)))

    private fun update(u: MalWebParser.Update): JSONObject {
        val o = JSONObject()
            .put(
                "entry", JSONObject()
                    .put("mal_id", u.malId)
                    .put("url", u.url)
                    .put("images", images(u.image, withSizes = true))
                    .put("title", u.title)
            )
            .put("score", u.score)
            .put("status", u.status)
            .put("date", u.dateIso.orNull())
        if (u.kind == "anime") {
            o.put("episodes_seen", u.progress.orNull()).put("episodes_total", u.total.orNull())
        } else {
            o.put("chapters_read", u.progress.orNull()).put("chapters_total", u.total.orNull())
        }
        return o
    }

    fun updates(p: MalWebParser.Profile): JSONObject = JSONObject()
        .put("anime", JSONArray(p.animeUpdates.map(::update)))
        .put("manga", JSONArray(p.mangaUpdates.map(::update)))

    /** `/users/{u}` (full=false) veya `/users/{u}/full` (full=true) `data` nesnesi. */
    fun user(p: MalWebParser.Profile, full: Boolean): JSONObject {
        val o = JSONObject()
            .put("mal_id", p.malId.orNull())
            .put("username", p.username)
            .put("url", p.url)
            .put("images", images(p.image, withSizes = false))
            .put("last_online", p.lastOnlineIso.orNull())
            .put("gender", p.gender.orNull())
            .put("birthday", p.birthdayIso.orNull())
            .put("location", p.location.orNull())
            .put("joined", p.joinedIso.orNull())
        if (full) {
            o.put("statistics", statistics(p))
                .put("favorites", favorites(p))
                .put("updates", updates(p))
                .put("about", p.aboutHtml.orNull())
                .put("external", JSONArray(p.external.map { JSONObject().put("name", it.name).put("url", it.url) }))
        }
        return o
    }

    fun friends(page: MalWebParser.FriendsPage, currentPage: Int): JSONObject {
        val data = JSONArray(page.friends.map { f ->
            JSONObject()
                .put(
                    "user", JSONObject()
                        .put("username", f.username)
                        .put("url", f.url)
                        .put("images", images(f.image, withSizes = false))
                )
                .put("last_online", f.lastOnlineIso.orNull())
                .put("friends_since", f.friendsSinceIso.orNull())
        })
        return JSONObject()
            .put("pagination", JSONObject()
                .put("last_visible_page", if (page.hasNextPage) currentPage + 1 else currentPage)
                .put("has_next_page", page.hasNextPage))
            .put("data", data)
    }

    fun history(items: List<MalWebParser.HistoryItem>): JSONObject = JSONObject().put(
        "data", JSONArray(items.map { h ->
            JSONObject()
                .put(
                    "entry", JSONObject()
                        .put("mal_id", h.malId)
                        .put("type", h.kind)
                        .put("name", h.title)
                        .put("url", h.url)
                )
                .put("increment", h.increment)
                .put("date", h.dateIso.orNull())
        })
    )

    fun wrap(data: Any): String = JSONObject().put("data", data).toString()
}
