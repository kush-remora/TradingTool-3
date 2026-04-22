package com.tradingtool.core.earnings

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.tradingtool.core.candle.DailyCandle
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class EarningsResultService(
    private val corporateEventSource: EarningsCorporateEventSource,
    private val earningsGateway: EarningsResultGateway,
    private val candleGateway: EarningsCandleGateway,
    private val stockTokenLookup: EarningsStockTokenLookup? = null,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Kolkata")),
) {

    suspend fun refresh(request: EarningsRefreshRequest = EarningsRefreshRequest()): EarningsRefreshResult {
        require(request.chunkDays > 0) { "chunkDays must be > 0" }
        require(request.pastDays >= 0) { "pastDays must be >= 0" }

        val today = LocalDate.now(clock)
        val from = request.from ?: today
        val to = request.to ?: today.plusDays(30)
        require(!from.isAfter(to)) { "from date must be <= to date" }

        val chunks = splitDateRange(from, to, request.chunkDays)
        var fetchedEvents = 0
        val uniqueEvents = linkedMapOf<Pair<String, LocalDate>, EarningsResultEvent>()
        var upsertedRows = 0
        val unresolvedSymbols = linkedSetOf<String>()

        for (chunk in chunks) {
            val events = corporateEventSource.fetchResultEvents(chunk.from, chunk.to)
            fetchedEvents += events.size
            for (event in events) {
                uniqueEvents[event.stockSymbol to event.resultDate] = event
            }
        }

        for (event in uniqueEvents.values) {
            val token = resolveInstrumentToken(event.stockSymbol, event.instrumentToken)
            if (token == null) {
                unresolvedSymbols += event.stockSymbol
                continue
            }
            upsertedRows += earningsGateway.upsert(event.stockSymbol, token, event.resultDate)
        }

        earningsGateway.backfillInstrumentTokenFromStocks()
        resolveMissingInstrumentTokens(unresolvedSymbols)
        val nullInstrumentTokenRowsAfterRefresh = earningsGateway.countRowsMissingInstrumentToken()
        val instrumentTokenNotNullEnforced = if (nullInstrumentTokenRowsAfterRefresh == 0) {
            earningsGateway.enforceInstrumentTokenNotNull()
        } else {
            false
        }

        val pastFrom = today.minusDays(request.pastDays.toLong())
        val pastTo = today.minusDays(1)
        val pastRows = if (pastFrom.isAfter(pastTo)) {
            emptyList()
        } else {
            earningsGateway.findByResultDateRange(pastFrom, pastTo)
        }

        var behaviorRowsUpdated = 0
        var preResultUpdates = 0
        var resultDayUpdates = 0
        var nextDayUpdates = 0
        var plus5dUpdates = 0

        for (row in pastRows) {
            val update = buildBehaviorPayloadUpdate(row, today)
            if (!update.changed) {
                continue
            }
            val updated = earningsGateway.updateBehaviorPayload(row.id, update.payloadJson)
            if (updated > 0) {
                behaviorRowsUpdated += 1
                preResultUpdates += if (update.preResultChanged) 1 else 0
                resultDayUpdates += if (update.resultDayChanged) 1 else 0
                nextDayUpdates += if (update.nextDayChanged) 1 else 0
                plus5dUpdates += if (update.plus5dChanged) 1 else 0
            }
        }

        return EarningsRefreshResult(
            from = from,
            to = to,
            pastFrom = pastFrom,
            pastTo = pastTo,
            chunkDays = request.chunkDays,
            chunkCount = chunks.size,
            fetchedEvents = fetchedEvents,
            uniqueEvents = uniqueEvents.size,
            upsertedRows = upsertedRows,
            unresolvedTokenCount = unresolvedSymbols.size,
            unresolvedSymbolsSample = unresolvedSymbols.take(UNRESOLVED_SYMBOLS_SAMPLE_LIMIT),
            nullInstrumentTokenRowsAfterRefresh = nullInstrumentTokenRowsAfterRefresh,
            instrumentTokenNotNullEnforced = instrumentTokenNotNullEnforced,
            pastRowsEvaluated = pastRows.size,
            behaviorRowsUpdated = behaviorRowsUpdated,
            preResultUpdates = preResultUpdates,
            resultDayUpdates = resultDayUpdates,
            nextDayUpdates = nextDayUpdates,
            plus5dUpdates = plus5dUpdates,
        )
    }

    private suspend fun resolveMissingInstrumentTokens(unresolvedSymbols: MutableSet<String>) {
        val rows = earningsGateway.findRowsMissingInstrumentToken(MISSING_TOKEN_SCAN_LIMIT)
        for (row in rows) {
            val token = resolveInstrumentToken(row.stockSymbol, null)
            if (token == null) {
                unresolvedSymbols += row.stockSymbol
                continue
            }
            earningsGateway.updateInstrumentToken(row.id, token)
        }
    }

    private suspend fun resolveInstrumentToken(symbol: String, seededToken: Long?): Long? {
        if (seededToken != null && seededToken > 0L) {
            return seededToken
        }
        val fromStocks = stockTokenLookup?.findInstrumentTokenBySymbol(symbol)
        if (fromStocks != null && fromStocks > 0L) {
            return fromStocks
        }
        return null
    }

    private suspend fun buildBehaviorPayloadUpdate(
        row: EarningsResultRow,
        today: LocalDate,
    ): BehaviorPayloadUpdate {
        val rangeFrom = row.resultDate.minusDays(90)
        val rangeTo = row.resultDate.plusDays(20)
        val candles = candleGateway.findDailyCandlesBySymbol(row.stockSymbol, rangeFrom, rangeTo)
            .sortedBy { candle -> candle.candleDate }

        val eventIndex = candles.indexOfFirst { candle -> candle.candleDate == row.resultDate }
        if (eventIndex == -1) {
            return BehaviorPayloadUpdate(
                payloadJson = row.behaviorPayloadJson,
                changed = false,
                preResultChanged = false,
                resultDayChanged = false,
                nextDayChanged = false,
                plus5dChanged = false,
            )
        }

        val existingNode = parseJsonNode(row.behaviorPayloadJson)
        val payload = parsePayloadMap(row.behaviorPayloadJson).toMutableMap()

        val eventCandle = candles[eventIndex]
        val preResultStage = computePreResultStage(candles, eventIndex)
        val resultDayStage = computeResultDayStage(eventCandle)
        val nextDayStage = computeNextDayStage(candles, eventIndex)
        val plus5dStage = computePlus5dStage(candles, eventIndex)

        val preResultChanged = putStageIfChanged(payload, "pre_result", preResultStage)
        val resultDayChanged = putStageIfChanged(payload, "result_day", resultDayStage)
        val nextDayChanged = putStageIfChanged(payload, "next_day", nextDayStage)
        val plus5dChanged = putStageIfChanged(payload, "plus_5d", plus5dStage)

        val changed = preResultChanged || resultDayChanged || nextDayChanged || plus5dChanged
        if (!changed) {
            return BehaviorPayloadUpdate(
                payloadJson = row.behaviorPayloadJson,
                changed = false,
                preResultChanged = false,
                resultDayChanged = false,
                nextDayChanged = false,
                plus5dChanged = false,
            )
        }

        payload["updated_on"] = today.toString()
        val updatedJson = objectMapper.writeValueAsString(payload)
        val updatedNode = parseJsonNode(updatedJson)
        if (updatedNode == existingNode) {
            return BehaviorPayloadUpdate(
                payloadJson = row.behaviorPayloadJson,
                changed = false,
                preResultChanged = false,
                resultDayChanged = false,
                nextDayChanged = false,
                plus5dChanged = false,
            )
        }

        return BehaviorPayloadUpdate(
            payloadJson = updatedJson,
            changed = true,
            preResultChanged = preResultChanged,
            resultDayChanged = resultDayChanged,
            nextDayChanged = nextDayChanged,
            plus5dChanged = plus5dChanged,
        )
    }

    private fun computePreResultStage(candles: List<DailyCandle>, eventIndex: Int): Map<String, Any>? {
        if (eventIndex < 14) {
            return null
        }
        val eventCandle = candles[eventIndex]
        val d14Candle = candles[eventIndex - 14]
        return linkedMapOf(
            "trading_date" to eventCandle.candleDate.toString(),
            "open" to eventCandle.open,
            "high" to eventCandle.high,
            "change_pct" to percentChange(eventCandle.open, d14Candle.close),
            "reference_date" to d14Candle.candleDate.toString(),
            "reference_close" to d14Candle.close,
        )
    }

    private fun computeResultDayStage(eventCandle: DailyCandle): Map<String, Any> {
        return linkedMapOf(
            "trading_date" to eventCandle.candleDate.toString(),
            "open" to eventCandle.open,
            "high" to eventCandle.high,
            "change_pct" to percentChange(eventCandle.close, eventCandle.open),
        )
    }

    private fun computeNextDayStage(candles: List<DailyCandle>, eventIndex: Int): Map<String, Any>? {
        val nextIndex = eventIndex + 1
        if (nextIndex >= candles.size) {
            return null
        }
        val nextCandle = candles[nextIndex]
        return linkedMapOf(
            "trading_date" to nextCandle.candleDate.toString(),
            "open" to nextCandle.open,
            "high" to nextCandle.high,
            "change_pct" to percentChange(nextCandle.close, nextCandle.open),
        )
    }

    private fun computePlus5dStage(candles: List<DailyCandle>, eventIndex: Int): Map<String, Any>? {
        val plus5Index = eventIndex + 5
        if (plus5Index >= candles.size) {
            return null
        }
        val eventCandle = candles[eventIndex]
        val plus5Candle = candles[plus5Index]
        return linkedMapOf(
            "trading_date" to plus5Candle.candleDate.toString(),
            "open" to plus5Candle.open,
            "high" to plus5Candle.high,
            "change_pct" to percentChange(plus5Candle.close, eventCandle.close),
            "reference_date" to eventCandle.candleDate.toString(),
            "reference_close" to eventCandle.close,
        )
    }

    private fun putStageIfChanged(
        payload: MutableMap<String, Any>,
        key: String,
        stage: Map<String, Any>?,
    ): Boolean {
        if (stage == null) {
            return false
        }

        val existingNode = objectMapper.valueToTree<JsonNode>(payload[key])
        val newNode = objectMapper.valueToTree<JsonNode>(stage)
        if (existingNode == newNode) {
            return false
        }

        payload[key] = stage
        return true
    }

    private fun parsePayloadMap(json: String): Map<String, Any> {
        if (json.isBlank()) {
            return emptyMap()
        }
        return runCatching {
            objectMapper.readValue(json, PAYLOAD_TYPE_REF)
        }.getOrElse {
            emptyMap()
        }
    }

    private fun parseJsonNode(json: String): JsonNode {
        return runCatching { objectMapper.readTree(json) }
            .getOrElse { objectMapper.createObjectNode() }
    }

    private fun percentChange(target: Double, base: Double): Double {
        if (base == 0.0) {
            return 0.0
        }
        val raw = ((target - base) / base) * 100.0
        return BigDecimal(raw).setScale(4, RoundingMode.HALF_UP).toDouble()
    }

    data class BehaviorPayloadUpdate(
        val payloadJson: String,
        val changed: Boolean,
        val preResultChanged: Boolean,
        val resultDayChanged: Boolean,
        val nextDayChanged: Boolean,
        val plus5dChanged: Boolean,
    )

    companion object {
        private val PAYLOAD_TYPE_REF = object : TypeReference<Map<String, Any>>() {}
        private const val MISSING_TOKEN_SCAN_LIMIT: Int = 5000
        private const val UNRESOLVED_SYMBOLS_SAMPLE_LIMIT: Int = 50

        internal fun splitDateRange(from: LocalDate, to: LocalDate, chunkDays: Int): List<DateChunk> {
            require(chunkDays > 0) { "chunkDays must be > 0" }
            if (from.isAfter(to)) {
                return emptyList()
            }

            val chunks = mutableListOf<DateChunk>()
            var cursor = from
            while (!cursor.isAfter(to)) {
                val chunkEnd = cursor.plusDays((chunkDays - 1).toLong())
                    .coerceAtMost(to)
                chunks += DateChunk(from = cursor, to = chunkEnd)
                cursor = chunkEnd.plusDays(1)
            }
            return chunks
        }
    }
}
