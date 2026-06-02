package com.tradingtool

import com.tradingtool.core.http.HttpError
import com.tradingtool.core.http.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KiteStartupTokenValidationTest {

    @Test
    fun `applies trimmed token when session validates`() {
        var appliedToken: String? = null
        var validationCount: Int = 0

        requireValidKiteStartupToken(
            latestToken = "  fresh-token  ",
            applyAccessToken = { token -> appliedToken = token },
            validateSession = {
                validationCount += 1
                Result.Success(Unit)
            },
        )

        assertEquals("fresh-token", appliedToken)
        assertEquals(1, validationCount)
    }

    @Test
    fun `throws clear error when latest token is stale`() {
        var appliedToken: String? = null

        val error = assertFailsWith<IllegalStateException> {
            requireValidKiteStartupToken(
                latestToken = " expired-token ",
                applyAccessToken = { token -> appliedToken = token },
                validateSession = {
                    Result.Failure(HttpError.HttpStatusError(statusCode = 403, body = "Forbidden"))
                },
            )
        }

        assertEquals("expired-token", appliedToken)
        assertTrue(error.message.orEmpty().contains("expired or invalid"))
        assertTrue(error.message.orEmpty().contains("HTTP 403 error"))
    }
}
