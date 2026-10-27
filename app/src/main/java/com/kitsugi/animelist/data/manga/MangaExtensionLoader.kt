package com.kitsugi.animelist.data.manga

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.kitsugi.animelist.core.security.TrustManager
import com.kitsugi.animelist.data.manga.loader.ChildFirstPathClassLoader
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import uy.kohesive.injekt.Injekt
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * MangaExtensionLoader
 *
 * Manga eklentilerini dinamik olarak yükleyen motor.
 *
 * İKİ FORMAT DESTEKLENİR:
 *
 * 1) Mihon/Tachiyomi APK (.apk)
 *    - AndroidManifest.xml içindeki meta-data "tachiyomi.extension.class" alanından
 *      sınıf ismi okunur.
 *    - Yüklenen sınıf eu.kanade.tachiyomi.source.Source interface'ini implement eder.
 *    - MihonSourceWrapper aracılığıyla Kitsugi MangaSource'a dönüştürülür.
 *
 * 2) Kitsugi Native (.mex)
 *    - ZIP içindeki manifest.json dosyasındaki "sourceClassName" alanından
 *      sınıf ismi okunur.
 *    - Yüklenen sınıf doğrudan MangaSource interface'ini implement eder.
 *
 * Keiyoushi gibi repolardan indirilen APK'lar Format 1 ile desteklenir.
 */
object MangaExtensionLoader {

    private const val TAG = "MangaExtensionLoader"

    // Mihon APK'larında AndroidManifest.xml meta-data anahtarı
    private const val MIHON_EXTENSION_META_KEY = "tachiyomi.extension.class"

    /** Yüklü kaynak örneklerini tutar: filePath -> List<MangaSource> (birden fazla source içerebilir) */
    private val loadedSources = ConcurrentHashMap<String, List<MangaSource>>()

    /** Yükleme tamamlandığında (herhangi bir değişiklikte) çağrılan dinleyiciler. */
    private val loadListeners = mutableListOf<() -> Unit>()

    @Synchronized
    fun addLoadListener(listener: () -> Unit) {
        loadListeners += listener
    }

    @Synchronized
    fun removeLoadListener(listener: () -> Unit) {
        loadListeners -= listener
    }

    @Synchronized
    fun notifyLoadComplete() {
        loadListeners.toList().forEach { listener ->
            runCatching { listener() }.onFailure { Log.w(TAG, "load listener error: ${it.message}") }
        }
    }

    fun getExternalSourcesSnapshot(): List<MangaSource> = loadedSources.values.flatten()

    // ─── Yüklü Kaynakları Sorgulama ─────────────────────────────────────────

    /** Returns repository-installed APK/MEX sources plus explicitly enabled Kotatsu sources. */
    fun getLoadedSources(): List<MangaSource> = buildList {
        addAll(loadedSources.values.flatten())
        addAll(KotatsuExtensionAdapter.getActiveSources())
    }.distinctBy { it.stableSourceKey() }

    fun getSource(extensionId: String, context: Context): MangaSource? {
        val apkFile = apkFile(context, extensionId)
        val mexFile = mexFile(context, extensionId)
        return loadedSources[apkFile.absolutePath]?.firstOrNull()
            ?: loadedSources[mexFile.absolutePath]?.firstOrNull()
    }

    fun getExtensionIdForSource(source: MangaSource): String? {
        val entry = loadedSources.entries.find { list -> list.value.any { it === source } } ?: return null
        return File(entry.key).nameWithoutExtension
    }

    // ─── İndirme ─────────────────────────────────────────────────────────────

