package com.tradingtool.resources

import com.tradingtool.core.strategy.deliverythreshold.DeliveryThresholdBacktestConfig
import com.tradingtool.core.strategy.deliverythreshold.DeliveryThresholdBacktestRequest
import com.tradingtool.core.strategy.deliverythreshold.DeliveryThresholdRocConfig
import com.tradingtool.core.strategy.deliverythreshold.DeliveryThresholdSma200Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DeliveryThresholdBacktestRequestValidationTest {

    @Test
    fun `validate defaults to 1 year when dates omitted`() {
        val result = validateDeliveryThresholdBacktestRequest(
            DeliveryThresholdBacktestRequest(
                indexKeys = listOf("NIFTY_LARGEMIDCAP_250"),
                config = DeliveryThresholdBacktestConfig(
                    thresholds = mapOf("NIFTY_LARGEMIDCAP_250" to 55.0),
                    profitPct = 10.0,
                ),
            ),
        )

        assertEquals(365L, java.time.temporal.ChronoUnit.DAYS.between(result.fromDate, result.toDate))
        assertEquals(10.0, result.profitPct)
    }

    @Test
    fun `validate accepts mixed index key formats`() {
        val result = validateDeliveryThresholdBacktestRequest(
            DeliveryThresholdBacktestRequest(
                indexKeys = listOf("nifty-microcap-250"),
                config = DeliveryThresholdBacktestConfig(
                    thresholds = mapOf("NIFTY_MICROCAP_250" to 85.0),
                    profitPct = 10.0,
                ),
            ),
        )

        assertEquals(listOf("nifty-microcap-250"), result.indexKeys)
        assertEquals(85.0, result.thresholdsByIndex["NIFTY_MICROCAP_250"])
    }

    @Test
    fun `validate uses backend default threshold for known index when omitted`() {
        val result = validateDeliveryThresholdBacktestRequest(
            DeliveryThresholdBacktestRequest(
                indexKeys = listOf("NIFTY_SMALLCAP_250"),
                config = DeliveryThresholdBacktestConfig(
                    thresholds = mapOf("NIFTY_LARGEMIDCAP_250" to 55.0),
                    profitPct = 10.0,
                ),
            ),
        )

        assertEquals(70.0, result.thresholdsByIndex["NIFTY_SMALLCAP_250"])
    }

    @Test
    fun `validate fails when selected index threshold is missing for unknown key`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateDeliveryThresholdBacktestRequest(
                DeliveryThresholdBacktestRequest(
                    indexKeys = listOf("NIFTY_UNKNOWN_999"),
                    config = DeliveryThresholdBacktestConfig(
                        thresholds = mapOf("NIFTY_LARGEMIDCAP_250" to 55.0),
                        profitPct = 10.0,
                    ),
                ),
            )
        }

        assertEquals("Missing threshold for selected indexKey=NIFTY_UNKNOWN_999.", error.message)
    }

    @Test
    fun `validate fails when profit pct is invalid`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateDeliveryThresholdBacktestRequest(
                DeliveryThresholdBacktestRequest(
                    indexKeys = listOf("NIFTY_LARGEMIDCAP_250"),
                    config = DeliveryThresholdBacktestConfig(
                        thresholds = mapOf("NIFTY_LARGEMIDCAP_250" to 55.0),
                        profitPct = 0.0,
                    ),
                ),
            )
        }

        assertEquals("profitPct must be a positive number.", error.message)
    }

    @Test
    fun `validate merges roc and sma config for nifty 50 from backend defaults`() {
        val defaultConfig = DeliveryThresholdBacktestConfig(
            thresholds = mapOf("NIFTY_50" to 55.0),
            profitPct = 10.0,
            roc20ByIndex = mapOf(
                "NIFTY_50" to DeliveryThresholdRocConfig(
                    accumulationMinPct = -5.0,
                    accumulationMaxPct = 5.0,
                    distributionMinPct = 15.0,
                ),
            ),
            sma200ByIndex = mapOf(
                "NIFTY_50" to DeliveryThresholdSma200Config(
                    accumulationMaxDistancePct = 5.0,
                    distributionMinDistancePct = 20.0,
                ),
            ),
        )

        val result = validateDeliveryThresholdBacktestRequest(
            request = DeliveryThresholdBacktestRequest(
                indexKeys = listOf("NIFTY_50"),
                config = DeliveryThresholdBacktestConfig(
                    thresholds = emptyMap(),
                    profitPct = 10.0,
                ),
            ),
            defaultConfig = defaultConfig,
        )

        assertEquals(-5.0, result.roc20ByIndex["NIFTY_50"]?.accumulationMinPct)
        assertEquals(5.0, result.roc20ByIndex["NIFTY_50"]?.accumulationMaxPct)
        assertEquals(15.0, result.roc20ByIndex["NIFTY_50"]?.distributionMinPct)
        assertEquals(5.0, result.sma200ByIndex["NIFTY_50"]?.accumulationMaxDistancePct)
        assertEquals(20.0, result.sma200ByIndex["NIFTY_50"]?.distributionMinDistancePct)
    }
}
