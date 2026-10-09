package com.kitsugi.animelist.core.memory

/**
 * Boyutu sınırlı, iş parçacığı güvenli LRU bellek önbelleği.
 *
 * NEDEN VAR?
 * Uygulamada onlarca `object` içinde `ConcurrentHashMap` tabanlı önbellek vardı ve hiçbiri
 * boyut sınırı koymuyordu. Kullanıcı detay sayfaları, karakterler, galeriler arasında gezdikçe
 * bu haritalar süreç ölene kadar büyüyordu → heap dolup OOM / LMK (düşük bellek) ölümü.
 *
 * Bu sınıf `MutableMap` arayüzünü birebir uyguladığı için mevcut kodda yalnızca tanım satırı
 * değiştirilir (`cache[key]`, `remove`, `clear`, `containsKey`, `getOrPut` aynen çalışır).
 *
 * Özellikler:
 *  - [maxEntries] aşılınca en uzun süredir KULLANILMAYAN kayıt atılır (erişim sıralı LRU).
 *  - `null` değerleri destekler (ConcurrentHashMap desteklemez ve NPE fırlatır).
 *  - `keys` / `values` / `entries` anlık KOPYA döner → yineleme sırasında başka bir thread
 *    yazsa bile ConcurrentModificationException oluşmaz.
 *  - Oluşturulduğu anda [KitsugiMemoryGuard]'a kaydolur; sistem bellek baskısı bildirdiğinde
 *    otomatik küçülür / boşaltılır.
 */
class BoundedCache<K, V>(
    val name: String,
    maxEntries: Int,
) : MutableMap<K, V>, KitsugiMemoryGuard.Trimmable {

    @Volatile
    var maxEntries: Int = maxEntries.coerceAtLeast(1)
        private set

    private val lock = Any()

    private val map = object : LinkedHashMap<K, V>(16, 0.75f, /* accessOrder = */ true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > this@BoundedCache.maxEntries
    }

    init {
        KitsugiMemoryGuard.register(this)
    }

    // ── Okuma ────────────────────────────────────────────────────────────────
    override val size: Int get() = synchronized(lock) { map.size }
    override fun isEmpty(): Boolean = synchronized(lock) { map.isEmpty() }
    override fun containsKey(key: K): Boolean = synchronized(lock) { map.containsKey(key) }
    override fun containsValue(value: V): Boolean = synchronized(lock) { map.containsValue(value) }
    override fun get(key: K): V? = synchronized(lock) { map[key] }

    // ── Yazma ────────────────────────────────────────────────────────────────
    override fun put(key: K, value: V): V? = synchronized(lock) { map.put(key, value) }
    override fun putAll(from: Map<out K, V>) = synchronized(lock) { map.putAll(from) }
    override fun remove(key: K): V? = synchronized(lock) { map.remove(key) }
    override fun clear() = synchronized(lock) { map.clear() }


    // ── Görünümler: anlık kopya (CME'ye karşı güvenli) ─────────────────────────
    override val keys: MutableSet<K>
        get() = synchronized(lock) { LinkedHashSet(map.keys) }
    override val values: MutableCollection<V>
        get() = synchronized(lock) { ArrayList(map.values) }
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = synchronized(lock) { LinkedHashMap(map).entries }

    // ── Bellek baskısı ─────────────────────────────────────────────────────────
    /**
     * @param fraction 0.0 → tamamen boşalt, 0.5 → en eski yarısını at.
     */
    override fun trimTo(fraction: Float) {
        synchronized(lock) {
            if (fraction <= 0f) {
                map.clear()
                return
            }
            val target = (map.size * fraction).toInt()
            val it = map.entries.iterator()
            var toRemove = map.size - target
            while (toRemove > 0 && it.hasNext()) {
                it.next()
                it.remove()
                toRemove--
            }
        }
    }

    override fun trimName(): String = name

    override fun toString(): String = "BoundedCache($name, size=$size/$maxEntries)"
}

/** Boyutu sınırlı, iş parçacığı güvenli küme (ör. "başarısız id'ler" listeleri için). */
fun <T> boundedSet(name: String, maxEntries: Int): MutableSet<T> =
    java.util.Collections.newSetFromMap(
        java.util.Collections.synchronizedMap(
            object : LinkedHashMap<T, Boolean>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<T, Boolean>?) =
                    size > maxEntries
            }
        )
    ).also { set -> KitsugiMemoryGuard.register(KitsugiMemoryGuard.clearer(name) { set.clear() }) }
