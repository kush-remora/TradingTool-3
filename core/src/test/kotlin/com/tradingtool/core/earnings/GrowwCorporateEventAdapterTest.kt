package com.tradingtool.core.earnings

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.http.HttpError
import com.tradingtool.core.http.JsonHttpClient
import com.tradingtool.core.http.Result
import com.tradingtool.core.http.SuspendHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GrowwCorporateEventAdapterTest {

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
    fun `parses only RESULTS rows with NSE symbol and valid primary date`() = runBlocking {
        val from = LocalDate.parse("2026-05-06")
        val to = LocalDate.parse("2026-05-10")
        val url = "https://groww.in/v1/api/stocks_data/equity_feature/v2/corporate_action/event?from=$from&to=$to"

        val httpClient = FakeHttpClient().apply {
            responses[url] = """
                {
                  "exdateEvents": [
                    {
                      "corporateEventFilter": "RESULTS",
                      "nseSymbol": "INFY",
                      "corporateEventPillDto": { "primaryDate": "2026-05-07" }
                    },
                    {
                      "corporateEventFilter": "BONUS",
                      "nseSymbol": "TCS",
                      "corporateEventPillDto": { "primaryDate": "2026-05-07" }
                    },
                    {
                      "corporateEventFilter": "RESULTS",
                      "nseSymbol": "",
                      "corporateEventPillDto": { "primaryDate": "2026-05-08" }
                    },
                    {
                      "corporateEventFilter": "RESULTS",
                      "nseSymbol": "RELIANCE",
                      "corporateEventPillDto": { "primaryDate": "invalid" }
                    }
                  ]
                }
            """.trimIndent()
        }

        val adapter = GrowwCorporateEventAdapter(
            jsonHttpClient = JsonHttpClient(httpClient, ObjectMapper().registerKotlinModule()),
        )

        val result = adapter.fetchResultEvents(from, to)

        assertEquals(1, result.size)
        assertEquals("INFY", result[0].stockSymbol)
        assertEquals(LocalDate.parse("2026-05-07"), result[0].resultDate)
    }
}
