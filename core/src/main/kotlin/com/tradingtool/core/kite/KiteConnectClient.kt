package com.tradingtool.core.kite

import com.tradingtool.core.http.HttpError
import com.tradingtool.core.http.Result
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
 *
 * Design: Wraps the Kite SDK (which makes its own HTTP calls) with structured
 * error handling via Result<T>. Keeps SDK integration intact for stability.
 */
class KiteConnectClient(private val config: KiteConfig) {

    private val kite: KiteConnect = KiteConnect(config.apiKey).also { k ->
        k.setSessionExpiryHook {
            // Token expired — log and wait for cron-job / manual refresh.
            // Do not crash the service; just mark the client as unauthenticated.
            isAuthenticated = false
        }
    }

    @Volatile
    var isAuthenticated: Boolean = false
        private set

    @Volatile
    var accessToken: String = ""
        private set

    val apiKey: String get() = config.apiKey

    @Volatile
    private var onTokenRefreshed: ((String) -> Unit)? = null

    /** Register a callback invoked every time a new access token is applied. */
    fun setTokenRefreshCallback(callback: (String) -> Unit) {
        onTokenRefreshed = callback
    }

    /** The URL the user must open in a browser to start the Kite login flow. */
    fun loginUrl(): String = kite.loginURL

    /**
     * Exchange a [requestToken] (received via Kite redirect) for an access token.
     * Configures this client immediately on success.
     * Returns Result for structured error handling.
     */
    fun generateSession(requestToken: String): Result<User> = runCatching {
        val user: User = kite.generateSession(requestToken, config.apiSecret)
        applyAccessToken(user.accessToken)
        user
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { e ->
            val detail = buildString {
                append(e::class.simpleName ?: e.javaClass.simpleName)
                val message = e.message?.takeIf { it.isNotBlank() }
                val causeMessage = e.cause?.message?.takeIf { it.isNotBlank() }
                if (message != null) {
                    append(": ")
                    append(message)
                } else if (causeMessage != null) {
                    append(": ")
                    append(causeMessage)
                }
            }
            Result.Failure(
                HttpError.UnknownError(e, "Failed to generate Kite session: $detail")
            )
        }
    )

    /** Apply a new access token — called by the cron-job after daily refresh. */
    fun applyAccessToken(token: String) {
        kite.setAccessToken(token)
        accessToken = token
        isAuthenticated = true
        onTokenRefreshed?.invoke(token)
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
