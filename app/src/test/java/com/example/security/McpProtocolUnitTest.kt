package com.example.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpProtocolUnitTest {

    @Test
    fun `agent token generation and sha256 hashing are deterministic and secure`() {
        val token1 = CredentialCrypto.generateAgentToken("antigravity_main")
        val token2 = CredentialCrypto.generateAgentToken("antigravity_main")
        assertTrue(token1.startsWith("ntfy_antigrav_"))
        assertNotEquals(token1, token2)

        val hash1 = CredentialCrypto.sha256Hex(token1)
        val hash1Again = CredentialCrypto.sha256Hex(token1)
        assertEquals(64, hash1.length)
        assertEquals(hash1, hash1Again)
        assertTrue(CredentialCrypto.constantTimeEquals(hash1, hash1Again))
        assertFalse(CredentialCrypto.constantTimeEquals(hash1, CredentialCrypto.sha256Hex(token2)))
    }

    @Test
    fun `sanitizeId strips unsafe characters and respects regex`() {
        assertEquals("task_auth_1", CredentialCrypto.sanitizeId("task.auth/1", "fallback"))
        assertEquals("fallback", CredentialCrypto.sanitizeId("   ", "fallback"))
        assertTrue(CredentialCrypto.isValidId("agent-01_prod"))
        assertFalse(CredentialCrypto.isValidId("bad/id"))
    }

    @Test
    fun `sliding window rate limiter enforces per-agent quota`() {
        val limiter = SlidingWindowRateLimiter(maxRequestsPerWindow = 3, windowMillis = 10_000L)
        assertTrue(limiter.allow("agent_a", 1000L))
        assertTrue(limiter.allow("agent_a", 2000L))
        assertTrue(limiter.allow("agent_a", 3000L))
        assertFalse(limiter.allow("agent_a", 4000L))
        // Different agent has independent bucket
        assertTrue(limiter.allow("agent_b", 4000L))
        // After window expires, requests are allowed again
        assertTrue(limiter.allow("agent_a", 12_000L))
    }
}
