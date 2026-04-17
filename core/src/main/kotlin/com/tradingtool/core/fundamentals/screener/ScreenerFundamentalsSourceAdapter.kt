package com.tradingtool.core.fundamentals.screener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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
        val quoteUrl = buildQuoteUrl(normalizedSymbol)
        val filingsUrl = buildCompanyPageUrl(normalizedSymbol)
        val shareholdingUrl = buildShareholdingUrl(normalizedSymbol)

        return when (val quoteResponse = httpClient.get(url = quoteUrl, headers = DEFAULT_HEADERS)) {
            is Result.Failure -> quoteResponse
            is Result.Success -> {
                when (val filingsResponse = httpClient.get(url = filingsUrl, headers = DEFAULT_HEADERS)) {
                    is Result.Failure -> filingsResponse
                    is Result.Success -> {
                        when (val shareholdingResponse = httpClient.get(url = shareholdingUrl, headers = DEFAULT_HEADERS)) {
                            is Result.Failure -> shareholdingResponse
                            is Result.Success -> {
                                val payload = NseFundamentalsPayload(
                                    symbol = normalizedSymbol,
                                    quoteJson = quoteResponse.data,
                                    financialResultsJson = filingsResponse.data,
                                    shareholdingJson = shareholdingResponse.data,
                                    sourceUrl = filingsUrl,
                                )
                                Result.Success(objectMapper.writeValueAsString(payload))
                            }
                        }
                    }
                }
            }
        }
    }

    fun buildCompanyPageUrl(symbol: String): String {
        val normalizedSymbol = symbol.trim().uppercase()
        return "$BASE_URL/api/corporates-financial-results?index=equities&symbol=$normalizedSymbol&period=Quarterly"
    }

    fun buildQuoteUrl(symbol: String): String {
        val normalizedSymbol = symbol.trim().uppercase()
        return "$BASE_URL/api/quote-equity?symbol=$normalizedSymbol"
    }

    fun buildShareholdingUrl(symbol: String): String {
        val normalizedSymbol = symbol.trim().uppercase()
        return "$BASE_URL/api/corporate-share-holdings-master?index=equities&symbol=$normalizedSymbol"
    }

    companion object {
        private const val BASE_URL: String = "https://www.nseindia.com"
        private val DEFAULT_HEADERS: Map<String, String> = mapOf(
            "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36",
            "Accept" to "application/json,text/plain,*/*",
        )
        private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()
    }
}
