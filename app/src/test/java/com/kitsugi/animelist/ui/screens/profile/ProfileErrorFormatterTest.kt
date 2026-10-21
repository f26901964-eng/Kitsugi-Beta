package com.kitsugi.animelist.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileErrorFormatterTest {

    @Test
    fun anilistRateLimitJsonIsReducedToItsStatusAndFriendlyCategory() {
        val summary = classifyProfileError(
            "AniList hatası: 429 {\"data\":null,\"errors\":[{\"message\":\"Too Many Requests\",\"status\":429}]}"
        )

        assertEquals(ProfileErrorKind.RATE_LIMIT, summary.kind)
        assertEquals(429, summary.httpStatusCode)
    }

    @Test
    fun commonHttpFailuresAreClassifiedWithoutKeepingTheRawPayload() {
        assertEquals(
            ProfileErrorKind.AUTHENTICATION,
            classifyProfileError("HTTP 401: invalid_token").kind
        )
        assertEquals(
            ProfileErrorKind.PERMISSION,
            classifyProfileError("HTTP 403 Forbidden").kind
        )
        assertEquals(
            ProfileErrorKind.NOT_FOUND,
            classifyProfileError("HTTP 404: Not Found").kind
        )
        assertEquals(
            ProfileErrorKind.SERVER,
            classifyProfileError("HTTP 503: Service Unavailable").kind
        )
        assertEquals(
            ProfileErrorKind.REQUEST_REJECTED,
            classifyProfileError("HTTP 400: Bad Request").kind
        )
    }

    @Test
    fun timeoutAndNetworkExceptionsGetShortUserFacingCategories() {
        assertEquals(
            ProfileErrorKind.TIMEOUT,
            classifyProfileError("java.net.SocketTimeoutException: timeout").kind
        )
        assertEquals(
            ProfileErrorKind.NETWORK,
            classifyProfileError("UnknownHostException: Unable to resolve host").kind
        )
    }

    @Test
    fun anilistTemporaryApiShutdownIsClassifiedAsServerNotPermission() {
        assertEquals(
            ProfileErrorKind.SERVER,
            classifyProfileError(
                "HTTP 403: The AniList API has been temporarily disabled due to severe stability issues."
            ).kind
        )
        assertEquals(
            ProfileErrorKind.SERVER,
            classifyProfileError(
                "TR — AniList API'si sunucu kararlılık sorunları nedeniyle geçici olarak devre dışı bırakıldı."
            ).kind
        )
    }

    @Test
    fun unrecognizedErrorsUseTheSafeGenericMessage() {
        val summary = classifyProfileError("Unexpected provider response: {large raw payload}")

        assertEquals(ProfileErrorKind.UNKNOWN, summary.kind)
        assertEquals(null, summary.httpStatusCode)
    }
}
