package com.tradingtool.core.strategy.absolutedelivery

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.indexconstituents.dao.IndexSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AbsoluteDeliveryBacktestAnalyzerTest {

    @Test
    fun `criteria preserve inclusive traded quantity and exclusive delivery boundaries`() {
        val dates = listOf(
            LocalDate.of(2026, 7, 21),
            LocalDate.of(2026, 7, 22),
            LocalDate.of(2026, 7, 23),
            LocalDate.of(2026, 7, 24),
        )
        val response = response(
            members = listOf(member("TEST", 1L)),
            dates = dates,
            deliveries = listOf(
                delivery(1L, dates[0], 20_000_000L, 5_000_001L, 60.01),
                delivery(1L, dates[1], 20_000_000L, 5_000_000L, 60.01),
                delivery(1L, dates[2], 20_000_000L, 5_000_001L, 60.0),
                delivery(1L, dates[3], 19_999_999L, 5_000_001L, 60.01),
            ),
        )

        val rowsByDate = response.allRows.associateBy { row -> row.tradingDate }
        assertTrue(requireNotNull(rowsByDate[dates[0].toString()]).matched)
        assertTrue(requireNotNull(rowsByDate[dates[0].toString()]).uptrendMatched)
        assertFalse(requireNotNull(rowsByDate[dates[1].toString()]).deliveryQuantityPassed)
        assertFalse(requireNotNull(rowsByDate[dates[2].toString()]).deliveryPercentagePassed)
        assertFalse(requireNotNull(rowsByDate[dates[3].toString()]).tradedQuantityPassed)
        assertEquals(1, response.summary.matchedRowCount)
    }

    @Test
    fun `cross product exposes incomplete missing-source and absent records`() {
        val olderDate = LocalDate.of(2026, 7, 23)
        val latestDate = LocalDate.of(2026, 7, 24)
        val response = response(
            members = listOf(member("AAA", 1L), member("BBB", 2L)),
            dates = listOf(olderDate, latestDate),
            deliveries = listOf(
                delivery(1L, latestDate, 1_000L, 500L, 50.0),
                delivery(
                    token = 2L,
                    date = latestDate,
                    tradedQuantity = null,
                    deliveryQuantity = null,
                    deliveryPercentage = null,
                    reconciliationStatus = DeliveryReconciliationStatus.MISSING_FROM_SOURCE,
                ),
                delivery(1L, olderDate, 1_000L, null, 50.0),
            ),
        )

        val statuses = response.allRows.associate { row ->
            "${row.symbol}-${row.tradingDate}" to row.dataStatus
        }
        assertEquals(AbsoluteDeliveryDataStatus.AVAILABLE, statuses["AAA-$latestDate"])
        assertEquals(AbsoluteDeliveryDataStatus.MISSING_FROM_SOURCE, statuses["BBB-$latestDate"])
        assertEquals(AbsoluteDeliveryDataStatus.INCOMPLETE, statuses["AAA-$olderDate"])
        assertEquals(AbsoluteDeliveryDataStatus.NO_RECORD, statuses["BBB-$olderDate"])
        assertEquals(4, response.summary.expectedRowCount)
        assertEquals(1, response.summary.evaluatedRowCount)
        assertEquals(3, response.summary.missingRowCount)
        assertTrue(response.allRows.none { row -> row.matched })
    }

    @Test
    fun `matched and full rows use their required ordering and six month start`() {
        val olderDate = LocalDate.of(2026, 7, 23)
        val latestDate = LocalDate.of(2026, 7, 24)
        val response = response(
            members = listOf(member("BBB", 2L), member("AAA", 1L)),
            dates = listOf(olderDate, latestDate),
            deliveries = listOf(
                delivery(1L, latestDate, 22_000_000L, 12_000_000L, 61.0),
                delivery(2L, latestDate, 25_000_000L, 15_000_000L, 65.0),
                delivery(1L, olderDate, 21_000_000L, 11_000_000L, 62.0),
                delivery(2L, olderDate, 24_000_000L, 14_000_000L, 63.0),
            ),
        )

        assertEquals(
            listOf("BBB-$latestDate", "AAA-$latestDate", "BBB-$olderDate", "AAA-$olderDate"),
            response.matchedRows.map { row -> "${row.symbol}-${row.tradingDate}" },
        )
        assertEquals(
            listOf("AAA-$latestDate", "BBB-$latestDate", "AAA-$olderDate", "BBB-$olderDate"),
            response.allRows.map { row -> "${row.symbol}-${row.tradingDate}" },
        )
        assertEquals(LocalDate.of(2026, 1, 24), absoluteDeliveryBacktestFromDate(latestDate))
    }

    @Test
    fun `grouping options are sorted and requested grouping is resolved safely`() {
        val summaries = listOf(
            IndexSummary(indexKey = "nifty_smallcap_250", count = 250),
            IndexSummary(indexKey = "groww_HIGH_QUALITY", count = 32),
        )

        assertEquals(
            listOf("groww_HIGH_QUALITY", "nifty_smallcap_250"),
            absoluteDeliveryGroupingOptions(summaries).map { option -> option.value },
        )
        assertEquals(
            "groww_HIGH_QUALITY",
            resolveAbsoluteDeliveryGrouping(
                requestedGrouping = null,
                summaries = summaries,
                defaultGrouping = "groww_HIGH_QUALITY",
            ),
        )
        assertEquals(
            "nifty_smallcap_250",
            resolveAbsoluteDeliveryGrouping(
                requestedGrouping = "NIFTY_SMALLCAP_250",
                summaries = summaries,
                defaultGrouping = "groww_HIGH_QUALITY",
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            resolveAbsoluteDeliveryGrouping(
                requestedGrouping = "unknown",
                summaries = summaries,
                defaultGrouping = "groww_HIGH_QUALITY",
            )
        }
    }

    @Test
    fun `uptrend requires all three price conditions without using future candles`() {
        val eventDate = LocalDate.of(2026, 7, 24)
        val risingCandles = candlesFromCloses(
            token = 1L,
            endDate = eventDate,
            closes = (1..240).map(Int::toDouble),
        )
        val contextWithoutFuture = buildAbsoluteDeliveryTrendContexts(
            candles = risingCandles,
            criteria = AbsoluteDeliveryCriteria(),
        )[1L]
        val contextWithFuture = buildAbsoluteDeliveryTrendContexts(
            candles = risingCandles + candle(1L, eventDate.plusDays(1L), 10_000.0),
            criteria = AbsoluteDeliveryCriteria(),
        )[1L]

        val evaluation = evaluateAbsoluteDeliveryTrend(
            context = contextWithoutFuture,
            tradingDate = eventDate,
            criteria = AbsoluteDeliveryCriteria(),
        )
        val evaluationWithFuture = evaluateAbsoluteDeliveryTrend(
            context = contextWithFuture,
            tradingDate = eventDate,
            criteria = AbsoluteDeliveryCriteria(),
        )

        assertTrue(evaluation.priceAboveSma50Passed)
        assertTrue(evaluation.sma50AboveSma200Passed)
        assertTrue(evaluation.sma50RisingPassed)
        assertTrue(evaluation.matched)
        assertEquals(evaluation, evaluationWithFuture)

        val priceBelowEvaluation = trendEvaluation(
            closes = (1..239).map(Int::toDouble) + 100.0,
            eventDate = eventDate,
        )
        assertFalse(priceBelowEvaluation.priceAboveSma50Passed)
        assertFalse(priceBelowEvaluation.matched)

        val weakStackEvaluation = trendEvaluation(
            closes = List(190) { 300.0 } + List(49) { 100.0 } + 200.0,
            eventDate = eventDate,
        )
        assertTrue(weakStackEvaluation.priceAboveSma50Passed)
        assertFalse(weakStackEvaluation.sma50AboveSma200Passed)
        assertFalse(weakStackEvaluation.matched)

        val fallingSmaCloses = List(190) { 100.0 } +
            List(30) { 400.0 } +
            List(19) { 50.0 } +
            300.0
        val fallingSmaEvaluation = trendEvaluation(
            closes = fallingSmaCloses,
            eventDate = eventDate,
        )
        assertTrue(fallingSmaEvaluation.priceAboveSma50Passed)
        assertTrue(fallingSmaEvaluation.sma50AboveSma200Passed)
        assertFalse(fallingSmaEvaluation.sma50RisingPassed)
        assertFalse(fallingSmaEvaluation.matched)
    }

    @Test
    fun `missing candle and short history never qualify as an uptrend`() {
        val eventDate = LocalDate.of(2026, 7, 24)
        val shortCandles = candlesFromCloses(
            token = 1L,
            endDate = eventDate,
            closes = (1..100).map(Int::toDouble),
        )
        val context = buildAbsoluteDeliveryTrendContexts(
            candles = shortCandles,
            criteria = AbsoluteDeliveryCriteria(),
        )[1L]

        assertEquals(
            AbsoluteDeliveryTrendDataStatus.INSUFFICIENT_HISTORY,
            evaluateAbsoluteDeliveryTrend(
                context = context,
                tradingDate = eventDate,
                criteria = AbsoluteDeliveryCriteria(),
            ).dataStatus,
        )
        assertEquals(
            AbsoluteDeliveryTrendDataStatus.NO_CANDLE,
            evaluateAbsoluteDeliveryTrend(
                context = context,
                tradingDate = eventDate.plusDays(1L),
                criteria = AbsoluteDeliveryCriteria(),
            ).dataStatus,
        )
    }

    private fun response(
        members: List<AbsoluteDeliveryWatchlistMember>,
        dates: List<LocalDate>,
        deliveries: List<StockDeliveryDaily>,
        candles: List<DailyCandle>? = null,
    ): AbsoluteDeliveryBacktestResponse {
        val resolvedCandles = candles ?: members.flatMap { member ->
            candlesFromCloses(
                token = member.instrumentToken,
                endDate = dates.max(),
                closes = (1..240).map(Int::toDouble),
            )
        }
        return AbsoluteDeliveryBacktestAnalyzer.buildResponse(
            AbsoluteDeliveryBacktestInput(
                universeKey = "groww_HIGH_QUALITY",
                fromDate = LocalDate.of(2026, 1, 24),
                toDate = LocalDate.of(2026, 7, 24),
                members = members,
                tradingDates = dates,
                deliveries = deliveries,
                candles = resolvedCandles,
            ),
        )
    }

    private fun trendEvaluation(
        closes: List<Double>,
        eventDate: LocalDate,
    ): AbsoluteDeliveryTrendEvaluation {
        val context = buildAbsoluteDeliveryTrendContexts(
            candles = candlesFromCloses(token = 1L, endDate = eventDate, closes = closes),
            criteria = AbsoluteDeliveryCriteria(),
        )[1L]
        return evaluateAbsoluteDeliveryTrend(
            context = context,
            tradingDate = eventDate,
            criteria = AbsoluteDeliveryCriteria(),
        )
    }

    private fun candlesFromCloses(
        token: Long,
        endDate: LocalDate,
        closes: List<Double>,
    ): List<DailyCandle> =
        closes.mapIndexed { index, close ->
            candle(
                token = token,
                date = endDate.minusDays((closes.lastIndex - index).toLong()),
                close = close,
            )
        }

    private fun candle(token: Long, date: LocalDate, close: Double): DailyCandle =
        DailyCandle(
            instrumentToken = token,
            symbol = "TEST",
            candleDate = date,
            open = close,
            high = close,
            low = close,
            close = close,
            volume = 1_000L,
        )

    private fun member(symbol: String, token: Long): AbsoluteDeliveryWatchlistMember =
        AbsoluteDeliveryWatchlistMember(
            symbol = symbol,
            companyName = "$symbol LTD",
            instrumentToken = token,
        )

    private fun delivery(
        token: Long,
        date: LocalDate,
        tradedQuantity: Long?,
        deliveryQuantity: Long?,
        deliveryPercentage: Double?,
        reconciliationStatus: DeliveryReconciliationStatus = DeliveryReconciliationStatus.PRESENT,
    ): StockDeliveryDaily {
        return StockDeliveryDaily(
            instrumentToken = token,
            symbol = "TEST",
            exchange = "NSE",
            universe = "watchlist",
            tradingDate = date,
            reconciliationStatus = reconciliationStatus,
            series = "EQ",
            ttlTrdQnty = tradedQuantity,
            delivQty = deliveryQuantity,
            delivPer = deliveryPercentage,
            sourceFileName = null,
            sourceUrl = null,
            fetchedAt = null,
        )
    }
}
