package com.tradingtool.core.kite

import com.tradingtool.core.http.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KiteConnectClientTest {

    @Test
    fun `applyAccessToken trims surrounding whitespace`() {
        val client = KiteConnectClient(KiteConfig(apiKey = "test-api-key", apiSecret = "test-api-secret"))

        client.applyAccessToken("  fresh-token  ")

        assertEquals("fresh-token", client.accessToken)
        assertTrue(client.isAuthenticated)
    }

    @Test
    fun `validateSession fails fast when no access token is applied`() {
        val client = KiteConnectClient(KiteConfig(apiKey = "test-api-key", apiSecret = "test-api-secret"))

        val result = client.validateSession()

        assertIs<Result.Failure>(result)
        assertFalse(client.isAuthenticated)
        assertEquals("", client.accessToken)
    }
}
