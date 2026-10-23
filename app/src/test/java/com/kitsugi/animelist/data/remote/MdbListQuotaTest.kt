package com.kitsugi.animelist.data.remote

import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MDBList kota ayrıştırma ve geri sayım kuralları.
 *
 * Amaç: arayüzde sabit "1.000 / 1.000" değil, MDBList'in `X-RateLimit-*` başlıklarından
 * veya `/user` uç noktasından gelen gerçek kalan istek sayısının gösterilmesi.
 */
class MdbListQuotaTest {

    /** 2023-11-14 22:13:20 UTC — bir sonraki UTC gece yarısı 1700006400. */
    private val nowMillis = 1_700_000_000_000L
    private val nowSeconds = 1_700_000_000L

    @Test
    fun userQuotaUrlTargetsTheDocumentedEndpoint() {
        val url = requireNotNull(MdbListClient.buildUserUrl("  my key  "))

        assertEquals("https", url.scheme)
        assertEquals("api.mdblist.com", url.host)
        assertEquals("/user", url.encodedPath)
        assertEquals("my key", url.queryParameter("apikey"))
        assertNull(MdbListClient.buildUserUrl("   "))
    }

    @Test
    fun rateLimitHeadersBecomeAuthoritativeQuota() {
        val headers = Headers.headersOf(
            "X-RateLimit-Limit", "1000",
            "X-RateLimit-Remaining", "742",
            "X-RateLimit-Reset", "1700006400"
        )

        val quota = requireNotNull(MdbListQuotaParser.fromHeaders(headers, nowMillis))

        assertEquals(1000, quota.dailyLimit)
        assertEquals(742, quota.remaining)
        assertEquals(258, quota.used)
        assertEquals(1700006400L, quota.resetAtEpochSeconds)
        assertTrue(quota.authoritative)
    }

    @Test
    fun headersAreCaseInsensitiveAndSupportRateLimitVariant() {
        val headers = Headers.headersOf(
            "ratelimit-limit", "25000",
            "ratelimit-remaining", "24990",
            "ratelimit-reset", "1700006400"
        )

        val quota = requireNotNull(MdbListQuotaParser.fromHeaders(headers, nowMillis))
        assertEquals(25000, quota.dailyLimit)
        assertEquals(24990, quota.remaining)
    }

    @Test
    fun missingRateLimitHeadersDoNotProduceFakeQuota() {
        val headers = Headers.headersOf("Content-Type", "application/json")
        assertNull(MdbListQuotaParser.fromHeaders(headers, nowMillis))

        val partial = Headers.headersOf("X-RateLimit-Limit", "1000")
        assertNull(MdbListQuotaParser.fromHeaders(partial, nowMillis))
    }

    @Test
    fun resetHeaderAcceptsEpochMillisecondsAndDeltaSeconds() {
        val millisHeaders = Headers.headersOf(
            "X-RateLimit-Limit", "1000",
            "X-RateLimit-Remaining", "10",
            "X-RateLimit-Reset", "1700006400000"
        )
        val fromMillis = requireNotNull(MdbListQuotaParser.fromHeaders(millisHeaders, nowMillis))
        assertEquals(1700006400L, fromMillis.resetAtEpochSeconds)

        val deltaHeaders = Headers.headersOf(
            "X-RateLimit-Limit", "1000",
            "X-RateLimit-Remaining", "10",
            "X-RateLimit-Reset", "3600"
        )
        val fromDelta = requireNotNull(MdbListQuotaParser.fromHeaders(deltaHeaders, nowMillis))
        assertEquals(nowSeconds + 3600L, fromDelta.resetAtEpochSeconds)
    }

    @Test
    fun userEndpointJsonPrefersRateLimitRemaining() {
        val json = """
            {
              "api_requests": 100000,
              "api_requests_count": 276,
              "rate_limit": 100000,
              "rate_limit_remaining": 99724,
              "rate_limit_reset": 1706918400,
              "plan": "Supporter"
            }
        """.trimIndent()

        val quota = requireNotNull(MdbListQuotaParser.fromUserJson(json, nowMillis))

        assertEquals(100000, quota.dailyLimit)
        assertEquals(99724, quota.remaining)
        assertEquals("Supporter", quota.plan)
        assertTrue(quota.authoritative)
    }

