package com.kitsugi.animelist.ui.screens.fullscreen.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class PreviewGenerator(
    private val context: Context,
    private val videoUrl: String,
    private val headers: Map<String, String> = emptyMap()
) {
    companion object {
        private const val TAG = "PreviewGenerator"
        private const val PREVIEW_WIDTH = 160
        private const val PREVIEW_HEIGHT = 90
        private const val MAX_PREVIEWS = 120
        private const val MIN_INTERVAL_MS = 5000L
        // NOT: Önizleme önbelleği bütçesi (150 MB / 30 gün) artık StorageJanitor'a aittir.
        // Eskiden burada, HER PreviewGenerator örneği oluşturulduğunda tüm `filesDir/previews`
        // ağacı gezilip boyut toplanıyordu; aynı anda Coil decode + Room yazımı ile disk yarışına
        // girip kaydırma karelerini düşürüyordu. Tek merkezden, günde bir kez yapılır.
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var extractJob: Job? = null

    private val urlHash = md5(videoUrl + headers.toSortedMap().toString())
    private val cacheDir = File(File(context.filesDir, "previews"), urlHash)

    private var durationMs: Long = 0L
    private var totalFrames: Int = 0
    private var stepMs: Long = 0L

    // In-memory cache of decoded Bitmaps
    private var images = Array<Bitmap?>(0) { null }
    private var isInitialized = false

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun start(playerDurationMs: Long) {
        if (playerDurationMs <= 0) return
        if (isInitialized) return

        durationMs = playerDurationMs
        // Calculate number of frames to extract: every 10s, but bounded between 10 and MAX_PREVIEWS
        val calculatedFrames = (durationMs / 10000L).toInt()
        totalFrames = calculatedFrames.coerceIn(10, MAX_PREVIEWS)
        stepMs = durationMs / totalFrames

        images = Array(totalFrames) { null }
        isInitialized = true

        extractJob = scope.launch {
            try {
                // If cache directory already has completed thumbnails, we don't need to extract
                val files = cacheDir.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") }
                val cachedCount = files?.size ?: 0
                
                // If we have at least 80% of target frames cached, reuse them
                if (cachedCount >= totalFrames * 0.8) {
                    Log.d(TAG, "Cache hit: found $cachedCount cached preview frames for $urlHash")
                    return@launch
                }

                Log.d(TAG, "Cache miss or incomplete: extracting $totalFrames frames for $urlHash")
                
                val retriever = MediaMetadataRetriever()
                try {
                    val uri = android.net.Uri.parse(videoUrl)
                    when (uri.scheme?.lowercase()) {
                        "content" -> retriever.setDataSource(context, uri)
                        "http", "https" -> retriever.setDataSource(videoUrl, headers)
                        "file" -> retriever.setDataSource(uri.path)
                        else -> retriever.setDataSource(videoUrl)
                    }

                    // Verify duration from media details if possible
                    val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    if (metaDuration != null && metaDuration > 0) {
                        durationMs = metaDuration
                        stepMs = durationMs / totalFrames
                    }

                    for (i in 0 until totalFrames) {
                        if (!isActive) break

                        val frameFile = File(cacheDir, "$i.jpg")
                        if (frameFile.exists() && frameFile.length() > 0) {
                            continue
                        }

                        val timeUs = (i * stepMs) * 1000L
                        val bitmap = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                retriever.getScaledFrameAtTime(
                                    timeUs,
                                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                    PREVIEW_WIDTH,
                                    PREVIEW_HEIGHT
                                )
                            } else {
                                val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                raw?.let {
                                    val scaled = Bitmap.createScaledBitmap(it, PREVIEW_WIDTH, PREVIEW_HEIGHT, true)
                                    if (scaled !== it) {
                                        it.recycle()
                                    }
                                    scaled
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to extract frame at $timeUs us: ${e.message}")
                            null
                        }

                        if (bitmap != null) {
                            // Save to cache dir
                            try {
                                FileOutputStream(frameFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to save frame $i to cache", e)
                            }

                            // Keep in-memory if near start, or recycle to save memory
                            // This bitmap was never exposed to UI, so recycling here is safe
                            bitmap.recycle()
                        }
                    }
                } finally {
                    runCatching { retriever.release() }
                }
                // Update directory lastModified so LRU knows it was recently active
                cacheDir.setLastModified(System.currentTimeMillis())
                Log.d(TAG, "Completed preview extraction for $urlHash")
            } catch (e: Exception) {
                Log.e(TAG, "Error in preview extraction loop", e)
            }
        }
    }

    fun getPreviewImage(fraction: Float): Bitmap? {
        if (!isInitialized || totalFrames <= 0) return null
        val idx = (fraction * totalFrames).toInt().coerceIn(0, totalFrames - 1)

        synchronized(images) {
            // 1. Check in-memory cache
            val memoryBmp = images[idx]
            if (memoryBmp != null && !memoryBmp.isRecycled) {
                return memoryBmp
            }

            // 2. Check disk cache
            val frameFile = File(cacheDir, "$idx.jpg")
            if (frameFile.exists() && frameFile.length() > 0) {
                try {
                    val bitmap = BitmapFactory.decodeFile(frameFile.absolutePath)
                    if (bitmap != null) {
                        images[idx] = bitmap
                        // Clean up other cached bitmaps to control RAM consumption
                        cleanMemoryCache(exceptIndex = idx)
                        return bitmap
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode cached frame $idx", e)
                }
            }
        }
        return null
    }

    private fun cleanMemoryCache(exceptIndex: Int) {
        // Keep up to 3 frames in memory (previous, current, next).
        // NOT: UI'ya (Compose ImageBitmap / RenderNode) teslim edilmiş bitmap'ler üzerinde
        // asla .recycle() ÇAĞIRMA — RenderThread o sırada önceki kareyi çiziyorsa
        // "Canvas: trying to use a recycled bitmap" veya libhwui.so SIGSEGV oluşur.
        // Referansı null yapmak GC / NativeAllocationRegistry için yeterlidir.
        for (i in images.indices) {
            if (i < exceptIndex - 1 || i > exceptIndex + 1) {
                images[i] = null
            }
        }
    }

    fun release() {
        extractJob?.cancel()
        scope.cancel()
        synchronized(images) {
            for (i in images.indices) {
                images[i] = null
            }
        }
        Log.d(TAG, "Released PreviewGenerator for $urlHash")
    }

    private fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

}
