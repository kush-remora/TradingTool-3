package com.tradingtool.core.kite

import com.tradingtool.config.KiteConfig
import com.zerodhatech.kiteconnect.KiteConnect
import com.zerodhatech.models.User

/**
 * Thin wrapper around the Kite Connect SDK's [KiteConnect] instance.
 *
 * Responsibilities:
 * - Configure [KiteConnect] from [KiteConfig] at startup.
 * - Provide the login URL when no access token is available.
 * - Exchange a request token for an access token (step 2 of Kite auth flow).
 * - Expose the underlying [KiteConnect] for direct API calls.
 *
 * Token lifecycle: Kite access tokens expire daily at 6 AM IST.
 * The cron-job module is responsible for refreshing the token; this class
 * just applies the new token via [applyAccessToken].
 */
class KiteConnectClient(private val config: KiteConfig) {

    private val kite: KiteConnect = KiteConnect(config.apiKey).also { k ->
        if (config.accessToken.isNotBlank()) {
            k.setAccessToken(config.accessToken)
        }
        k.setSessionExpiryHook {
            // Token expired — log and wait for cron-job / manual refresh.
            // Do not crash the service; just mark the client as unauthenticated.
            isAuthenticated = false
        }
    }

    @Volatile
    var isAuthenticated: Boolean = config.accessToken.isNotBlank()
        private set

    /** The URL the user must open in a browser to start the Kite login flow. */
    fun loginUrl(): String = kite.loginURL

    /**
     * Exchange a [requestToken] (received via Kite redirect) for an access token.
     * Configures this client immediately on success.
     */
    fun generateSession(requestToken: String): User {
        val user: User = kite.generateSession(requestToken, config.apiSecret)
        applyAccessToken(user.accessToken)
        return user
    }

    /** Apply a new access token — called by the cron-job after daily refresh. */
    fun applyAccessToken(accessToken: String) {
        kite.setAccessToken(accessToken)
        isAuthenticated = true
    }

    /**
     * Returns the underlying [KiteConnect] instance for direct API calls.
     * @throws IllegalStateException if the client is not authenticated yet.
     */
    fun client(): KiteConnect {
        check(isAuthenticated) {
            "KiteConnect is not authenticated. " +
                "Open the login URL and exchange the request token first: ${loginUrl()}"
        }
        return kite
    }
}