    @Test
    fun userEndpointJsonFallsBackToRequestCount() {
        val json = """
            {
              "api_requests": 1000,
              "api_requests_count": 250,
              "rate_limit_reset": 1700006400
            }
        """.trimIndent()

        val quota = requireNotNull(MdbListQuotaParser.fromUserJson(json, nowMillis))
        assertEquals(1000, quota.dailyLimit)
        assertEquals(750, quota.remaining)
    }

    @Test
    fun userEndpointJsonWithoutLimitIsRejected() {
        assertNull(MdbListQuotaParser.fromUserJson("""{"username":"someone"}""", nowMillis))
        assertNull(MdbListQuotaParser.fromUserJson("not-json", nowMillis))
    }

    @Test
    fun localRequestsReduceTheServerRemainingValue() {
        val base = MdbListQuota(
            dailyLimit = 1000,
            serverRemaining = 500,
            resetAtEpochSeconds = 1700006400L,
            observedAtMillis = nowMillis
        )

        val afterOne = base.afterRequest(nowMillis)
        assertEquals(499, afterOne.remaining)
        assertEquals(1, afterOne.pendingRequests)
        // "Son güncelleme" sunucu okumasının zamanı olarak kalır.
        assertEquals(nowMillis, afterOne.observedAtMillis)
        // Yerel sayaç devreye girdiği anda değer "kesin" olmaktan çıkar.
        assertFalse(afterOne.isExact)

        val afterThree = afterOne.afterRequest(nowMillis).afterRequest(nowMillis)
        assertEquals(497, afterThree.remaining)
    }

    @Test
    fun quotaRollsOverToFullLimitAfterUtcMidnight() {
        val yesterday = MdbListQuota(
            dailyLimit = 1000,
            serverRemaining = 12,
            resetAtEpochSeconds = 1_699_999_999L,
            observedAtMillis = nowMillis - 86_400_000L
        )

        val rolled = yesterday.updatedFor(nowMillis)

        assertEquals(1000, rolled.remaining)
        assertEquals(0, rolled.pendingRequests)
        assertEquals(1_700_086_399L, rolled.resetAtEpochSeconds)
        assertFalse(rolled.authoritative)
    }

    @Test
    fun rateLimitedResponseReportsExhaustedQuotaWithRetryWindow() {
        val base = MdbListQuota(
            dailyLimit = 1000,
            serverRemaining = 3,
            resetAtEpochSeconds = 1700006400L,
            observedAtMillis = nowMillis
        )

        val limited = base.onRateLimited(nowMillis, retryAfterSeconds = 43_200L)

        assertEquals(0, limited.remaining)
        assertTrue(limited.isExhausted)
        assertEquals(nowSeconds + 43_200L, limited.resetAtEpochSeconds)
        assertTrue(limited.authoritative)
    }

    @Test
    fun storedQuotaRoundTripsThroughDiskFormat() {
        val quota = MdbListQuota(
            dailyLimit = 1000,
            serverRemaining = 481,
            pendingRequests = 3,
            resetAtEpochSeconds = 1700006400L,
            observedAtMillis = nowMillis,
            plan = "Free",
            authoritative = true
        )

        val decoded = requireNotNull(MdbListQuotaStore.decode(MdbListQuotaStore.encode(quota)))

        assertEquals(quota, decoded)
        assertEquals(478, decoded.remaining)
    }

    @Test
    fun quotaStoreAlwaysReturnsUsableBaseEvenWithoutServerValue() {
        val base = MdbListQuotaStore.base("unit-test-key", nowMillis)

        assertEquals(MdbListQuotaStore.DEFAULT_DAILY_LIMIT, base.dailyLimit)
        assertEquals(MdbListQuotaStore.DEFAULT_DAILY_LIMIT, base.remaining)
        assertEquals(nextUtcMidnightEpochSeconds(nowMillis), base.resetAtEpochSeconds)
        assertFalse(base.authoritative)
    }
}
