package com.tradingtool.core.strategy.absolutedelivery

import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
                delivery(1L, dates[0], 20_000_000L, 10_000_001L, 60.01),
                delivery(1L, dates[1], 20_000_000L, 10_000_000L, 60.01),
                delivery(1L, dates[2], 20_000_000L, 10_000_001L, 60.0),
                delivery(1L, dates[3], 19_999_999L, 10_000_001L, 60.01),
            ),
        )

        val rowsByDate = response.allRows.associateBy { row -> row.tradingDate }
        assertTrue(requireNotNull(rowsByDate[dates[0].toString()]).matched)
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

    private fun response(
        members: List<AbsoluteDeliveryWatchlistMember>,
        dates: List<LocalDate>,
        deliveries: List<StockDeliveryDaily>,
    ): AbsoluteDeliveryBacktestResponse {
        return AbsoluteDeliveryBacktestAnalyzer.buildResponse(
            AbsoluteDeliveryBacktestInput(
                universeKey = "groww_HIGH_QUALITY",
                fromDate = LocalDate.of(2026, 1, 24),
                toDate = LocalDate.of(2026, 7, 24),
                members = members,
                tradingDates = dates,
                deliveries = deliveries,
            ),
        )
    }

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
