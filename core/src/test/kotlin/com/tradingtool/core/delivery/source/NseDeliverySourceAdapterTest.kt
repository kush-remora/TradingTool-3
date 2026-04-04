package com.tradingtool.core.delivery.source

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

class NseDeliverySourceAdapterTest {

    class FakeHttpClient : SuspendHttpClient {
        val responses = mutableMapOf<String, String>()
        
        override suspend fun get(url: String, headers: Map<String, String>): Result<String> {
            val resp = responses[url] ?: responses["*"] ?: return Result.Failure(HttpError.HttpStatusError(404, "Not Found"))
            return Result.Success(resp)
        }

        override suspend fun post(url: String, body: String?, headers: Map<String, String>) = Result.Failure(HttpError.HttpStatusError(404, "Not Found"))
        override suspend fun put(url: String, body: String?, headers: Map<String, String>) = Result.Failure(HttpError.HttpStatusError(404, "Not Found"))
        override suspend fun delete(url: String, headers: Map<String, String>) = Result.Failure(HttpError.HttpStatusError(404, "Not Found"))
        override suspend fun patch(url: String, body: String?, headers: Map<String, String>) = Result.Failure(HttpError.HttpStatusError(404, "Not Found"))
    }

    private val httpClient = FakeHttpClient()
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val jsonHttpClient = JsonHttpClient(httpClient, objectMapper)
    private val adapter = NseDeliverySourceAdapter(jsonHttpClient)

    @Test
    fun `test source discovery - CurrentDay priority`() = runBlocking {
        httpClient.responses["https://www.nseindia.com/api/daily-reports?key=CM"] = """
            {
                "CurrentDay": [
                    { "fileKey": "CM-BHAVDATA-FULL", "filePath": "https://current/", "fileActlName": "current.csv" }
                ],
                "PreviousDay": [
                    { "fileKey": "CM-BHAVDATA-FULL", "filePath": "https://prev/", "fileActlName": "prev.csv" }
                ]
            }
        """.trimIndent()
        
        httpClient.responses["https://current/current.csv"] = "SYMBOL,SERIES,DATE1,TTL_TRD_QNTY,DELIV_QTY,DELIV_PER\nRELIANCE,EQ,25-Mar-2026,1000,500,50.0"

        val result = adapter.fetchLatestDeliveryData()
        assertEquals(1, result.size)
        assertEquals("RELIANCE", result[0].symbol)
        assertEquals("https://current/current.csv", result[0].sourceUrl)
    }

    @Test
    fun `test source discovery - PreviousDay fallback`() = runBlocking {
        httpClient.responses["https://www.nseindia.com/api/daily-reports?key=CM"] = """
            {
                "CurrentDay": [
                    { "fileKey": "OTHER", "filePath": "https://current/", "fileActlName": "other.csv" }
                ],
                "PreviousDay": [
                    { "fileKey": "CM-BHAVDATA-FULL", "filePath": "https://prev/", "fileActlName": "prev.csv" }
                ]
            }
        """.trimIndent()
        
        httpClient.responses["https://prev/prev.csv"] = "SYMBOL,SERIES,DATE1,TTL_TRD_QNTY,DELIV_QTY,DELIV_PER\nRELIANCE,EQ,24-Mar-2026,1000,500,50.0"

        val result = adapter.fetchLatestDeliveryData()
        assertEquals(1, result.size)
        assertEquals("RELIANCE", result[0].symbol)
        assertEquals("https://prev/prev.csv", result[0].sourceUrl)
    }

    @Test
    fun `test CSV parsing - valid and invalid rows`() = runBlocking {
        val csv = """
            SYMBOL, SERIES, DATE1, TTL_TRD_QNTY, DELIV_QTY, DELIV_PER
            RELIANCE, EQ, 25-Mar-2026, 1000, 600, 60.0
            TCS, EQ, 25-Mar-2026, 2000, -, -
            INFY, EQ, 25-Mar-2026, 3000, 1500, 50.0
            INVALID, EQ, 25-Mar-2026, NAN, NAN, NAN
        """.trimIndent()

        httpClient.responses["https://www.nseindia.com/api/daily-reports?key=CM"] = """{"CurrentDay": [{"fileKey": "CM-BHAVDATA-FULL", "filePath": "h://", "fileActlName": "f.csv"}]}"""
        httpClient.responses["h://f.csv"] = csv

        val result = adapter.fetchLatestDeliveryData()
        assertEquals(2, result.size)
        assertEquals("RELIANCE", result[0].symbol)
        assertEquals(60.0, result[0].delivPer)
        assertEquals("INFY", result[1].symbol)
        assertEquals(50.0, result[1].delivPer)
    }
}
