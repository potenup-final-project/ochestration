package com.pg.ochestration.infrastructure.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ApiKeyHasherTest {

    @Test
    fun `should return same hash for same rawKey and pepper`() {
        val hasher = ApiKeyHasher(VALID_PEPPER)

        val first = hasher.hash("sk_test_rawkeyvalue")
        val second = hasher.hash("sk_test_rawkeyvalue")

        assertEquals(first, second)
    }

    @Test
    fun `should return different hash when pepper changes`() {
        val hasherA = ApiKeyHasher("pepper-a-value-that-is-32-chars!!")
        val hasherB = ApiKeyHasher("pepper-b-value-that-is-32-chars!!")

        val hashA = hasherA.hash("sk_test_rawkeyvalue")
        val hashB = hasherB.hash("sk_test_rawkeyvalue")

        assertNotEquals(hashA, hashB)
    }

    @Test
    fun `should throw IllegalArgumentException when pepper is shorter than 32 characters`() {
        assertFailsWith<IllegalArgumentException> {
            ApiKeyHasher("short-pepper")
        }
    }

    @Test
    fun `should create hasher successfully when pepper is exactly 32 characters`() {
        val pepper32Chars = "a".repeat(32)
        ApiKeyHasher(pepper32Chars)
    }

    @Test
    fun `should return 64 character hex string as hash result`() {
        val hasher = ApiKeyHasher(VALID_PEPPER)

        val hash = hasher.hash("any-raw-key")

        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    private companion object {
        const val VALID_PEPPER = "test-pepper-value-that-is-32chars"
    }
}
