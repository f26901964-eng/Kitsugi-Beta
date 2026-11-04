package com.kitsugi.animelist.data.manga

import android.app.Application
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsugi.animelist.data.manga.loader.ChildFirstPathClassLoader
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import uy.kohesive.injekt.Injekt

/** Published v1.6.24 DEX with deterministic responses, not a live site test. */
class MangaTrPublishedApkTest {
    @Test fun publishedApkRunsThroughHostSearchDetailChapterPageAndImage() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        assumeTrue("The CI-downloaded, checksum-pinned APK fixture is absent", assets.list("")!!.contains("mangatr-fixture.apk"))
        val context = instrumentation.targetContext
        val apk = File(context.codeCacheDir, "mangatr-fixture.apk")
        // Delete a previous read-only file before writing again.
        apk.delete()
        assets.open("mangatr-fixture.apk").use { input -> apk.outputStream().use { input.copyTo(it) } }
        assertTrue(apk.setReadOnly()) // Android 14 disallows writable dynamically loaded DEX.

        Injekt.addSingleton(Application::class.java, context.applicationContext as Application)
        Injekt.addSingleton(NetworkHelper::class.java, NetworkHelper(context))
        Injekt.addSingleton(Json::class.java, Json { ignoreUnknownKeys = true })
        val loader = ChildFirstPathClassLoader(apk.absolutePath, null, context.classLoader)
        val plugin = loader.loadClass("keiyoushi.source.Generated").getDeclaredConstructor().newInstance() as HttpSource
        // Trigger the real KeiSource client setup and its interceptor-name checks first.
        val originalClient = plugin.client
        val bitmap = Bitmap.createBitmap(2, 4, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val seen = mutableListOf<String>()
        val fixtureClient = originalClient.newBuilder().addInterceptor { chain ->
            val request = chain.request()
            synchronized(seen) { seen.add(request.url.encodedPath) }
            val path = request.url.encodedPath
            val body = when {
                path == "/arama.html" -> """<div class="arama-card"><a class="arama-card__title" href="/manga-chainsaw-man.html">Chainsaw Man</a></div>""".toResponseBody("text/html; charset=utf-8".toMediaType())
                path == "/manga-chainsaw-man.html" -> """<h1 class="bento-hero-title">Chainsaw Man</h1><div id="results" data-manga="chainsaw-man"></div>""".toResponseBody("text/html; charset=utf-8".toMediaType())
                path == "/cek/fetch_pages_manga.php" -> """<article class="bento-ep-card"><a class="bento-ep-title-link" href="/id-1">1. Bölüm</a><a class="fansub-group-item" href="/id-2">Diğer Fansub</a></article>""".toResponseBody("text/html; charset=utf-8".toMediaType())
                path == "/id-1" || path == "/id-2" -> """<div class="reader"><img src="https://image.mngtr.site/page.png"></div>""".toResponseBody("text/html; charset=utf-8".toMediaType())
                path == "/page.png" -> png.toResponseBody("image/png".toMediaType())
                else -> error("Unexpected fixture request: $path")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build()
        // Inject only a test transport at the end of the plugin's real interceptor chain.
        // No production code or plugin implementation is replaced.
        var type: Class<*>? = plugin.javaClass
        while (type != null && type.name != "keiyoushi.source.KeiSource") type = type.superclass
        val clientDelegate = requireNotNull(type).getDeclaredField("client\$delegate")
        clientDelegate.isAccessible = true
        clientDelegate.set(plugin, lazyOf(fixtureClient))
        val source = MihonSourceWrapper(plugin, context, "eu.kanade.tachiyomi.extension.tr.mangatr")
        val search = source.fetchSearchManga(1, "Chainsaw Man")
        assertEquals("Chainsaw Man", search.mangas.single().title)
        val details = source.fetchMangaDetails("/manga-chainsaw-man.html")
        assertEquals("Chainsaw Man", details.title)
        assertEquals("/manga-chainsaw-man.html", details.url)
        val chapters = source.fetchChapterList("/manga-chainsaw-man.html")
        assertEquals(setOf("/id-1", "/id-2"), chapters.map { it.url }.toSet())
        val pages = source.fetchPageList(chapters.first())
        assertEquals("https://image.mngtr.site/page.png", pages.single().imageUrl)
        assertArrayEquals(png, source.getImage(pages.single()).use { it.readBytes() })
        assertTrue(seen.contains("/arama.html"))
        assertTrue(seen.contains("/cek/fetch_pages_manga.php"))
        assertTrue(seen.contains("/page.png"))
        Unit
    }
}
