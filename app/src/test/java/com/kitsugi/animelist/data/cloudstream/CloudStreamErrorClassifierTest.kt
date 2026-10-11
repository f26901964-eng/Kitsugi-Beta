package com.kitsugi.animelist.data.cloudstream

import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

class CloudStreamErrorClassifierTest {

    @Test
    fun classifiesProtectionFirstAndMapsCommonFailureTypes() {
        assertEquals(
            PlatformErrorCode.CloudStreamProtection,
            CloudStreamErrorClassifier.classify(IOException("HTTP 403 Cloudflare Just a moment")),
        )
        assertEquals(
            PlatformErrorCode.CloudStreamProtection,
            CloudStreamErrorClassifier.classify(CloudflareBlockException("challenge unavailable")),
        )
        assertEquals(
            PlatformErrorCode.CloudStreamRateLimited,
            CloudStreamErrorClassifier.classify(IOException("HTTP 429 Too Many Requests")),
        )
        assertEquals(
            PlatformErrorCode.CloudStreamPluginUnsupported,
            CloudStreamErrorClassifier.classify(IOException("HTTP 403 Forbidden")),
        )
        assertEquals(
            PlatformErrorCode.CloudStreamNetwork,
            CloudStreamErrorClassifier.classifyText("HTTP 503 Service Unavailable"),
        )
        assertEquals(
            PlatformErrorCode.CloudStreamPluginUnsupported,
            CloudStreamErrorClassifier.classify(NotImplementedError("search is unsupported")),
        )
        assertEquals(
            PlatformErrorCode.CloudStreamTimeout,
            CloudStreamErrorClassifier.classify(TimeoutException("request timed out")),
        )
        assertEquals(
            PlatformErrorCode.CloudStreamNetwork,
            CloudStreamErrorClassifier.classify(UnknownHostException("host not found")),
        )
        assertEquals(
            PlatformErrorCode.CloudStreamParser,
            CloudStreamErrorClassifier.classifyText("failed to parse provider HTML"),
        )
    }

    @Test
    fun emptyPluginResultsAndUnknownErrorsHaveStableCodes() {
        assertEquals(
            PlatformErrorCode.CloudStreamNoStreams,
            CloudStreamErrorClassifier.classifyText("Plugin completed", noStreams = true),
        )
        assertEquals(
            PlatformErrorCode.CloudStreamNoStreams,
            CloudStreamErrorClassifier.classifyText("Akış bulunamadı"),
        )
        assertEquals(
            PlatformErrorCode.CloudStreamNoStreams,
            CloudStreamErrorClassifier.classifyText("Arama sonucu yok"),
        )
        assertEquals(
            PlatformErrorCode.CloudStreamTimeout,
            CloudStreamErrorClassifier.classifyText("Zaman aşımı; sağlayıcı yanıt vermedi"),
        )
        assertEquals(
            PlatformErrorCode.CloudStreamUnknown,
            CloudStreamErrorClassifier.classify(IllegalStateException("unclassified provider failure")),
        )
    }
}
