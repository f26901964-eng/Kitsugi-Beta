package com.kitsugi.animelist.core.player

import android.content.Context
import java.io.File
import java.security.KeyStore
import java.util.Base64

/** Export Android's trusted CAs for libmpv/FFmpeg, which cannot read AndroidCAStore. */
internal object MpvTrustStore {
    @Synchronized
    fun install(context: Context): File {
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val certificates = store.aliases().toList().sorted().mapNotNull { store.getCertificate(it) }
        check(certificates.isNotEmpty()) { "Android trust store is empty; refusing insecure playback" }
        val pem = buildString {
            for (certificate in certificates) {
                append("-----BEGIN CERTIFICATE-----\n")
                append(Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(certificate.encoded))
                append("\n-----END CERTIFICATE-----\n")
            }
        }
        val directory = File(context.filesDir, "mpv").apply { check(isDirectory || mkdirs()) }
        val target = File(directory, "android-ca.pem")
        if (!target.exists() || target.readText() != pem) {
            val temporary = File(directory, "android-ca.pem.tmp")
            temporary.writeText(pem)
            check(temporary.renameTo(target)) { "Unable to install MPV trust store" }
        }
        return target
    }
}
