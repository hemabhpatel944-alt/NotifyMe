package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

object CredentialCrypto {
    private val secureRandom = SecureRandom()
    private val validIdRegex = Regex("^[a-zA-Z0-9_\\-]+$")

    fun generateAgentToken(agentId: String): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        val shortAgent = sanitizeId(agentId, fallback = "ag").take(8)
        return "ntfy_${shortAgent}_$hex"
    }

    fun tokenPrefix(token: String): String {
        if (token.length <= 14) return token.take(4) + "..."
        return token.take(10) + "..." + token.takeLast(4)
    }

    fun sha256Hex(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    fun sanitizeId(raw: String?, fallback: String): String {
        val cleaned = raw.orEmpty()
            .trim()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(120)
        return if (cleaned.isNotEmpty() && validIdRegex.matches(cleaned)) cleaned else fallback
    }

    fun isValidId(raw: String): Boolean =
        raw.length in 1..128 && validIdRegex.matches(raw)
}

class SlidingWindowRateLimiter(
    private val maxRequestsPerWindow: Int = 60,
    private val windowMillis: Long = 60_000L
) {
    private val buckets = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun allow(key: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val deque = buckets.computeIfAbsent(key) { ArrayDeque() }
        synchronized(deque) {
            val cutoff = nowMillis - windowMillis
            while (deque.isNotEmpty() && deque.first() < cutoff) {
                deque.removeFirst()
            }
            if (deque.size >= maxRequestsPerWindow) {
                return false
            }
            deque.addLast(nowMillis)
            return true
        }
    }
}
