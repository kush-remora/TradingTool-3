package com.tradingtool.core.earnings

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.tradingtool.core.http.JsonHttpClient
import org.slf4j.LoggerFactory
import java.time.LocalDate

class GrowwCorporateEventAdapter(
    private val jsonHttpClient: JsonHttpClient,
) : EarningsCorporateEventSource {
    private val log = LoggerFactory.getLogger(GrowwCorporateEventAdapter::class.java)

    override suspend fun fetchResultEvents(from: LocalDate, to: LocalDate): List<EarningsResultEvent> {
        val url = "$BASE_URL?from=$from&to=$to"
        val response = jsonHttpClient.get<GrowwCorporateEventsResponse>(url, defaultHeaders())
        val payload = response.getOrNull()
        if (payload == null) {
            log.error("Failed to fetch Groww corporate events for {}..{}: {}", from, to, response.errorOrNull())
            return emptyList()
        }

        return payload.exdateEvents.orEmpty()
            .asSequence()
            .filter { event -> event.corporateEventFilter == RESULTS_FILTER }
            .mapNotNull { event ->
                val symbol = event.nseSymbol?.trim().orEmpty()
                if (symbol.isBlank()) {
                    return@mapNotNull null
                }
                val date = runCatching { LocalDate.parse(event.corporateEventPillDto?.primaryDate) }.getOrNull()
                    ?: return@mapNotNull null
                EarningsResultEvent(stockSymbol = symbol.uppercase(), resultDate = date)
            }
            .distinctBy { event -> event.stockSymbol to event.resultDate }
            .toList()
    }

    private fun defaultHeaders(): Map<String, String> {
        return mapOf(
            "Accept" to "application/json, text/plain, */*",
            "Origin" to "https://groww.in",
            "Referer" to "https://groww.in/",
            "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
        )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GrowwCorporateEventsResponse(
        @JsonProperty("exdateEvents")
        val exdateEvents: List<GrowwEventRow>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GrowwEventRow(
        @JsonProperty("corporateEventFilter")
        val corporateEventFilter: String? = null,
        @JsonProperty("nseSymbol")
        val nseSymbol: String? = null,
        @JsonProperty("corporateEventPillDto")
        val corporateEventPillDto: GrowwCorporateEventPillDto? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GrowwCorporateEventPillDto(
        @JsonProperty("primaryDate")
        val primaryDate: String? = null,
    )

    companion object {
        private const val BASE_URL = "https://groww.in/v1/api/stocks_data/equity_feature/v2/corporate_action/event"
        private const val RESULTS_FILTER = "RESULTS"
    }
}
