package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsPluginDiagnosticRunnerTest {

    @Test
    fun emptySearchWithoutProtectionSignatureIsNotClassifiedAsCfBlocked() {
        val result = diagnosticResult(searchCount = 0)

        assertEquals(CsPluginDiagnosticRunner.ResultStatus.SEARCH_EMPTY, result.status)
        assertFalse(result.hasCfBlock)
        assertFalse(result.hasDdosGuard)
    }

    @Test
    fun genericForbiddenResponseIsNotClassifiedAsCfBlocked() {
        val result = diagnosticResult(
            searchCount = 0,
            phaseErrors = listOf(phaseError(message = "HTTP 403 Forbidden"))
        )

        assertEquals(CsPluginDiagnosticRunner.ResultStatus.SEARCH_EMPTY, result.status)
        assertFalse(result.hasCfBlock)
    }

    @Test
    fun explicitCfSignatureIsClassifiedAsCfBlocked() {
        val result = diagnosticResult(
            searchCount = 0,
            phaseErrors = listOf(phaseError(isCfPattern = true))
        )

        assertEquals(CsPluginDiagnosticRunner.ResultStatus.CF_BLOCKED, result.status)
        assertTrue(result.hasCfBlock)
    }

    @Test
    fun protectionSignatureInAnyPhaseIsCountedEvenWhenSearchHadHits() {
        val result = diagnosticResult(
            searchCount = 2,
            loadOk = false,
            phaseErrors = listOf(phaseError(phase = "detail", isCfPattern = true))
        )

        assertEquals(CsPluginDiagnosticRunner.ResultStatus.CF_BLOCKED, result.status)
    }

    @Test
    fun confirmedProtectionIsNotHiddenByPluginLoadFailure() {
        val result = diagnosticResult(
            loaded = false,
            phaseErrors = listOf(phaseError(phase = "load", isCfPattern = true))
        )

        assertEquals(CsPluginDiagnosticRunner.ResultStatus.CF_BLOCKED, result.status)
    }

    @Test
    fun successfulStreamsTakePrecedenceOverAnEarlierProtectionFailure() {
        val result = diagnosticResult(
            searchCount = 1,
            streamCount = 1,
            phaseErrors = listOf(phaseError(isCfPattern = true))
        )

        assertEquals(CsPluginDiagnosticRunner.ResultStatus.WORKING, result.status)
    }

    @Test
    fun summarySeparatesEmptySearchesFromConfirmedProtectionBlocks() {
        val searchEmpty = diagnosticResult(searchCount = 0)
        val confirmedCf = diagnosticResult(
            searchCount = 0,
            phaseErrors = listOf(phaseError(isCfPattern = true))
        )
        val noStreams = diagnosticResult(searchCount = 1, loadOk = true)
        val loadFailed = diagnosticResult(searchCount = 1, loadOk = false)
        val dead = diagnosticResult(loaded = false)
        val working = diagnosticResult(searchCount = 1, streamCount = 1)

        val summary = CsPluginDiagnosticRunner.summarize(
            listOf(searchEmpty, confirmedCf, noStreams, loadFailed, dead, working)
        )

        assertEquals(1, summary.searchEmpty)
        assertEquals(1, summary.cfBlocked)
        assertEquals(1, summary.noStreams)
        assertEquals(1, summary.loadFailed)
        assertEquals(1, summary.dead)
        assertEquals(1, summary.working)
    }

    private fun diagnosticResult(
        downloaded: Boolean = true,
        loaded: Boolean = true,
        searchCount: Int = 0,
        loadOk: Boolean = false,
        streamCount: Int = 0,
        phaseErrors: List<CsPluginDiagnosticRunner.PhaseError> = emptyList()
    ) = CsPluginDiagnosticRunner.DiagnosticResult(
        pluginId = "test-plugin",
        repoSlug = "test/repo",
        displayName = "Test Plugin",
        mainUrl = "https://example.test",
        downloaded = downloaded,
        loaded = loaded,
        apiCount = if (loaded) 1 else 0,
        searchQuery = "Example",
        searchCount = searchCount,
        topSearchHit = null,
        loadOk = loadOk,
        loadedTitle = null,
        episodeFound = false,
        streamCount = streamCount,
        streamUrls = emptyList(),
        embedResults = emptyList(),
        phaseErrors = phaseErrors,
        error = null
    )

    private fun phaseError(
        phase: String = "search",
        isCfPattern: Boolean = false,
        isDdosGuard: Boolean = false,
        message: String? = null
    ) = CsPluginDiagnosticRunner.PhaseError(
        phase = phase,
        errorClass = "IOException",
        message = message ?: if (isCfPattern) "Cloudflare challenge" else "network error",
        httpCode = null,
        isCfPattern = isCfPattern,
        isDdosGuard = isDdosGuard,
        isTimeout = false,
        isNetworkFail = false,
        stackSnippet = null
    )
}
