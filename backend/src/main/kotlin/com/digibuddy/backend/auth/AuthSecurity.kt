package com.digibuddy.backend.auth

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SecretHasher(private val pepper: String) {
    fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest("$pepper:$value".toByteArray(StandardCharsets.UTF_8)).toHex()
    }

    fun matches(value: String, expectedHash: String): Boolean = MessageDigest.isEqual(
        hash(value).toByteArray(StandardCharsets.US_ASCII),
        expectedHash.toByteArray(StandardCharsets.US_ASCII),
    )
}

class IdentifierFingerprinter(key: String) {
    private val keyBytes = key.toByteArray(StandardCharsets.UTF_8)

    fun fingerprint(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)).toHex()
    }
}

class PasswordHasher(private val secureRandom: SecureRandom = SecureRandom()) {
    fun hash(password: CharArray): String {
        val salt = ByteArray(SALT_LENGTH).also(secureRandom::nextBytes)
        val output = derive(password, salt)
        return "\$argon2id\$v=19\$m=$MEMORY_KIB,t=$ITERATIONS,p=$PARALLELISM\$" +
            "${Base64.getEncoder().withoutPadding().encodeToString(salt)}\$" +
            Base64.getEncoder().withoutPadding().encodeToString(output)
    }

    fun verify(password: CharArray, encoded: String): Boolean = runCatching {
        val parts = encoded.split('$')
        require(parts.size == 6 && parts[1] == "argon2id" && parts[2] == "v=19")
        require(parts[3] == "m=$MEMORY_KIB,t=$ITERATIONS,p=$PARALLELISM")
        val salt = Base64.getDecoder().decode(parts[4])
        val expected = Base64.getDecoder().decode(parts[5])
        MessageDigest.isEqual(derive(password, salt), expected)
    }.getOrDefault(false)

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(MEMORY_KIB)
            .withIterations(ITERATIONS)
            .withParallelism(PARALLELISM)
            .withSalt(salt)
            .build()
        val output = ByteArray(HASH_LENGTH)
        Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password, output)
        return output
    }

    companion object {
        private const val MEMORY_KIB = 19 * 1024
        private const val ITERATIONS = 2
        private const val PARALLELISM = 1
        private const val SALT_LENGTH = 16
        private const val HASH_LENGTH = 32
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
