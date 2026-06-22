package com.tradingtool.core.strategy.wyckoff.deliverythreshold

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeliveryThresholdBacktestEngineTest {

    private val engine = DeliveryThresholdBacktestEngine()

    @Test
    fun `resolveHighestThresholdMembership picks max threshold`() {
        val members = listOf(
            IndexConstituentUpsertRow(
                indexKey = "NIFTY_SMALLCAP_250",
                symbol = "ABC",
                instrumentToken = 1,
                companyName = "ABC LTD",
                industry = "",
                series = "EQ",
                isinCode = "",
                sourceUrl = "",
            ),
            IndexConstituentUpsertRow(
                indexKey = "NIFTY_LARGEMIDCAP_250",
                symbol = "ABC",
                instrumentToken = 1,
                companyName = "ABC LTD",
                industry = "",
                series = "EQ",
                isinCode = "",
                sourceUrl = "",
            ),
        )

        val resolved = resolveHighestThresholdMembership(
            members = members,
            thresholdsByIndex = mapOf(
                "NIFTY_SMALLCAP_250" to 70.0,
                "NIFTY_LARGEMIDCAP_250" to 55.0,
            ),
        )

        assertNotNull(resolved)
        assertEquals("NIFTY_SMALLCAP_250", resolved?.member?.indexKey)
        assertEquals(70.0, resolved?.threshold)
    }

    @Test
    fun `consecutive delivery signals create multiple trades`() {
        val context = DeliveryThresholdSymbolContext(
            symbol = "ABC",
            instrumentToken = 1,
            companyName = "ABC LTD",
            resolvedIndexKey = "NIFTY_SMALLCAP_250",
            threshold = 70.0,
            candles = candles(
                100.0,
                102.0,
                104.0,
                106.0,
                108.0,
                110.0,
                112.0,
                114.0,
                116.0,
                118.0,
                120.0,
                122.0,
                124.0,
                126.0,
                128.0,
                130.0,
                132.0,
                134.0,
                136.0,
                138.0,
                140.0,
                142.0,
                144.0,
                146.0,
                148.0,
            ),
            deliveries = listOf(
                delivery(1, LocalDate.of(2026, 1, 20), 75.0, 100_000),
                delivery(1, LocalDate.of(2026, 1, 21), 80.0, 101_000),
            ),
        )

        val response = engine.run(
            config = DeliveryThresholdBacktestRunConfig(
                indexKeys = listOf("NIFTY_SMALLCAP_250"),
                symbols = emptyList(),
                thresholdsByIndex = mapOf("NIFTY_SMALLCAP_250" to 70.0),
                profitPct = 10.0,
                fromDate = LocalDate.of(2026, 1, 1),
                toDate = LocalDate.of(2026, 2, 10),
            ),
            contexts = listOf(context),
        )

        assertEquals(2, response.rows.size)
        assertEquals(2, response.summary.totalBuys)
    }

    @Test
    fun `open trade keeps exit fields null and floating pnl computed`() {
        val context = DeliveryThresholdSymbolContext(
            symbol = "XYZ",
            instrumentToken = 2,
            companyName = "XYZ LTD",
            resolvedIndexKey = "NIFTY_LARGEMIDCAP_250",
            threshold = 55.0,
            candles = candles(
                100.0,
                101.0,
                100.5,
                100.2,
                100.8,
                100.4,
                100.6,
                100.7,
                100.9,
                100.3,
                100.5,
                100.4,
                100.2,
                100.1,
                100.0,
                99.8,
                100.1,
                100.2,
                100.1,
                100.0,
                100.2,
                100.1,
            ),
            deliveries = listOf(delivery(2, LocalDate.of(2026, 1, 18), 60.0, 90_000)),
        )

        val response = engine.run(
            config = DeliveryThresholdBacktestRunConfig(
                indexKeys = listOf("NIFTY_LARGEMIDCAP_250"),
                symbols = emptyList(),
                thresholdsByIndex = mapOf("NIFTY_LARGEMIDCAP_250" to 55.0),
                profitPct = 10.0,
                fromDate = LocalDate.of(2026, 1, 1),
                toDate = LocalDate.of(2026, 2, 10),
            ),
            contexts = listOf(context),
        )

        assertEquals(1, response.rows.size)
        val row = response.rows.first()
        assertEquals("OPEN", row.status)
        assertNull(row.exitDate)
        assertNull(row.exitPrice)
        assertNull(row.rsiSell)
        assertNotNull(row.floatingPnlPct)
        assertEquals(1, response.summary.openCount)
    }

    private fun candles(vararg closes: Double): List<DailyCandle> {
        val start = LocalDate.of(2026, 1, 1)
        return closes.mapIndexed { index, close ->
            DailyCandle(
                instrumentToken = 1,
                symbol = "ABC",
                candleDate = start.plusDays(index.toLong()),
                open = close,
                high = close * 1.02,
                low = close * 0.98,
                close = close,
                volume = 100_000,
            )
        }
    }

    private fun delivery(
        token: Long,
        date: LocalDate,
        deliveryPct: Double,
        totalVolume: Long,
    ): StockDeliveryDaily {
        return StockDeliveryDaily(
            instrumentToken = token,
            symbol = "ABC",
            exchange = "NSE",
            universe = "SMALLCAP_250",
            tradingDate = date,
            reconciliationStatus = DeliveryReconciliationStatus.PRESENT,
            series = "EQ",
            ttlTrdQnty = totalVolume,
            delivQty = totalVolume / 2,
            delivPer = deliveryPct,
            sourceFileName = null,
            sourceUrl = null,
            fetchedAt = null,
        )
    }
}
