package com.kitsugi.animelist.data.remote

import android.content.Context
import android.os.Looper
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.core.memory.BoundedCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Bangumi **karakter / kişi** Latin adlarının kalıcı önbelleği (`bangumi_entity_names_v1`).
 *
 * Neden gerekli: karakter ve ekip listeleri (`/p1/subjects/{id}/characters`,
 * `/p1/subjects/{id}/staffs/persons`) yalnızca özgün (Japonca/Çince) `name` + `nameCN`
 * döndürür; romaji (`罗马字`) ve İngilizce (`英文名`) yalnızca **varlık başına**
 * `/p1/characters/{id}` · `/p1/persons/{id}` infobox'ında bulunur. Aynı seslendirmen
 * yüzlerce yapımda geçtiği için bu istekler tekrar tekrar atılıyor, Bangumi'nin 5 istek/sn
 * sınırıyla birlikte sekmeler dakikalarca açılmıyordu.
 *
 * Bu depo, bir kez çözülen adı **diske** yazar; uygulama yeniden açıldığında veya aynı kişi
 * başka bir yapımda göründüğünde hiç ağ isteği yapılmaz. Yalnızca olumlu (Latin karşılığı
 * gerçekten bulunmuş) kayıtlar saklanır; "bulunamadı" hükmü bellek içi TTL ile yönetilir ki
 * daha sonra eklenen bir infobox kalıcı olarak gölgelenmesin.
 *
 * Disk okuması tembel ve tek seferdir; ana iş parçacığında asla disk okunmaz.
 */
internal object BangumiEntityNameStore {

    private const val PREFS_NAME = "bangumi_entity_names_v1"
    private const val KEY_PREFIX = "n"
    private const val MAX_ENTRIES = 4_000

    private val memory = BoundedCache<String, BangumiLocalizedName>("bangumi.entityNameStore", MAX_ENTRIES)

    @Volatile
    private var loaded = false

    private val loadLock = Any()
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun appContext(): Context? = KitsugiApplication.getInstance()?.applicationContext

    private fun key(kind: String, id: Int): String = "$kind:$id"

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            val context = appContext()
            if (context == null) return // Uygulama bağlamı yoksa bellekte kal; bir sonraki çağrıda denenir.
            // Disk okuması UI thread'ine girmez. Looper bulunmayan thread'lerde (birim testi,
            // düz IO havuzu) bu denetim sessizce yok sayılır.
            if (runCatching { Looper.myLooper() == Looper.getMainLooper() }.getOrDefault(false)) return
            runCatching {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                for ((rawKey, rawValue) in prefs.all) {
                    if (!rawKey.startsWith(KEY_PREFIX)) continue
                    val storedKey = rawKey.removePrefix("$KEY_PREFIX|").takeIf { it != rawKey } ?: continue
                    val payload = rawValue as? String ?: continue
                    parse(storedKey, payload)?.let { memory[storedKey] = it }
                }
            }
            loaded = true
        }
    }

    private fun parse(storedKey: String, payload: String): BangumiLocalizedName? = runCatching {
        val json = JSONObject(payload)
        val english = json.optString("e").trim().takeIf { it.isNotEmpty() }
        val romaji = json.optString("r").trim().takeIf { it.isNotEmpty() }
        val native = json.optString("n").trim().takeIf { it.isNotEmpty() }
        val chinese = json.optString("c").trim().takeIf { it.isNotEmpty() }
        val display = romaji ?: english ?: native ?: chinese ?: return@runCatching null
        BangumiLocalizedName(
            display = display,
            english = english,
            romaji = romaji,
            native = native,
            chinese = chinese,
            alternatives = emptyList()
        )
    }.getOrNull()

    /** Kayıtlı Latin ad; yoksa `null`. Disk okuması yalnızca ilk çağrıda (IO thread) yapılır. */
    fun get(kind: String, id: Int): BangumiLocalizedName? {
        if (id <= 0) return null
        ensureLoaded()
        return memory[key(kind, id)]
    }

    /** Çözülen adı belleğe ve (IO kapsamında) diske yazar. */
    fun put(kind: String, id: Int, name: BangumiLocalizedName?) {
        if (id <= 0 || name == null) return
        val storedKey = key(kind, id)
        ensureLoaded()
        memory[storedKey] = name
        val context = appContext() ?: return
        val payload = JSONObject().apply {
            name.romaji?.let { put("r", it) }
            name.english?.let { put("e", it) }
            name.native?.let { put("n", it) }
            name.chinese?.let { put("c", it) }
        }.toString()
        writeScope.launch {
            runCatching {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()
                if (prefs.all.size >= MAX_ENTRIES) editor.clear()
                editor.putString("$KEY_PREFIX|$storedKey", payload)
                editor.apply()
            }
        }
    }
}
