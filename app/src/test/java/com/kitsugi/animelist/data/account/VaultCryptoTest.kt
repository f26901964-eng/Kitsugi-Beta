package com.kitsugi.animelist.data.account

import com.kitsugi.animelist.data.account.VaultCrypto.asBlob
import org.junit.Assert.*
import org.junit.Test

class VaultCryptoTest {
    private val key = ByteArray(32) { (it + 1).toByte() }

    @Test fun pendingChangePreservesBothWaysToTheSameKeyUntilFinalization() {
        val old = VaultCrypto.wrap("old-password", key)
        val next = VaultCrypto.wrap("new-password", key)
        val staged = old.copy(pending = VaultCrypto.PasswordWrap(next.salt, next.iv, next.ct))
        // Before Auth update, old password still works. After update/new login, pending works.
        assertArrayEquals(key, VaultCrypto.unwrap("old-password", staged))
        assertArrayEquals(key, VaultCrypto.unwrap("new-password", staged.pending!!.asBlob()))
        val finalized = staged.pending.asBlob()
        assertTrue(runCatching { VaultCrypto.unwrap("old-password", finalized) }.isFailure)
        assertArrayEquals(key, VaultCrypto.unwrap("new-password", finalized))
    }

    @Test fun passwordRotationDoesNotRequireReencryptingAccountPayload() {
        val plain = "test-access-and-refresh-token".toByteArray()
        val (iv, ciphertext) = VaultCrypto.encrypt(key, plain)
        val newKey = VaultCrypto.unwrap("new-password", VaultCrypto.wrap("new-password", key))
        assertArrayEquals(plain, VaultCrypto.decrypt(newKey, iv, ciphertext))
    }

    @Test fun corruptedCiphertextIsRejected() {
        val (iv, ciphertext) = VaultCrypto.encrypt(key, "test-token".toByteArray())
        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        assertTrue(runCatching { VaultCrypto.decrypt(key, iv, ciphertext) }.isFailure)
    }

    @Test fun everyEncryptionAndWrapUsesFreshRandomness() {
        val first = VaultCrypto.wrap("password", key)
        val second = VaultCrypto.wrap("password", key)
        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.iv, second.iv)
        assertNotEquals(first.ct, second.ct)
        assertArrayEquals(key, VaultCrypto.unwrap("password", first))
    }
}
