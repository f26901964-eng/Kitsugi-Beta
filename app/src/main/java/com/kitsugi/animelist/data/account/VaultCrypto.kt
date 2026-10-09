package com.kitsugi.animelist.data.account

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Android-independent crypto primitives, also exercised by JVM regression tests. */
internal object VaultCrypto {
    private const val PBKDF2_ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private val random = SecureRandom()
    @Serializable
    data class CipherBlob(val salt: String = "", val iv: String, val ct: String,
        val pending: PasswordWrap? = null)

    @Serializable
    data class PasswordWrap(val salt: String, val iv: String, val ct: String)

    // ─────────────────────────── Kriptografi ───────────────────────────

    fun deriveKek(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    fun encrypt(key: ByteArray, plain: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv to cipher.doFinal(plain)
    }

    fun decrypt(key: ByteArray, iv: ByteArray, ct: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)

    fun wrap(password: String, key: ByteArray): CipherBlob {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val kek = deriveKek(password, salt)
        return try {
            val (iv, ct) = encrypt(kek, key)
            CipherBlob(b64(salt), b64(iv), b64(ct))
        } finally { kek.fill(0) }
    }

    fun unwrap(password: String, wrapped: CipherBlob): ByteArray {
        val kek = deriveKek(password, unb64(wrapped.salt))
        return try { decrypt(kek, unb64(wrapped.iv), unb64(wrapped.ct)) } finally { kek.fill(0) }
    }

    fun PasswordWrap.asBlob() = CipherBlob(salt, iv, ct)

}
