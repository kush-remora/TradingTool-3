package com.tradingtool.core.strategy.wyckoff.phase1

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WyckoffPhase1ScannerEngineTest {

    private val engine = WyckoffPhase1ScannerEngine()

    @Test
    fun `returns latest matched date in last 5 sessions and run length`() {
        val start = LocalDate.of(2026, 1, 1)
        val candles = (0 until 40).map { index ->
            DailyCandle(
                instrumentToken = 1,
                symbol = "ABC",
                candleDate = start.plusDays(index.toLong()),
                open = 100.0,
                high = 102.0,
                low = 98.0,
                close = 100.0,
                volume = 100_000L + index,
            )
        }

        val deliveries = (0 until 40).map { index ->
            val date = start.plusDays(index.toLong())
            val pct = when (index) {
                36 -> 70.0
                37 -> 50.0
                38 -> 65.0
                39 -> 72.0
                else -> 40.0
            }
            delivery(date, pct, 100_000 + index.toLong())
        }

        val response = engine.evaluate(
            config = WyckoffPhase1Config(),
            runConfig = WyckoffPhase1RunConfig(
                universeKeys = listOf("NIFTY_SMALLCAP_250"),
                symbols = emptyList(),
                asOfDate = start.plusDays(39),
                applyStrictBaseFilter = false,
            ),
            contexts = listOf(
                WyckoffPhase1SymbolContext(
                    symbol = "ABC",
                    instrumentToken = 1,
                    companyName = "ABC LTD",
                    indexKey = "NIFTY_SMALLCAP_250",
                    deliveryThresholdPct = 55.0,
                    candles = candles,
                    deliveries = deliveries,
                ),
            ),
        )

        assertEquals(1, response.rows.size)
        val row = response.rows.first()
        assertEquals(start.plusDays(39).toString(), row.signal_date)
        assertEquals(2, row.accumulation_run_length_days)
        assertEquals(3, row.lvq_hit_count_15d)
    }

    @Test
    fun `uses available candle count for sma window when less than 200`() {
        val start = LocalDate.of(2026, 1, 1)
        val candles = (0 until 100).map { index ->
            DailyCandle(
                instrumentToken = 2,
                symbol = "IPO",
                candleDate = start.plusDays(index.toLong()),
                open = 100.0,
                high = 103.0,
                low = 97.0,
                close = 100.0,
                volume = 50_000L + index,
            )
        }

        val deliveries = (0 until 100).map { index ->
            val date = start.plusDays(index.toLong())
            val pct = if (index == 99) 80.0 else 40.0
            delivery(date, pct, 50_000 + index.toLong())
        }

        val response = engine.evaluate(
            config = WyckoffPhase1Config(),
            runConfig = WyckoffPhase1RunConfig(
                universeKeys = listOf("WATCHLIST"),
                symbols = emptyList(),
                asOfDate = start.plusDays(99),
                applyStrictBaseFilter = false,
            ),
            contexts = listOf(
                WyckoffPhase1SymbolContext(
                    symbol = "IPO",
                    instrumentToken = 2,
                    companyName = "IPO LTD",
                    indexKey = "WATCHLIST",
                    deliveryThresholdPct = 55.0,
                    candles = candles,
                    deliveries = deliveries,
                ),
            ),
        )

        val row = response.rows.first()
        assertNotNull(row.sma200_distance_pct)
    }

    @Test
    fun `passed count uses seven flags including lvq`() {
        val start = LocalDate.of(2026, 1, 1)
        val candles = (0 until 80).map { index ->
            val close = 100.0 + (index % 3) * 0.1
            val spread = if (index == 79) 0.5 else 4.0
            DailyCandle(
                instrumentToken = 3,
                symbol = "PASS",
                candleDate = start.plusDays(index.toLong()),
                open = close,
                high = close + spread,
                low = close - spread,
                close = close,
                volume = 10_000L + index * 10,
            )
        }

        val deliveries = (0 until 80).map { index ->
            val date = start.plusDays(index.toLong())
            val pct = when {
                index in 65..79 && index % 3 == 0 -> 60.0
                index == 79 -> 80.0
                else -> 40.0
            }
            val volume = if (index == 79) 1_000_000L else 10_000L + index * 10
            delivery(date, pct, volume)
        }

        val response = engine.evaluate(
            config = WyckoffPhase1Config(
                trackA = WyckoffPhase1TrackAConfig(
                    lvqDq = WyckoffPhase1LvqDqConfig(
                        enabled = true,
                        rollingMinDays = 63,
                        nearMinPctOfRollingMin = 1.0,
                        lookbackDays = 15,
                        requireDeliveryPass = true,
                    ),
                ),
            ),
            runConfig = WyckoffPhase1RunConfig(
                universeKeys = listOf("NIFTY_LARGEMIDCAP_250"),
                symbols = emptyList(),
                asOfDate = start.plusDays(79),
                applyStrictBaseFilter = false,
            ),
            contexts = listOf(
                WyckoffPhase1SymbolContext(
                    symbol = "PASS",
                    instrumentToken = 3,
                    companyName = "PASS LTD",
                    indexKey = "NIFTY_LARGEMIDCAP_250",
                    deliveryThresholdPct = 55.0,
                    candles = candles,
                    deliveries = deliveries,
                ),
            ),
        )

        val row = response.rows.first()
        assertEquals(6, row.lvq_hit_count_15d)
    }

    @Test
    fun `includes row when zscore passes even if delivery fails`() {
        val start = LocalDate.of(2026, 1, 1)
        val candles = (0 until 70).map { index ->
            DailyCandle(
                instrumentToken = 4,
                symbol = "ZSCORE",
                candleDate = start.plusDays(index.toLong()),
                open = 100.0,
                high = 101.0,
                low = 99.0,
                close = 100.0,
                volume = if (index == 69) 1_000_000L else 100_000L + index,
            )
        }
        val deliveries = (0 until 70).map { index ->
            val date = start.plusDays(index.toLong())
            val volume = if (index == 69) 1_000_000L else 100_000L + index
            delivery(date, 40.0, volume)
        }

        val response = engine.evaluate(
            config = WyckoffPhase1Config(),
            runConfig = WyckoffPhase1RunConfig(
                universeKeys = listOf("NIFTY_LARGEMIDCAP_250"),
                symbols = emptyList(),
                asOfDate = start.plusDays(69),
                applyStrictBaseFilter = false,
            ),
            contexts = listOf(
                WyckoffPhase1SymbolContext(
                    symbol = "ZSCORE",
                    instrumentToken = 4,
                    companyName = "ZSCORE LTD",
                    indexKey = "NIFTY_LARGEMIDCAP_250",
                    deliveryThresholdPct = 55.0,
                    candles = candles,
                    deliveries = deliveries,
                ),
            ),
        )

        assertEquals(1, response.rows.size)
        val row = response.rows.first()
        assertEquals(start.plusDays(69).toString(), row.signal_date)
        assertNotNull(row.delivery_volume_zscore_60d)
    }

    @Test
    fun `skips moving average compression when price is below 200 sma`() {
        val start = LocalDate.of(2025, 1, 1)
        val candles = (0 until 220).map { index ->
            val close = if (index < 170) {
                120.0
            } else {
                100.0
            }
            DailyCandle(
                instrumentToken = 5,
                symbol = "BELOW200",
                candleDate = start.plusDays(index.toLong()),
                open = close,
                high = close + 1.0,
                low = close - 1.0,
                close = close,
                volume = 100_000L + index,
            )
        }
        val deliveries = (0 until 220).map { index ->
            val date = start.plusDays(index.toLong())
            val pct = if (index in 205..219) 60.0 else 40.0
            delivery(date, pct, 100_000L + index.toLong())
        }

        val response = engine.evaluate(
            config = WyckoffPhase1Config(
                strictFilter = WyckoffPhase1StrictFilterConfig(
                    dma200Proximity = WyckoffPhase1RangeConfig(enabled = true, minDistancePct = -100.0, maxDistancePct = 2.0),
                    roc20Proximity = WyckoffPhase1RocRangeConfig(enabled = false),
                    movingAverageCompression = WyckoffPhase1MovingAverageCompressionConfig(enabled = true, maxDma50To200DistancePct = 3.0),
                    volatilityContraction = WyckoffPhase1VolatilityContractionConfig(enabled = false, requireSpreadLessThan20dAverage = false),
                    accumulationDensity = WyckoffPhase1AccumulationDensityConfig(enabled = true, minTier55Count = 3),
                ),
            ),
            runConfig = WyckoffPhase1RunConfig(
                universeKeys = listOf("NIFTY_100"),
                symbols = emptyList(),
                asOfDate = start.plusDays(219),
                applyStrictBaseFilter = true,
            ),
            contexts = listOf(
                WyckoffPhase1SymbolContext(
                    symbol = "BELOW200",
                    instrumentToken = 5,
                    companyName = "BELOW200 LTD",
                    indexKey = "NIFTY_100",
                    deliveryThresholdPct = 55.0,
                    candles = candles,
                    deliveries = deliveries,
                ),
            ),
        )

        assertEquals(1, response.rows.size)
        val row = response.rows.first()
        assertTrue((row.sma200_distance_pct ?: 0.0) < 0.0)
    }

    @Test
    fun `allows negative roc20 when strict filter is enabled`() {
        val start = LocalDate.of(2025, 1, 1)
        val candles = (0 until 220).map { index ->
            val close = if (index < 200) {
                120.0
            } else {
                100.0
            }
            DailyCandle(
                instrumentToken = 6,
                symbol = "NEGROC",
                candleDate = start.plusDays(index.toLong()),
                open = close,
                high = close + 1.0,
                low = close - 1.0,
                close = close,
                volume = 100_000L + index,
            )
        }
        val deliveries = (0 until 220).map { index ->
            val date = start.plusDays(index.toLong())
            val pct = if (index in 205..219) 60.0 else 40.0
            delivery(date, pct, 100_000L + index.toLong())
        }

        val response = engine.evaluate(
            config = WyckoffPhase1Config(
                strictFilter = WyckoffPhase1StrictFilterConfig(
                    dma200Proximity = WyckoffPhase1RangeConfig(enabled = true, minDistancePct = -100.0, maxDistancePct = 2.0),
                    roc20Proximity = WyckoffPhase1RocRangeConfig(enabled = true, minPct = -100.0, maxPct = 2.0),
                    movingAverageCompression = WyckoffPhase1MovingAverageCompressionConfig(enabled = false),
                    volatilityContraction = WyckoffPhase1VolatilityContractionConfig(enabled = false, requireSpreadLessThan20dAverage = false),
                    accumulationDensity = WyckoffPhase1AccumulationDensityConfig(enabled = true, minTier55Count = 3),
                ),
            ),
            runConfig = WyckoffPhase1RunConfig(
                universeKeys = listOf("NIFTY_100"),
                symbols = emptyList(),
                asOfDate = start.plusDays(219),
                applyStrictBaseFilter = true,
            ),
            contexts = listOf(
                WyckoffPhase1SymbolContext(
                    symbol = "NEGROC",
                    instrumentToken = 6,
                    companyName = "NEGROC LTD",
                    indexKey = "NIFTY_100",
                    deliveryThresholdPct = 55.0,
                    candles = candles,
                    deliveries = deliveries,
                ),
            ),
        )

        assertEquals(1, response.rows.size)
        val row = response.rows.first()
        assertTrue((row.roc20_pct ?: 0.0) < 0.0)
    }

    private fun delivery(
        date: LocalDate,
        deliveryPct: Double,
        totalVolume: Long,
    ): StockDeliveryDaily {
        return StockDeliveryDaily(
            stockId = null,
            instrumentToken = 1,
            symbol = "ABC",
            exchange = "NSE",
            universe = "NIFTY",
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
