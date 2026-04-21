package com.tradingtool.core.earnings

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

class FileCorporateEventAdapter(
    private val filePath: Path,
    private val objectMapper: ObjectMapper,
) : EarningsCorporateEventSource {
    private val log = LoggerFactory.getLogger(FileCorporateEventAdapter::class.java)

    override suspend fun fetchResultEvents(from: LocalDate, to: LocalDate): List<EarningsResultEvent> {
        val payload = readPayload()
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
                if (date.isBefore(from) || date.isAfter(to)) {
                    return@mapNotNull null
                }
                EarningsResultEvent(stockSymbol = symbol.uppercase(), resultDate = date)
            }
            .distinctBy { event -> event.stockSymbol to event.resultDate }
            .toList()
    }

    private fun readPayload(): GrowwCorporateEventsResponse {
        if (!Files.exists(filePath)) {
            throw IllegalStateException("Earnings input file not found: $filePath")
        }
        val json = runCatching { Files.readString(filePath) }.getOrElse { error ->
            throw IllegalStateException("Failed to read earnings input file $filePath: ${error.message}", error)
        }
        return runCatching {
            objectMapper.readValue(json, GrowwCorporateEventsResponse::class.java)
        }.getOrElse { error ->
            log.error("Failed to parse earnings input file {}: {}", filePath, error.message, error)
            throw IllegalStateException("Invalid earnings JSON in $filePath", error)
        }
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
        private const val RESULTS_FILTER = "RESULTS"
    }
}