    /**
     * Verilen URL'den eklentiyi indirir.
     * Dosya uzantısı .apk ise APK olarak, değilse .mex olarak kaydedilir.
     */
    fun downloadExtension(
        context: Context,
        extensionId: String,
        urlString: String
    ): Boolean {
        val dir = extensionDir(context).also { if (!it.exists()) it.mkdirs() }
        val isApk = urlString.substringBefore("?").endsWith(".apk", ignoreCase = true)
            || urlString.contains(".apk", ignoreCase = true)
            || extensionId.startsWith("eu_kanade_tachiyomi")
        val targetExt = if (isApk) "apk" else "mex"
        val temp = File(dir, "$extensionId.$targetExt.tmp")
        val target = File(dir, "$extensionId.$targetExt")

        return try {
            if (temp.exists()) temp.delete()

            val mirroredUrl = com.kitsugi.animelist.data.remote.MangaRepoClient.applyMirror(urlString)
            Log.d(TAG, "İndiriliyor [$targetExt]: $extensionId <- $mirroredUrl (original: $urlString)")
            
            val networkHelper = Injekt.get(NetworkHelper::class.java)
            val client = networkHelper.client

            var success = false
            var attempt = 1
            val maxAttempts = 4
            var delayMs = 1500L

            while (attempt <= maxAttempts && !success) {
                try {
                    val request = okhttp3.Request.Builder()
                        .url(mirroredUrl)
                        .header("User-Agent", networkHelper.defaultUserAgentProvider())
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.code == 429) {
                            val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: (delayMs / 1000L)
                            val sleepTime = (retryAfter * 1000L) + (Math.random() * 500).toLong()
                            Log.w(TAG, "$extensionId: HTTP 429 (Too Many Requests). $sleepTime ms bekleniyor... (Deneme $attempt/$maxAttempts)")
                            Thread.sleep(sleepTime)
                            attempt++
                            delayMs *= 2
                        } else if (!response.isSuccessful) {
                            Log.e(TAG, "$extensionId indirme hatası: HTTP ${response.code} (Deneme $attempt/$maxAttempts)")
                            Thread.sleep(delayMs)
                            attempt++
                            delayMs *= 2
                        } else {
                            response.body?.byteStream()?.use { inp ->
                                FileOutputStream(temp).use { out -> inp.copyTo(out) }
                            }
                            success = true
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "$extensionId bağlantı hatası (Deneme $attempt/$maxAttempts): ${e.message}")
                    if (attempt == maxAttempts) throw e
                    Thread.sleep(delayMs)
                    attempt++
                    delayMs *= 2
                }
            }

            if (!success) {
                Log.e(TAG, "$extensionId: Tüm indirme denemeleri başarısız oldu.")
                return false
            }

            // ZIP/APK geçerliliği kontrolü
            val valid = try { ZipFile(temp).use { true } } catch (_: Exception) { false }
            if (!valid) {
                Log.e(TAG, "$extensionId: indirilen dosya geçerli bir ZIP/APK değil")
                temp.delete()
                return false
            }

            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                Log.e(TAG, "$extensionId: geçici dosya taşınamadı")
                temp.delete()
                return false
            }
            // NOT: setReadOnly() KALDIRILDI — Android 10+ PathClassLoader read-only dosyayı yükleyemez
            Log.i(TAG, "$extensionId başarıyla indirildi (${target.length()} bytes) [$targetExt]")
            true
        } catch (e: Exception) {
            Log.e(TAG, "$extensionId indirme hatası: ${e.message}", e)
            if (temp.exists()) temp.delete()
            false
        }
    }

    // ─── Yükleme ─────────────────────────────────────────────────────────────

    /**
     * İndirilen eklentiyi belleğe yükler.
     * Önce .apk uzantılı Mihon formatını dener, bulamazsa .mex formatını dener.
     */
    fun loadExtension(context: Context, extensionId: String): MangaSource? {
        val apk = apkFile(context, extensionId)
        val mex = mexFile(context, extensionId)

        val result = when {
            apk.exists() -> loadMihonApk(context, extensionId, apk)
            mex.exists() -> loadNativeMex(context, extensionId, mex)
            else -> {
                Log.e(TAG, "$extensionId: ne .apk ne de .mex dosyası bulunamadı")
                null
            }
        }
        if (result != null) {
            notifyLoadComplete()
        }
        return result
    }

    /**
     * Mihon/Tachiyomi APK yükleyici.
     *
     * Adımlar:
     * 1) PackageManager ile APK içindeki AndroidManifest.xml meta-data'dan
     *    eu.kanade.tachiyomi.source.Source sınıf adını okur.
     * 2) PathClassLoader ile sınıfı yükler.
     * 3) NetworkHelper'ı Injekt'e kaydeder (HttpSource bu bağımlılığa ihtiyaç duyar).
     * 4) Source örneğini MihonSourceWrapper ile sarar ve döndürür.
     */
    private fun loadMihonApk(context: Context, extensionId: String, file: File): MangaSource? {
        // Önbellekten döndür (ilk source'u ver)
        loadedSources[file.absolutePath]?.let { return it.firstOrNull() }

        return try {
            // NOT: setReadOnly() kaldırıldı — Android 10+ PathClassLoader için dosya readable olmalı

            // AndroidManifest.xml meta-data'dan paket adı ve sınıf adını oku
            val (packageName, className) = readMihonClassNameFromManifest(context, file, extensionId)
                ?: return null

            // ─── T1.01 – Signature Check ──────────────────────────────────────────
            val signatureHash = TrustManager.getApkSignatureHash(context, file)
            if (signatureHash != null) {
                if (!TrustManager.isSignatureTrusted(context, signatureHash)) {
                    Log.e(TAG, "SecurityException: extension signature is not trusted: $signatureHash")
                    throw SecurityException("Signature untrusted for package $packageName: $signatureHash")
                }
            } else {
                Log.w(TAG, "Unsigned APK uyarısı ($extensionId): imza doğrulanamadı, güven listesi atlanıyor")
            }

            Log.d(TAG, "$extensionId [APK]: paket=$packageName, sınıf yükleniyor -> $className")

            // ChildFirstPathClassLoader
            val loader = ChildFirstPathClassLoader(file.absolutePath, null, context.classLoader)

            // NetworkHelper'ı Injekt'e kaydet (HttpSource lazy inject eder)
            try {
                Injekt.import<android.app.Application>(context.applicationContext as android.app.Application)
                Injekt.import<eu.kanade.tachiyomi.network.NetworkHelper>(
                    Injekt.get(eu.kanade.tachiyomi.network.NetworkHelper::class.java)
                )
                Injekt.import<kotlinx.serialization.json.Json>(
                    Injekt.get(kotlinx.serialization.json.Json::class.java)
                )
            } catch (_: Exception) { /* Zaten kayıtlıysa hata verir, önemli değil */ }

            // Sınıfları yükle (Mihon'da sınıflar ';' ile ayrılabilir)
            val classNames = className.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val sources = classNames.flatMap { name ->
                try {
                    val clazz = loader.loadClass(name)
                    when {
                        SourceFactory::class.java.isAssignableFrom(clazz) -> {
                            val factory = clazz.getDeclaredConstructor().newInstance() as SourceFactory
                            factory.createSources()
                        }
                        else -> {
                            val instance = try {
                                clazz.fields.firstOrNull { it.name == "INSTANCE" && java.lang.reflect.Modifier.isStatic(it.modifiers) }
                                    ?.get(null) as? Source
                                    ?: clazz.getDeclaredConstructor(Context::class.java).newInstance(context) as Source
                            } catch (_: NoSuchMethodException) {
                                clazz.getDeclaredConstructor().newInstance() as Source
                            }
                            listOfNotNull(instance)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sınıf yükleme hatası ($name): ${e.message}", e)
                    emptyList()
                }
            }

            if (sources.isEmpty()) {
                Log.e(TAG, "$extensionId: kaynak listesi boş veya yüklenemedi")
                return null
            }

            Log.d(TAG, "$extensionId [APK]: ${sources.size} kaynak bulundu")

            // Engine tipini APK DEX'inden tespit et ve cache'le
            val engine = detectEngineFromApk(context, extensionId, file)
            Log.d(TAG, "$extensionId engine tespit: $engine")

            // TÜM source'ları wrap et — artık sadece 1 değil hepsi kaydediliyor
            val wrappers = sources.map { source ->
                applyDefaultPreferences(context, source)
                MihonSourceWrapper(source, context, packageName, engine)
            }

            loadedSources[file.absolutePath] = wrappers
            Log.i(TAG, "$extensionId [APK] yüklendi: ${wrappers.size} kaynak (pkg=$packageName): ${wrappers.joinToString { "${it.name}[${it.lang}]" }}")
            KitsugiMangaDefaultProfile.onExternalSourcesLoaded(context, wrappers)

            // YENİ YÜKLENEN KAYNAKLARIN ESKİ COOLDOWN/BROKEN STATE'İNİ TEMİZLE
            // Önceki broken build'den kalan "Broken" durumu yeni kaynakları arama dışı bırakır.
            // Her fresh load'da state sıfırlanır — bir sonraki başarılı aramada Healthy'ye döner.
            try {
                val stateStore = MangaSourceStateStore(context)
                wrappers.forEach { wrapper ->
                    val health = stateStore.getHealthStatus(wrapper)
                    if (health == com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Unknown &&
                        stateStore.getLastUpdatedAt(wrapper) == 0L
                    ) {
                        stateStore.setHealthStatus(wrapper, com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Healthy,
                            reason = "initial_install_default")
                        stateStore.recordOperationSuccess(wrapper, "load", 0L)
                    } else if (health != com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Disabled) {
                        stateStore.resetSource(wrapper)
                        stateStore.setHealthStatus(wrapper, com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Healthy,
                            reason = "fresh_load_reset")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "$extensionId: state sıfırlama hatası (kritik değil): ${e.message}")
            }

            wrappers.firstOrNull()

        } catch (e: SecurityException) {
            Log.e(TAG, "$extensionId [APK] signature verification failed: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$extensionId [APK] yükleme hatası: ${e.message}", e)
            null
        }
    }

    /**
     * Kitsugi native .mex yükleyici (eski format, manifest.json tabanlı).
     */
    private fun loadNativeMex(context: Context, extensionId: String, file: File): MangaSource? {
        loadedSources[file.absolutePath]?.let { return it.firstOrNull() }

        return try {
            val valid = try { ZipFile(file).use { true } } catch (_: Exception) { false }
            if (!valid) { Log.e(TAG, "$extensionId [MEX]: bozuk ZIP"); return null }
            // NOT: setReadOnly() kaldırıldı

            val loader = PathClassLoader(file.absolutePath, context.classLoader)
            val className = readSourceClassNameFromJson(loader, file, extensionId) ?: return null

            Log.d(TAG, "$extensionId [MEX]: sınıf yükleniyor -> $className")
            val clazz = loader.loadClass(className)
            val instance = clazz.getDeclaredConstructor().newInstance() as MangaSource

            loadedSources[file.absolutePath] = listOf(instance)
            Log.i(TAG, "$extensionId [MEX] yüklendi: ${instance.name} [${instance.lang}]")
            KitsugiMangaDefaultProfile.onExternalSourcesLoaded(context, listOf(instance))
            instance
        } catch (e: Exception) {
            Log.e(TAG, "$extensionId [MEX] yükleme hatası: ${e.message}", e)
            null
        }
    }

    // ─── Kaldırma / Silme ────────────────────────────────────────────────────

    fun unloadExtension(context: Context, extensionId: String) {
        val removed = loadedSources.remove(apkFile(context, extensionId).absolutePath) != null ||
            loadedSources.remove(mexFile(context, extensionId).absolutePath) != null
        Log.i(TAG, "$extensionId bellekten kaldırıldı")
        if (removed) notifyLoadComplete()
    }

    fun deleteExtension(context: Context, extensionId: String): Boolean {
        unloadExtension(context, extensionId)
        var deleted = false
        listOf(apkFile(context, extensionId), mexFile(context, extensionId)).forEach { file ->
            if (file.exists()) deleted = file.delete().also {
                Log.i(TAG, "${file.name} silindi: $it")
            }
        }
        return deleted
    }

    /**
     * Diskte kurulu TÜM manga eklentisi dosyalarını döner (.apk / .mex).
     *
     * "Toplu Sil" bu listeyi esas almak zorundadır: [getLoadedSources] yalnızca
     * belleğe başarıyla yüklenmiş kaynakları görür; devre dışı bırakılmış,
     * yüklenememiş ya da henüz taranmamış eklentiler orada yer almaz. Eski
     * uygulama görünür `sources` listesini tek tek siliyordu ve bu yüzden
     * dosyalar diskte kalıyordu (buton "çalışmıyor" sanılıyordu).
     */
    fun getInstalledExtensionFiles(context: Context): List<File> {
        val dir = extensionDir(context)
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles { file ->
            file.isFile && (file.name.endsWith(".apk", ignoreCase = true) || file.name.endsWith(".mex", ignoreCase = true))
        }
        return files?.toList() ?: emptyList()
    }

    /** Belleğe yüklenmiş eklentilerin "kimlik → kaynaklar" anlık görüntüsü. */
    fun getLoadedExtensions(): Map<String, List<MangaSource>> =
        loadedSources.mapKeys { (path, _) -> File(path).nameWithoutExtension }

    /**
     * Uygulama içine gömülü Kotatsu kaynakları. Bunların diske yazılmış bir
     * eklenti dosyası yoktur; `deleteExtension` onlar için her zaman `false`
     * döner. Toplu silme bu kaynakları geri getirilebilir şekilde devre dışı
     * bırakır (bkz. MangaSourceRepository.deleteAllExtensions).
     */
    fun getBuiltInSources(): List<MangaSource> = KotatsuExtensionAdapter.getActiveSources()

    /** Yüklü source sayısını döndürür (debug amaçlı) */
    fun getLoadedSourceCount(): Int = loadedSources.values.sumOf { it.size }

    /**
     * Tüm .apk ve .mex eklentilerini tarayarak yükler.
     * isLoadedAll flag kaldırıldı — her dosya için cache kontrolü yapılır,
     * böylece yeni kurulan extensionlar her zaman doğru şekilde yüklenir.
     */
    @Synchronized
    fun loadAllExtensions(context: Context) {
        val dir = extensionDir(context)
        if (!dir.exists()) { dir.mkdirs(); return }

        val files = dir.listFiles { _, name ->
            name.endsWith(".mex", ignoreCase = true) || name.endsWith(".apk", ignoreCase = true)
        } ?: return

        val newFiles = files.filter { !loadedSources.containsKey(it.absolutePath) }
        if (newFiles.isEmpty()) {
            Log.d(TAG, "Tüm eklentiler zaten yüklü (${loadedSources.size} APK/MEX, ${getLoadedSourceCount()} kaynak)")
            notifyLoadComplete()
            return
        }

        Log.i(TAG, "${newFiles.size} yeni eklenti yükleniyor: ${dir.absolutePath}")
        for (file in newFiles) {
            val extensionId = file.nameWithoutExtension
            try { loadExtension(context, extensionId) }
            catch (e: Exception) { Log.e(TAG, "Eklenti yükleme hatası ($extensionId): ${e.message}", e) }
        }
        Log.i(TAG, "Toplam yüklü kaynak: ${getLoadedSourceCount()}")
        notifyLoadComplete()
    }

    // ─── Preference Varsayılanları ────────────────────────────────────────────

    /**
     * Bir kaynak [ConfigurableSource] ise, `setupPreferenceScreen` çağrılarak
     * androidx.preference'ın varsayılan değerleri kalıcı hale getirilmesi sağlanır.
     *
     * Nasıl çalışır:
     * - Kaynağın kendi SharedPreferences'ı (`getSourcePreferences()`) bir PreferenceManager'a
     *   bağlanır (reflection ile `preferenceDataStore` yerine SharedPreferences adı ayarlanır).
     * - Boş bir PreferenceScreen oluşturulur ve `setupPreferenceScreen(screen)` çağrılır.
     * - Kaynak, EditTextPreference vb. eklerken `setDefaultValue(...)` verir; ekleme sırasında
     *   androidx bu default'u ilgili SharedPreferences'a yazar.
     *
     * Hata olursa sessizce geçilir (kaynak yine yüklü kalır, sadece default yazılmamış olur).
     */
    private fun applyDefaultPreferences(context: Context, source: eu.kanade.tachiyomi.source.Source) {
        val configurable = source as? eu.kanade.tachiyomi.source.ConfigurableSource ?: return
        try {
            // Kaynağın kendi preference dosyası (source_<id>)
            val prefKey = "source_${source.id}"

            // androidx.preference.PreferenceManager'ın public constructor'ı yoktur;
            // Mihon/Aniyomi gibi reflection ile oluşturuyoruz.
            val pmClass = androidx.preference.PreferenceManager::class.java
            val ctor = pmClass.getDeclaredConstructor(Context::class.java)
            ctor.isAccessible = true
            val prefManager = ctor.newInstance(context)

            // Hangi SharedPreferences dosyasını kullanacağını ayarla (source_<id>)
            pmClass.getMethod("setSharedPreferencesName", String::class.java)
                .invoke(prefManager, prefKey)
            pmClass.getMethod("setSharedPreferencesMode", Int::class.javaPrimitiveType)
                .invoke(prefManager, Context.MODE_PRIVATE)

            val screen = pmClass.getMethod("createPreferenceScreen", Context::class.java)
                .invoke(prefManager, context) as androidx.preference.PreferenceScreen

            // Kaynak, screen'e kendi preference'larını (default değerleriyle) ekler;
            // androidx ekleme sırasında default'ları SharedPreferences'a yazar.
            configurable.setupPreferenceScreen(screen)

            Log.d(TAG, "Default preferences uygulandı: ${source.name} ($prefKey)")
        } catch (e: Exception) {
            Log.w(TAG, "Default preference uygulanamadı (${source.name}): ${e.message}")
        }
    }

    // ─── Engine Detection ─────────────────────────────────────────────────────

    /**
     * APK dosyasının DEX'indeki ASCII string tablosunu okuyarak
     * eklentinin hangi scraping motorunu kullandığını tespit eder.
     *
     * Bu yaklaşım jadx/dex2jar gibi araç gerektirmez; sadece ByteArray
     * üzerinde regex ile pattern matching yapar.
     *
     * Fingerprint öncelik sırası (en özgül → en genel):
     *   INERTIA > SVELTE > MADARA > THEMESIA > CUSTOM_HTML
     */
    private fun detectEngineFromApk(context: Context, extensionId: String, apkFile: File): ExtensionEngine {
        val cacheKey = "engine_$extensionId"
        val timeKey = "engine_time_$extensionId"
        val prefs = context.getSharedPreferences("manga_extension_engines", Context.MODE_PRIVATE)

        val lastModified = apkFile.lastModified()
        if (prefs.getLong(timeKey, 0L) == lastModified) {
            val cached = prefs.getString(cacheKey, null)
            if (cached != null) {
                val parsed = runCatching { ExtensionEngine.valueOf(cached) }.getOrNull()
                if (parsed != null) {
                    return parsed
                }
            }
        }

        val engine = try {
            ZipFile(apkFile).use { zip ->
                // Tüm dex dosyalarını tara (Keiyoushi 2026+ eklentilerde sınıflar
                // classes5.dex, classes13.dex vb. çoklu-dex dosyalarına dağılmış
                // olabilir; sadece classes.dex bakmak yanlış tespit üretir).
                val dexEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.matches(Regex("classes[0-9]*\\.dex")) }
                    .toList()
                if (dexEntries.isEmpty()) return@use ExtensionEngine.UNKNOWN
                val combined = buildString(dexEntries.sumOf { (it.size / 4).coerceAtMost(80_000).toInt() } + 16) {
                    for (entry in dexEntries) {
                        try {
                            zip.getInputStream(entry).use { ins ->
                                // Büyük dex'lerin tamamını okumaya gerek yok; ilk 80KB'lık kısım
                                // fingerprint string'lerini içermeye yeter.
                                val buf = ByteArray(entry.size.coerceAtMost(80_000L).toInt())
                                val read = ins.read(buf)
                                if (read > 0) append(String(buf, 0, read, Charsets.ISO_8859_1))
                            }
                        } catch (_: Exception) { /* bir dex okunamazsa diğerlerine devam et */ }
                    }
                }

                when {
                    // Inertia.js — en spesifik, önce kontrol et
                    "InertiaDto" in combined || "X-Inertia" in combined ||
                    ("inertia" in combined && "mangadenizi" in combined) ->
                        ExtensionEngine.INERTIA

                    // SvelteKit — __data.json veya SvelteNode varlığı
                    "SvelteNode" in combined || "SvelteResponse" in combined ||
                    "__data.json" in combined || ("__data" in combined && "cdn-u.efsaneler" in combined) ->
                        ExtensionEngine.SVELTE

                    // Madara — WP-Manga Plugin spesifik string'ler
                    "wpmangaprotectornonce" in combined || "madara_load_more" in combined ||
                    "wp_manga_chapter_type" in combined ->
                        ExtensionEngine.MADARA

                    // Themesia — WP Manga Themesia spesifik
                    "themesia" in combined || "ts_reader" in combined ||
                    "MangaThemesia" in combined ->
                        ExtensionEngine.THEMESIA

                    // Keiyoushi — Modern KeiSource / Keiyoushi core mimarisi
                    "keiyoushi" in combined || "KeiSource" in combined ->
                        ExtensionEngine.KEI_SOURCE

                    // Diğer tüm HTML tabanlı extensionlar
                    else -> ExtensionEngine.CUSTOM_HTML
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Engine detection başarısız (${apkFile.name}): ${e.message}")
            ExtensionEngine.UNKNOWN
        }

        if (engine != ExtensionEngine.UNKNOWN) {
            prefs.edit()
                .putString(cacheKey, engine.name)
                .putLong(timeKey, lastModified)
                .apply()
        }

        return engine
    }

    // ─── Yardımcılar ─────────────────────────────────────────────────────────

    fun extensionDir(context: Context) = File(context.filesDir, "manga_extensions")
    fun apkFile(context: Context, id: String) = File(extensionDir(context), "$id.apk")
    private fun mexFile(context: Context, id: String) = File(extensionDir(context), "$id.mex")

    /**
     * Kurulu APK dosyasından versionCode değerini okur.
     *
     * Mihon'daki PackageInfoCompat.getLongVersionCode() ile eşdeğer:
     *   API 28+ → longVersionCode (yüksek 32 bit versionCodeMajor + düşük 32 bit versionCode)
     *   API <28  → versionCode (int)
     *
     * @param pkg APK paket adı (noktalı). Örn: "eu.kanade.tachiyomi.extension.tr.mangatr"
     * @return Kurulu versionCode, dosya yoksa veya okunamazsa -1L
     */
    fun getInstalledVersionCode(context: Context, pkg: String): Long {
        val extensionId = pkg.replace(".", "_")
        val file = apkFile(context, extensionId)
        if (!file.exists()) return -1L
        return try {
            val pm = context.packageManager
            val flags = PackageManager.GET_META_DATA
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, flags)
            } ?: return -1L
            // API 28+: longVersionCode; API <28: versionCode (int → long)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            Log.w(TAG, "getInstalledVersionCode hata ($pkg): ${e.message}")
            -1L
        }
    }

    /**
     * PackageManager kullanarak APK içindeki AndroidManifest.xml'den
     * "tachiyomi.extension.class" meta-data değerini ve paket adını okur.
     *
     * @return Pair(packageName, resolvedClassName) veya hata durumunda null.
     *   - first  → APK'nın paket adı (örn. "eu.kanade.tachiyomi.extension.tr.mangadenizi")
     *   - second → Tam nitelikli sınıf adı (veya ';' ile ayrılmış liste)
     */
    private fun readMihonClassNameFromManifest(
        context: Context,
        file: File,
        extensionId: String
    ): Pair<String, String>? {
        return try {
            val pm = context.packageManager
            val flags = PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, flags)
            }
            if (packageInfo == null) {
                Log.e(TAG, "$extensionId: getPackageArchiveInfo null döndü. Dosya bozuk veya uyumsuz olabilir: ${file.absolutePath}")
                return null
            }
            val pkgName = packageInfo.packageName
            if (pkgName.isNullOrBlank()) {
                Log.e(TAG, "$extensionId: packageInfo.packageName null veya boş.")
                return null
            }
            val appInfo = packageInfo.applicationInfo
            if (appInfo == null) {
                Log.e(TAG, "$extensionId: packageInfo.applicationInfo null döndü.")
                return null
            }
            appInfo.let { info ->
                if (info.sourceDir == null) info.sourceDir = file.absolutePath
                if (info.publicSourceDir == null) info.publicSourceDir = file.absolutePath
            }
            val meta = appInfo.metaData
            if (meta == null) {
                Log.e(TAG, "$extensionId: applicationInfo.metaData null döndü. Manifest meta-data bulunamadı.")
                return null
            }
            val rawClassName = meta.getString(MIHON_EXTENSION_META_KEY)
            if (rawClassName.isNullOrBlank()) {
                Log.e(TAG, "$extensionId: '$MIHON_EXTENSION_META_KEY' meta-data bulunamadı veya boş.")
                return null
            }
            // "." ile başlıyorsa her parçanın önüne paket adı ekle (ör. ".MangaSource;.MangaSource2")
            val resolvedClass = rawClassName.split(";").joinToString(";") { part ->
                val trimmed = part.trim()
                if (trimmed.startsWith(".")) "$pkgName$trimmed" else trimmed
            }
            Log.d(TAG, "$extensionId manifest okundu -> pkg=$pkgName, class=$resolvedClass")
            Pair(pkgName, resolvedClass)
        } catch (e: Exception) {
            Log.e(TAG, "$extensionId: manifest okuma hatası: ${e.message}", e)
            null
        }
    }

    /**
     * manifest.json dosyasından "sourceClassName" alanını okur (MEX formatı).
     */
    private fun readSourceClassNameFromJson(
        loader: PathClassLoader,
        file: File,
        extensionId: String
    ): String? {
        var json: String? = null

        try {
            loader.getResourceAsStream("manifest.json")?.use {
                json = it.bufferedReader().readText()
            }
        } catch (_: Exception) {}

        if (json == null) {
            try {
                ZipFile(file).use { zip ->
                    zip.getEntry("manifest.json")?.let { entry ->
                        json = zip.getInputStream(entry).bufferedReader().readText()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "$extensionId: manifest.json okunamadı", e)
            }
        }

        if (json == null) { Log.e(TAG, "$extensionId: manifest.json bulunamadı"); return null }

        val raw = json!!
        val key = "\"sourceClassName\""
        val idx = raw.indexOf(key)
        if (idx < 0) { Log.e(TAG, "$extensionId: 'sourceClassName' alanı yok"); return null }
        val colon = raw.indexOf(':', idx + key.length)
        val start = raw.indexOf('"', colon + 1) + 1
        val end = raw.indexOf('"', start)
        if (start <= 0 || end <= start) { Log.e(TAG, "$extensionId: sourceClassName ayrıştırılamadı"); return null }
        return raw.substring(start, end).trim()
    }
}
