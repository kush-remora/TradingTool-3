package com.tradingtool.core.fundamentals.screener

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.http.Result
import com.tradingtool.core.http.SuspendHttpClient

@Singleton
class ScreenerFundamentalsSourceAdapter @Inject constructor(
    private val httpClient: SuspendHttpClient,
) {
    suspend fun fetchCompanyPage(symbol: String): Result<String> {
        val normalizedSymbol = symbol.trim().uppercase()
        val url = "$BASE_URL/company/$normalizedSymbol/consolidated/"
        return httpClient.get(
            url = url,
            headers = DEFAULT_HEADERS,
        )
    }

    companion object {
        private const val BASE_URL: String = "https://www.screener.in"
        private val DEFAULT_HEADERS: Map<String, String> = mapOf(
            "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        )
    }
}
