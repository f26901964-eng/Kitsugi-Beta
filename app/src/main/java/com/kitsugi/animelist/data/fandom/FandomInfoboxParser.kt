package com.kitsugi.animelist.data.fandom

import org.json.JSONArray
import org.json.JSONObject

/**
 * Fandom `pageprops.infoboxes` alanını (PortableInfobox'ın hazır JSON hâli) ayrıştırır.
 *
 * Yapı (parser_tag_version 5):
 * ```
 * [ { "data": [
 *     { "type": "image",  "data": [ { "url": "…", "caption": "…" } ] },
 *     { "type": "title",  "data": { "value": "Sung Jinwoo" } },
 *     { "type": "group",  "data": { "value": [
 *         { "type": "header", "data": { "value": "Personal Information" } },
 *         { "type": "data",   "data": { "label": "Age", "value": "…", "source": "Age" } } ] } },
 *     { "type": "data",   "data": { "label": "Status", "value": "…", "source": "Status" } } ] } ]
 * ```
 * Değerler HTML içerebilir; [FandomHtmlText.inline] ile sadeleştirilir. Bilinmeyen türler
 * içindeki `value` dizileri de gezilir; böylece sekmeli (panel/section) kutular kaybolmaz.
 */
object FandomInfoboxParser {

    private const val MAX_IMAGES = 24
    private const val MAX_VALUE_LENGTH = 2_000
    private const val MAX_DEPTH = 6

    fun parse(infoboxesJson: String?, wikiId: String? = null): FandomInfobox? {
        if (infoboxesJson.isNullOrBlank()) return null
        val array = try {
            JSONArray(infoboxesJson)
        } catch (e: Exception) {
            return null
        }
        var best: FandomInfobox? = null
        var bestWeight = -1
        for (box in array.objects()) {
            val parsed = parseBox(box, wikiId)
            val titleWeight = if (parsed.title.isNullOrBlank()) 0 else 1
            val weight = parsed.allFields().size * 2 + parsed.images.size + titleWeight
            if (weight > bestWeight) {
                best = parsed
                bestWeight = weight
            }
        }
        return best?.takeUnless { it.isEmpty() }
    }

    private class Builder {
        var title: String? = null
        val images = mutableListOf<FandomImage>()
        private val groups = mutableListOf<FandomInfoboxGroup>()
        private var currentHeader: String? = null
        private val currentFields = mutableListOf<FandomInfoboxField>()

        fun header(text: String?) {
            if (currentFields.isNotEmpty()) closeGroup()
            currentHeader = text?.takeIf { it.isNotBlank() }
        }

        fun field(field: FandomInfoboxField) {
            currentFields += field
        }

        fun closeGroup() {
            if (currentFields.isNotEmpty()) {
                groups += FandomInfoboxGroup(currentHeader, currentFields.toList())
            }
            currentFields.clear()
            currentHeader = null
        }

        fun build(): FandomInfobox {
            closeGroup()
            return FandomInfobox(
                title = title,
                images = images.distinctBy { it.url }.take(MAX_IMAGES),
                groups = groups.toList()
            )
        }
    }

    private fun parseBox(box: JSONObject, wikiId: String?): FandomInfobox {
        val builder = Builder()
        walk(box.optJSONArray("data"), builder, depth = 0, wikiId = wikiId)
        return builder.build()
    }

    private fun walk(items: JSONArray?, builder: Builder, depth: Int, wikiId: String?) {
        if (items == null || depth > MAX_DEPTH) return
        for (item in items.objects()) {
            when (item.str("type")) {
                "title" -> {
                    if (builder.title.isNullOrBlank()) {
                        val value = FandomHtmlText.inline(item.optJSONObject("data")?.str("value"))
                        builder.title = value.takeIf { it.isNotBlank() }
                    }
                }
                "image" -> parseImages(item.opt("data"), builder)
                "header" -> builder.header(FandomHtmlText.inline(item.optJSONObject("data")?.str("value")))
                "data" -> parseField(item.optJSONObject("data"), wikiId)?.let { builder.field(it) }
                "group" -> {
                    builder.closeGroup()
                    walk(item.optJSONObject("data")?.optJSONArray("value"), builder, depth + 1, wikiId)
                    builder.closeGroup()
                }
                "navigation" -> Unit
                else -> when (val data = item.opt("data")) {
                    is JSONObject -> walk(data.optJSONArray("value"), builder, depth + 1, wikiId)
                    is JSONArray -> walk(data, builder, depth + 1, wikiId)
                    else -> Unit
                }
            }
        }
    }

    private fun parseField(data: JSONObject?, wikiId: String?): FandomInfoboxField? {
        if (data == null) return null
        return buildField(data.str("label"), data.str("value"), data.str("source"), wikiId)
    }

    /**
     * Tek bilgi kutusu satırı. JSON (`pageprops.infoboxes`) ve işlenmiş HTML (`aside.portable-infobox`)
     * yolları aynı kuralları (etiket temizliği, spoiler, bağlantı çıkarımı) paylaşsın diye ortaktır.
     */
    internal fun buildField(rawLabel: String?, rawValue: String?, source: String?, wikiId: String?): FandomInfoboxField? {
        val value = FandomHtmlText.inline(rawValue)
        if (value.isBlank()) return null
        val label = FandomHtmlText.inline(rawLabel).trimEnd(':', ' ').trim()
        val finalLabel = label.ifBlank {
            source?.replace('_', ' ')?.trim()?.replaceFirstChar { it.uppercaseChar() }.orEmpty()
        }
        if (finalLabel.isBlank()) return null
        val role = FandomRelationClassifier.forInfoboxLabel(finalLabel)
        val language = FandomRelationClassifier.languageHint(finalLabel)
        val links = wikiId?.let {
            FandomHtmlText.wikiLinks(rawValue, it, role = role, language = language)
        }.orEmpty()
        return FandomInfoboxField(
            label = finalLabel,
            value = value.take(MAX_VALUE_LENGTH),
            source = source,
            spoiler = FandomSpoilerRules.isSpoilerField(finalLabel, source),
            links = links
        )
    }

    private fun parseImages(data: Any?, builder: Builder) {
        when (data) {
            is JSONArray -> data.objects().forEach { addImage(it, builder) }
            is JSONObject -> addImage(data, builder)
            else -> Unit
        }
    }

    private fun addImage(obj: JSONObject, builder: Builder) {
        if (obj.optBoolean("isVideo", false)) return
        val url = FandomUrls.normalizeImageUrl(obj.str("url")) ?: return
        val caption = FandomHtmlText.inline(obj.str("caption"))
            .ifBlank { FandomHtmlText.inline(obj.str("alt")) }
            .takeIf { it.isNotBlank() }
        builder.images += FandomImage(url, caption)
    }
}
