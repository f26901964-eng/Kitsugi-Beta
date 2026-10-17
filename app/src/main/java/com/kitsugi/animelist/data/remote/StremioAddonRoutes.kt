package com.kitsugi.animelist.data.remote

import com.google.gson.Gson
import com.kitsugi.animelist.data.local.ManagedAddonEntity

/**
 * Room satırını ([ManagedAddonEntity]) saf yönlendirme modeline ([StremioAddonRoute]) çevirir.
 * Stream akışının iki girişi (AddonStreamRepository ve StreamViewModel) bu tek dönüşümü kullanır.
 */
fun ManagedAddonEntity.toStreamRoute(): StremioAddonRoute = StremioAddonRoute(
    name = name,
    manifestUrl = manifestUrl,
    streamTypes = streamTypes,
    idPrefixes = parseIdPrefixesJson(idPrefixes),
    subtitleTypes = subtitleTypes
)

/** `["tt","kitsu"]` JSON dizisini okur. Bozuk veya boş JSON → boş liste (kısıt yok). */
internal fun parseIdPrefixesJson(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        Gson().fromJson(json, Array<String>::class.java)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
}
