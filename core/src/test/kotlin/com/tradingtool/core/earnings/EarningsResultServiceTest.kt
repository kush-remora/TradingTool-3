package com.tradingtool.core.earnings

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.candle.DailyCandle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class EarningsResultServiceTest {

    @Test
    fun `splitDateRange creates contiguous non-overlapping chunks`() {
        val chunks = EarningsResultService.splitDateRange(
            from = LocalDate.parse("2026-05-01"),
            to = LocalDate.parse("2026-05-13"),
            chunkDays = 5,
        )

        assertEquals(3, chunks.size)
        assertEquals(LocalDate.parse("2026-05-01"), chunks[0].from)
        assertEquals(LocalDate.parse("2026-05-05"), chunks[0].to)
        assertEquals(LocalDate.parse("2026-05-06"), chunks[1].from)
        assertEquals(LocalDate.parse("2026-05-10"), chunks[1].to)
        assertEquals(LocalDate.parse("2026-05-11"), chunks[2].from)
        assertEquals(LocalDate.parse("2026-05-13"), chunks[2].to)
    }

    @Test
    fun `refresh is idempotent and avoids duplicates`() = runBlocking {
        val source = FakeCorporateEventSource(
            eventsByRange = mapOf(
                Pair(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-05")) to listOf(
                    EarningsResultEvent("INFY", LocalDate.parse("2026-05-04")),
                ),
            ),
        )
        val gateway = FakeEarningsGateway()
        val candles = FakeCandleGateway(emptyMap())
        val service = buildService(source, gateway, candles)

        repeat(2) {
            service.refresh(
                EarningsRefreshRequest(
                    from = LocalDate.parse("2026-05-01"),
                    to = LocalDate.parse("2026-05-05"),
                    pastDays = 30,
                    chunkDays = 5,
                ),
            )
        }

        assertEquals(1, gateway.rowsByKey.size)
        assertEquals(2, gateway.upsertCalls)
    }

    @Test
    fun `refresh computes behavior stages with trading-session offsets and leaves missing stage absent`() = runBlocking {
        val today = LocalDate.parse("2026-05-20")
        val eventDate = LocalDate.parse("2026-05-06")
        val symbol = "INFY"

        val source = FakeCorporateEventSource(emptyMap())
        val gateway = FakeEarningsGateway(
            seedRows = listOf(
                EarningsResultRow(
                    id = 1L,
                    stockSymbol = symbol,
                    resultDate = eventDate,
                    behaviorPayloadJson = "{}",
                ),
            ),
        )

        val tradingDays = generateTradingDays(
            from = LocalDate.parse("2026-04-10"),
            count = 18,
        )
        val eventIndex = tradingDays.indexOf(eventDate)
        check(eventIndex >= 14)

        val candles = tradingDays.mapIndexed { index, date ->
            DailyCandle(
                instrumentToken = 1L,
                symbol = symbol,
                candleDate = date,
                open = 100.0 + index,
                high = 101.0 + index,
                low = 99.0 + index,
                close = 100.5 + index,
                volume = 1000L + index,
            )
        }

        val candleGateway = FakeCandleGateway(mapOf(symbol to candles))
        val service = buildService(source, gateway, candleGateway, fixedClock(today))

        val result = service.refresh(
            EarningsRefreshRequest(
                from = today,
                to = today,
                pastDays = 30,
                chunkDays = 5,
            ),
        )

        assertEquals(1, result.behaviorRowsUpdated)
        val updatedJson = gateway.updatedBehaviorById[1L] ?: error("Expected behavior payload update")
        val updatedNode = ObjectMapper().registerKotlinModule().readTree(updatedJson)

        assertTrue(updatedNode.has("pre_result"))
        assertTrue(updatedNode.has("result_day"))
        assertTrue(updatedNode.has("next_day"))
        assertTrue(updatedNode.has("plus_5d"))

        val pre = updatedNode["pre_result"]
        assertEquals(eventDate.toString(), pre["trading_date"].asText())
        assertEquals(tradingDays[eventIndex - 14].toString(), pre["reference_date"].asText())

        val plus5 = updatedNode["plus_5d"]
        assertEquals(tradingDays[eventIndex + 5].toString(), plus5["trading_date"].asText())
    }

    @Test
    fun `refresh leaves plus_5d absent when fifth trading day is unavailable`() = runBlocking {
        val today = LocalDate.parse("2026-05-20")
        val eventDate = LocalDate.parse("2026-05-14")
        val symbol = "TCS"

        val source = FakeCorporateEventSource(emptyMap())
        val gateway = FakeEarningsGateway(
            seedRows = listOf(
                EarningsResultRow(
                    id = 2L,
                    stockSymbol = symbol,
                    resultDate = eventDate,
                    behaviorPayloadJson = "{}",
                ),
            ),
        )

        val tradingDays = listOf(
            LocalDate.parse("2026-04-20"),
            LocalDate.parse("2026-04-21"),
            LocalDate.parse("2026-04-22"),
            LocalDate.parse("2026-04-23"),
            LocalDate.parse("2026-04-24"),
            LocalDate.parse("2026-04-27"),
            LocalDate.parse("2026-04-28"),
            LocalDate.parse("2026-04-29"),
            LocalDate.parse("2026-04-30"),
            LocalDate.parse("2026-05-01"),
            LocalDate.parse("2026-05-04"),
            LocalDate.parse("2026-05-05"),
            LocalDate.parse("2026-05-06"),
            LocalDate.parse("2026-05-07"),
            LocalDate.parse("2026-05-08"),
            LocalDate.parse("2026-05-11"),
            LocalDate.parse("2026-05-12"),
            LocalDate.parse("2026-05-13"),
            LocalDate.parse("2026-05-14"),
            LocalDate.parse("2026-05-15"),
            LocalDate.parse("2026-05-18"),
        )

        val candles = tradingDays.mapIndexed { index, date ->
            DailyCandle(
                instrumentToken = 2L,
                symbol = symbol,
                candleDate = date,
                open = 200.0 + index,
                high = 201.0 + index,
                low = 199.0 + index,
                close = 200.5 + index,
                volume = 2000L + index,
            )
        }

        val candleGateway = FakeCandleGateway(mapOf(symbol to candles))
        val service = buildService(source, gateway, candleGateway, fixedClock(today))

        service.refresh(
            EarningsRefreshRequest(
                from = today,
                to = today,
                pastDays = 30,
                chunkDays = 5,
            ),
        )

        val updatedJson = gateway.updatedBehaviorById[2L] ?: error("Expected behavior payload update")
        val updatedNode = ObjectMapper().registerKotlinModule().readTree(updatedJson)
        assertTrue(updatedNode.has("result_day"))
        assertTrue(updatedNode.has("next_day"))
        assertTrue(!updatedNode.has("plus_5d"))
    }

    private fun buildService(
        source: EarningsCorporateEventSource,
        gateway: EarningsResultGateway,
        candleGateway: EarningsCandleGateway,
        clock: Clock = fixedClock(LocalDate.parse("2026-05-20")),
    ): EarningsResultService {
        return EarningsResultService(
            corporateEventSource = source,
            earningsGateway = gateway,
            candleGateway = candleGateway,
            objectMapper = ObjectMapper().registerKotlinModule(),
            clock = clock,
        )
    }

    private fun fixedClock(date: LocalDate): Clock {
        return Clock.fixed(date.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant(), ZoneId.of("Asia/Kolkata"))
    }

    private fun generateTradingDays(from: LocalDate, count: Int): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var date = from
        while (dates.size < count) {
            val dayOfWeek = date.dayOfWeek
            if (dayOfWeek.value <= 5) {
                dates += date
            }
            date = date.plusDays(1)
        }
        return dates
    }

    private class FakeCorporateEventSource(
        private val eventsByRange: Map<Pair<LocalDate, LocalDate>, List<EarningsResultEvent>>,
    ) : EarningsCorporateEventSource {
        override suspend fun fetchResultEvents(from: LocalDate, to: LocalDate): List<EarningsResultEvent> {
            return eventsByRange[from to to].orEmpty()
        }
    }

    private class FakeEarningsGateway(
        seedRows: List<EarningsResultRow> = emptyList(),
    ) : EarningsResultGateway {
        var upsertCalls: Int = 0
        val rowsByKey = linkedMapOf<Pair<String, LocalDate>, EarningsResultRow>()
        val updatedBehaviorById = mutableMapOf<Long, String>()

        init {
            seedRows.forEach { row ->
                rowsByKey[row.stockSymbol to row.resultDate] = row
            }
        }

        override suspend fun upsert(stockSymbol: String, resultDate: LocalDate): Int {
            upsertCalls += 1
            val key = stockSymbol to resultDate
            val existing = rowsByKey[key]
            if (existing == null) {
                val nextId = (rowsByKey.values.maxOfOrNull { row -> row.id } ?: 0L) + 1L
                rowsByKey[key] = EarningsResultRow(nextId, stockSymbol, resultDate, "{}")
            }
            return 1
        }

        override suspend fun findByResultDateRange(from: LocalDate, to: LocalDate): List<EarningsResultRow> {
            return rowsByKey.values.filter { row -> !row.resultDate.isBefore(from) && !row.resultDate.isAfter(to) }
        }

        override suspend fun updateBehaviorPayload(id: Long, behaviorPayloadJson: String): Int {
            updatedBehaviorById[id] = behaviorPayloadJson
            val entry = rowsByKey.entries.firstOrNull { (_, value) -> value.id == id } ?: return 0
            rowsByKey[entry.key] = entry.value.copy(behaviorPayloadJson = behaviorPayloadJson)
            return 1
        }
    }

    private class FakeCandleGateway(
        private val candlesBySymbol: Map<String, List<DailyCandle>>,
    ) : EarningsCandleGateway {
        override suspend fun findDailyCandlesBySymbol(symbol: String, from: LocalDate, to: LocalDate): List<DailyCandle> {
            return candlesBySymbol[symbol].orEmpty().filter { candle ->
                !candle.candleDate.isBefore(from) && !candle.candleDate.isAfter(to)
            }
        }
    }
}
