package com.tradingtool.core.watchlist.groww

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.http.HttpError
import com.tradingtool.core.http.JsonHttpClient
import com.tradingtool.core.http.Result
import com.tradingtool.core.http.SuspendHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GrowwWatchlistAdapterTest {

    private class FakeHttpClient : SuspendHttpClient {
        val responses = mutableMapOf<String, String>()

        override suspend fun get(url: String, headers: Map<String, String>): Result<String> {
            val body = responses[url] ?: return Result.Failure(HttpError.HttpStatusError(404, "Not Found"))
            return Result.Success(body)
        }

        override suspend fun post(url: String, body: String?, headers: Map<String, String>) = Result.Failure(HttpError.HttpStatusError(404, "Not Found"))
        override suspend fun put(url: String, body: String?, headers: Map<String, String>) = Result.Failure(HttpError.HttpStatusError(404, "Not Found"))
        override suspend fun delete(url: String, headers: Map<String, String>) = Result.Failure(HttpError.HttpStatusError(404, "Not Found"))
        override suspend fun patch(url: String, body: String?, headers: Map<String, String>) = Result.Failure(HttpError.HttpStatusError(404, "Not Found"))
    }

    @Test
    fun `extracts NSE stocks with token and skips non-stock items`() = runBlocking {
        val watchlistId = "GWL_1729712098800"
        val url = "https://groww.in/v1/api/presentation/v2/watchlist/$watchlistId/details?include_index_fno=true"

        val httpClient = FakeHttpClient().apply {
            responses[url] = """
                {
                  "payload": {
                    "items": [
                      {
                        "instrumentType": "STOCKS",
                        "nseSymbol": "INFY",
                        "instrumentToken": 408065,
                        "companyName": "Infosys Ltd"
                      },
                      {
                        "instrumentType": "INDEX",
                        "nseSymbol": "NIFTY 50",
                        "instrumentToken": 123,
                        "companyName": "Nifty"
                      },
                      {
                        "instrumentType": "STOCKS",
                        "nseSymbol": "RELIANCE",
                        "instrument": {
                          "instrumentToken": 738561
                        },
                        "companyShortName": "Reliance"
                      }
                    ]
                  }
                }
            """.trimIndent()
        }

        val adapter = GrowwWatchlistAdapter(
            jsonHttpClient = JsonHttpClient(httpClient, ObjectMapper().registerKotlinModule()),
            objectMapper = ObjectMapper().registerKotlinModule(),
        )

        val stocks = adapter.fetchStocks(GrowwWatchlistSyncRequest(watchlistId))

        assertEquals(2, stocks.size)
        assertEquals("INFY", stocks[0].symbol)
        assertEquals(408065L, stocks[0].instrumentToken)
        assertEquals("RELIANCE", stocks[1].symbol)
        assertEquals(738561L, stocks[1].instrumentToken)
    }
}
