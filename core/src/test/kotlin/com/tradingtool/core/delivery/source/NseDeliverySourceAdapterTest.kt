package com.tradingtool.core.delivery.source

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.delivery.model.DeliverySourceType
import com.tradingtool.core.http.HttpError
import com.tradingtool.core.http.JsonHttpClient
import com.tradingtool.core.http.Result
import com.tradingtool.core.http.SuspendHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
                    { "fileKey": "CM-BHAVDATA-FULL", "filePath": "https://current/", "fileActlName": "current.csv", "tradingDate": "25-Mar-2026" },
                    { "fileKey": "CM-SECWISE-DELIVERY-POSITION", "filePath": "https://current/", "fileActlName": "mto.dat", "tradingDate": "25-Mar-2026" }
                ],
                "PreviousDay": [
                    { "fileKey": "CM-BHAVDATA-FULL", "filePath": "https://prev/", "fileActlName": "prev.csv", "tradingDate": "24-Mar-2026" }
                ]
            }
        """.trimIndent()
        
        httpClient.responses["https://current/current.csv"] = "SYMBOL,SERIES,DATE1,TTL_TRD_QNTY,DELIV_QTY,DELIV_PER\nRELIANCE,EQ,25-Mar-2026,1000,500,50.0"

        val result = adapter.fetchLatestDeliveryData()
        assertEquals(1, result.size)
        assertEquals("RELIANCE", result[0].symbol)
        assertEquals("https://current/current.csv", result[0].sourceUrl)

        val discovery = adapter.discoverDeliveryReports()
        assertNotNull(discovery)
        assertEquals(LocalDate.of(2026, 3, 25), discovery?.resolvedTradingDate)
        assertEquals("CurrentDay", discovery?.bucket)
        assertEquals("current.csv", discovery?.bhavDataFull?.fileName)
        assertEquals("mto.dat", discovery?.mto?.fileName)
    }

    @Test
    fun `test source discovery - PreviousDay fallback`() = runBlocking {
        httpClient.responses["https://www.nseindia.com/api/daily-reports?key=CM"] = """
            {
                "CurrentDay": [
                    { "fileKey": "OTHER", "filePath": "https://current/", "fileActlName": "other.csv", "tradingDate": "25-Mar-2026" }
                ],
                "PreviousDay": [
                    { "fileKey": "CM-BHAVDATA-FULL", "filePath": "https://prev/", "fileActlName": "prev.csv", "tradingDate": "24-Mar-2026" }
                ]
            }
        """.trimIndent()
        
        httpClient.responses["https://prev/prev.csv"] = "SYMBOL,SERIES,DATE1,TTL_TRD_QNTY,DELIV_QTY,DELIV_PER\nRELIANCE,EQ,24-Mar-2026,1000,500,50.0"

        val result = adapter.fetchLatestDeliveryData()
        assertEquals(1, result.size)
        assertEquals("RELIANCE", result[0].symbol)
        assertEquals("https://prev/prev.csv", result[0].sourceUrl)

        val discovery = adapter.discoverDeliveryReports()
        assertEquals(LocalDate.of(2026, 3, 24), discovery?.resolvedTradingDate)
        assertEquals("PreviousDay", discovery?.bucket)
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

        httpClient.responses["https://www.nseindia.com/api/daily-reports?key=CM"] = """{"CurrentDay": [{"fileKey": "CM-BHAVDATA-FULL", "filePath": "h://", "fileActlName": "f.csv", "tradingDate": "25-Mar-2026"}]}"""
        httpClient.responses["h://f.csv"] = csv

        val result = adapter.fetchLatestDeliveryData()
        assertEquals(2, result.size)
        assertEquals("RELIANCE", result[0].symbol)
        assertEquals(60.0, result[0].delivPer)
        assertEquals("INFY", result[1].symbol)
        assertEquals(50.0, result[1].delivPer)
    }

    @Test
    fun `test explicit date discovery`() = runBlocking {
        httpClient.responses["https://www.nseindia.com/api/daily-reports?key=CM"] = """
            {
                "CurrentDay": [
                    { "fileKey": "CM-BHAVDATA-FULL", "filePath": "https://current/", "fileActlName": "current.csv", "tradingDate": "25-Mar-2026" }
                ],
                "PreviousDay": [
                    { "fileKey": "CM-BHAVDATA-FULL", "filePath": "https://prev/", "fileActlName": "prev.csv", "tradingDate": "24-Mar-2026" },
                    { "fileKey": "CM-SECWISE-DELIVERY-POSITION", "filePath": "https://prev/", "fileActlName": "mto.dat", "tradingDate": "24-Mar-2026" }
                ]
            }
        """.trimIndent()

        val discovery = adapter.discoverDeliveryReports(LocalDate.of(2026, 3, 24))

        assertEquals("PreviousDay", discovery?.bucket)
        assertEquals("prev.csv", discovery?.bhavDataFull?.fileName)
        assertEquals("mto.dat", discovery?.mto?.fileName)
    }

    @Test
    fun `test MTO parsing`() = runBlocking {
        httpClient.responses["https://file/mto.dat"] = """
            Security Wise Delivery Position - Compulsory Rolling Settlement
            10,MTO,25032026,2078308988,0003222
            Trade Date <25-MAR-2026>,Settlement Type <N>
            Record Type,Sr No,Name of Security,Quantity Traded,Deliverable Quantity(gross across client level),% of Deliverable Quantity to Traded Quantity
            20,1,RELIANCE,EQ,1000,600,60.00
            20,2,INFY,EQ,2000,900,45.00
        """.trimIndent()

        val rows = adapter.fetchMtoRows(
            com.tradingtool.core.delivery.validation.DeliveryFileDescriptor(
                tradingDate = LocalDate.of(2026, 3, 25),
                url = "https://file/mto.dat",
                fileName = "mto.dat",
            )
        )

        assertEquals(2, rows.size)
        assertEquals("RELIANCE", rows[0].symbol)
        assertEquals(DeliverySourceType.MTO, rows[0].source)
        assertEquals(60.0, rows[0].deliveryPercent)
    }
}
