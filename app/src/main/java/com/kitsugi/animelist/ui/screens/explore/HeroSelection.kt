package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.components.homeHeroBackdropUrl
import com.kitsugi.animelist.ui.components.homeHeroItemKey
import com.kitsugi.animelist.ui.components.isHomeHeroMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random

internal const val HERO_PER_SECTION_ALL = 1
internal const val HERO_PER_SECTION_SINGLE = 2

/** Source + shelf, not just an enum: TMDB/SIMKL reuse *_MANGA fields for film/TV shelves. */
internal data class HomeHeroSection(val key: String, val results: List<JikanSearchResult>)

internal data class HomeHeroPick(val sectionKey: String, val item: JikanSearchResult)

/** The countdown rail is a real hero pool too, but is only included once in ALL mode. */
internal fun homeHeroSections(
    sections: List<ExploreSourceSection>,
    airingSoon: List<JikanSearchResult> = emptyList()
): List<HomeHeroSection> = buildList {
    sections.forEach { section -> add(HomeHeroSection(section.key, section.results)) }
    if (airingSoon.isNotEmpty()) add(HomeHeroSection("shared_airing_soon", airingSoon))
}.map { section ->
    section.copy(results = section.results.filter(::isHomeHeroMedia).distinctBy(::homeHeroItemKey))
}.filter { it.results.isNotEmpty() }

/**
 * Assign unique titles to section slots. An augmenting path can move an earlier pick to
 * another candidate, so an overlapping popular/trending title cannot starve a small shelf.
 * First give every shelf one slot, then its second slot; never relax the per-shelf quota.
 */
internal fun matchHomeHeroSections(
    sections: List<HomeHeroSection>,
    itemsPerSection: Int
): List<HomeHeroPick> {
    if (itemsPerSection <= 0) return emptyList()
    val pools = sections.map { section ->
        section.results.mapNotNull { item ->
            homeHeroBackdropUrl(item)?.let { url ->
                if (url == item.backdropUrl) item else item.copy(backdropUrl = url)
            }
        }.distinctBy(::homeHeroItemKey)
    }
    val slotSections = buildList {
        repeat(itemsPerSection) {
            addAll(sections.indices.sortedBy { pools[it].size })
        }
    }
    val owners = mutableMapOf<String, Int>()
    val assignments = mutableMapOf<Int, JikanSearchResult>()

    fun assign(slot: Int, visited: MutableSet<String>): Boolean {
        for (item in pools[slotSections[slot]]) {
            val identity = homeHeroItemKey(item)
            if (!visited.add(identity)) continue
            val owner = owners[identity]
            if (owner == null || assign(owner, visited)) {
                owners[identity] = slot
                assignments[slot] = item
                return true
            }
        }
        return false
    }

    slotSections.indices.forEach { assign(it, mutableSetOf()) }
    return assignments.map { (slot, item) -> HomeHeroPick(sections[slotSections[slot]].key, item) }
}

/** Synchronous selection for already available horizontal artwork; no score bias or global cap. */
internal fun selectHeroItems(
    sections: List<ExploreSourceSection>,
    itemsPerSection: Int = HERO_PER_SECTION_SINGLE,
    airingSoon: List<JikanSearchResult> = emptyList(),
    random: Random = Random.Default
): List<JikanSearchResult> = matchHomeHeroSections(
    homeHeroSections(sections, airingSoon).map { it.copy(results = it.results.shuffled(random)) },
    itemsPerSection
).map { it.item }.shuffled(random)

/**
 * Resolve only as many randomly chosen candidates as each shelf needs. A failed/missing
 * backdrop tries another candidate instead of admitting its poster. Results (including
 * misses) are shared between overlapping shelves. Network work is bounded, cancellable,
 * and does not publish a half-prepared/reordered carousel on every image completion.
 *
 * [resolve] must return a decoded/verified horizontal image, or null. Keeping it injectable
 * makes fallback, concurrency, quotas and cancellation testable without any Android/network.
 */
internal suspend fun resolveHomeHeroItems(
    sections: List<HomeHeroSection>,
    itemsPerSection: Int,
    seed: Int,
    maxConcurrent: Int = 3,
    resolve: suspend (JikanSearchResult) -> JikanSearchResult?
): List<JikanSearchResult> = coroutineScope {
    if (itemsPerSection <= 0) return@coroutineScope emptyList()
    require(maxConcurrent > 0)
    val pools = sections.map { section ->
        section.copy(
            results = section.results.filter(::isHomeHeroMedia).distinctBy(::homeHeroItemKey)
                .shuffled(Random(seed xor section.key.hashCode()))
        )
    }
    val resolved = mutableMapOf<String, JikanSearchResult?>()
    val semaphore = Semaphore(maxConcurrent)
    while (true) {
        currentCoroutineContext().ensureActive()
        val readySections = pools.map { pool ->
            pool.copy(results = pool.results.mapNotNull { resolved[homeHeroItemKey(it)] })
        }
        val picks = matchHomeHeroSections(readySections, itemsPerSection)
        val counts = picks.groupingBy { it.sectionKey }.eachCount()
        val pending = LinkedHashMap<String, JikanSearchResult>()
        pools.forEach { pool ->
            var missing = itemsPerSection - (counts[pool.key] ?: 0)
            for (item in pool.results) {
                if (missing <= 0) break
                val key = homeHeroItemKey(item)
                if (resolved.containsKey(key)) continue
                pending[key] = item
                missing--
            }
        }
        if (pending.isEmpty()) {
            return@coroutineScope picks.map { it.item }.shuffled(Random(seed))
        }
        pending.map { (key, item) ->
            async {
                semaphore.withPermit {
                    val ready = try {
                        resolve(item)?.takeIf { homeHeroItemKey(it) == key && homeHeroBackdropUrl(it) != null }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    key to ready
                }
            }
        }.awaitAll().forEach { (key, item) -> resolved[key] = item }
    }
    @Suppress("UNREACHABLE_CODE")
    emptyList()
}
