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

   
}
