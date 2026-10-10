package com.kitsugi.animelist.data.manga

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsugi.animelist.data.manga.loader.MangaCache
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class MangaCacheTest {
    private val cache = MangaCache(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test fun rejectsHtmlAndEmptyResponses() {
        for (payload in listOf("", "<html>Cloudflare challenge</html>")) {
            val url = "https://cache-test.invalid/${System.nanoTime()}.jpg"
            try {
                cache.putImageToCache(url, payload.byteInputStream())
                fail("Invalid image must not be cached")
            } catch (_: IOException) {
                assertFalse(cache.isImageInCache(url))
            }
        }
    }

    @Test fun validImageRoundTripsAndInvalidReplacementPreservesIt() {
        val url = "https://cache-test.invalid/${System.nanoTime()}.png"
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        try {
            cache.putImageToCache(url, bytes.inputStream())
            assertTrue(cache.isImageInCache(url))
            try {
                cache.putImageToCache(url, "<html>Error</html>".byteInputStream())
                fail("Invalid replacement must throw")
            } catch (_: IOException) { /* Original image must survive. */ }
            assertArrayEquals(bytes, cache.getImageFile(url).readBytes())
        } finally {
            cache.getImageFile(url).delete()
        }
    }

    @Test fun evictsPreviouslyCachedHtml() {
        val url = "https://cache-test.invalid/${System.nanoTime()}.jpg"
        val file = cache.getImageFile(url)
        file.writeText("<html>Access denied</html>")
        assertFalse(cache.isImageInCache(url))
        assertFalse(file.exists())
    }
}
