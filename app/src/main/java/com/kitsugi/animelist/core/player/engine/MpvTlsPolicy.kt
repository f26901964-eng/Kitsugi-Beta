package com.kitsugi.animelist.core.player.engine

/** Use libmpv's default trust store if no explicit CA path was supplied; never trust-all. */
internal fun mpvTlsOptions(caFilePath: String?): List<Pair<String, String>> = buildList {
    add("tls-verify" to "yes")
    if (!caFilePath.isNullOrBlank()) add("tls-ca-file" to caFilePath)
}

internal fun applyMpvTlsOptions(caFilePath: String? = null, setter: (String, String) -> Unit) {
    mpvTlsOptions(caFilePath).forEach { (name, value) -> setter(name, value) }
}
