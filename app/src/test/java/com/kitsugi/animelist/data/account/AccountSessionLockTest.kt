package com.kitsugi.animelist.data.account

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class AccountSessionLockTest {
    @Test fun sessionReplacementWaitsUntilTheCurrentTransferCompletes() = runBlocking {
        withTimeout(5_000) {
            var owner = "a"
            val transferEntered = CompletableDeferred<Unit>()
            val allowTransfer = CompletableDeferred<Unit>()
            val replacementWaiting = CompletableDeferred<Unit>()
            val transfer = async {
                LinkedAccountVault.changeSession {
                    val readOwner = owner
                    transferEntered.complete(Unit)
                    allowTransfer.await()
                    assertEquals(readOwner, owner)
                }
            }
            transferEntered.await()
            val replacement = async {
                replacementWaiting.complete(Unit)
                LinkedAccountVault.changeSession { owner = "b" }
            }
            replacementWaiting.await()
            assertEquals("a", owner)
            allowTransfer.complete(Unit)
            transfer.await()
            replacement.await()
            assertEquals("b", owner)
        }
    }

    @Test fun cancellingAnOperationReleasesTheSessionLock() = runBlocking {
        withTimeout(5_000) {
            val entered = CompletableDeferred<Unit>()
            val neverComplete = CompletableDeferred<Unit>()
            val cancelled = async {
                LinkedAccountVault.changeSession { entered.complete(Unit); neverComplete.await() }
            }
            entered.await()
            cancelled.cancelAndJoin()
            assertEquals("next operation", LinkedAccountVault.changeSession { "next operation" })
        }
    }
}
