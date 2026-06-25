package com.tradingtool.core.strategy.fiftytwohigh

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ChartinkFiftyTwoWeekHighBacktestEngineTest {

    private val engine = ChartinkFiftyTwoWeekHighBacktestEngine()
    private val strategy = ChartinkFiftyTwoWeekHighBacktestStrategy(
        name = "target_5_stop_5",
        profitTargetPct = 5.0,
        stopLossPct = 5.0,
    )

    @Test
    fun `target hit counts as success`() {
        val report = engine.run(
            inputFile = "manual-input/test.csv",
            priceDataToDate = LocalDate.of(2026, 1, 31),
            strategies = listOf(strategy),
            contexts = listOf(
                ChartinkFiftyTwoWeekHighSymbolContext(
                    signal = signal("ABC", LocalDate.of(2026, 1, 2), "Largecap"),
                    candles = listOf(
                        candle(LocalDate.of(2026, 1, 2), 100.0, 102.0, 99.0, 101.0),
                        candle(LocalDate.of(2026, 1, 3), 100.0, 106.0, 99.0, 105.0),
                        candle(LocalDate.of(2026, 1, 4), 105.0, 107.0, 104.0, 106.0),
                    ),
                ),
            ),
        )

        val trade = report.trades.single()
        assertEquals("TARGET_HIT", trade.outcome)
        assertTrue(trade.success)
        assertEquals(0, trade.holdingTradingDays)
        assertEquals(5.0, trade.returnPct)
    }

    @Test
    fun `ambiguous bar exits at stop loss conservatively`() {
        val report = engine.run(
            inputFile = "manual-input/test.csv",
            priceDataToDate = LocalDate.of(2026, 1, 31),
            strategies = listOf(strategy),
            contexts = listOf(
                ChartinkFiftyTwoWeekHighSymbolContext(
                    signal = signal("ABC", LocalDate.of(2026, 1, 2), "Midcap"),
                    candles = listOf(
                        candle(LocalDate.of(2026, 1, 2), 100.0, 102.0, 99.0, 101.0),
                        candle(LocalDate.of(2026, 1, 3), 100.0, 106.0, 94.0, 102.0),
                    ),
                ),
            ),
        )

        val trade = report.trades.single()
        assertEquals("STOP_LOSS", trade.outcome)
        assertFalse(trade.success)
        assertTrue(trade.exitWasAmbiguous)
        assertEquals(-5.0, trade.returnPct)
    }

    @Test
    fun `summary groups success by market cap bucket`() {
        val report = engine.run(
            inputFile = "manual-input/test.csv",
            priceDataToDate = LocalDate.of(2026, 1, 31),
            strategies = listOf(strategy),
            contexts = listOf(
                ChartinkFiftyTwoWeekHighSymbolContext(
                    signal = signal("AAA", LocalDate.of(2026, 1, 2), "Largecap"),
                    candles = listOf(
                        candle(LocalDate.of(2026, 1, 2), 100.0, 101.0, 99.0, 100.0),
                        candle(LocalDate.of(2026, 1, 3), 100.0, 106.0, 99.0, 104.0),
                    ),
                ),
                ChartinkFiftyTwoWeekHighSymbolContext(
                    signal = signal("BBB", LocalDate.of(2026, 1, 2), "Smallcap"),
                    candles = listOf(
                        candle(LocalDate.of(2026, 1, 2), 100.0, 101.0, 99.0, 100.0),
                        candle(LocalDate.of(2026, 1, 3), 100.0, 101.0, 94.0, 95.0),
                    ),
                ),
            ),
        )

        val summary = report.summaries.single()
        assertEquals(2, summary.enteredTrades)
        assertEquals(1, summary.successCount)
        assertEquals(50.0, summary.successRatePct)
        assertEquals(100.0, summary.buckets.first { it.marketCapBucket == "Largecap" }.successRatePct)
        assertEquals(0.0, summary.buckets.first { it.marketCapBucket == "Smallcap" }.successRatePct)
    }

    private fun signal(symbol: String, signalDate: LocalDate, marketCapName: String): ChartinkFiftyTwoWeekHighSignal {
        return ChartinkFiftyTwoWeekHighSignal(
            signalDate = signalDate,
            symbol = symbol,
            marketCapName = marketCapName,
            sector = "Financials",
        )
    }

    private fun candle(
        date: LocalDate,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
    ): DailyCandle {
        return DailyCandle(
            instrumentToken = 1L,
            symbol = "ABC",
            candleDate = date,
            open = open,
            high = high,
            low = low,
            close = close,
            volume = 100_000L,
        )
    }
}
