package com.tradingtool.resources

import com.tradingtool.core.strategy.deliverythreshold.DeliveryThresholdBacktestConfig
import com.tradingtool.core.strategy.deliverythreshold.DeliveryThresholdBacktestRequest
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
    fun `validate fails when selected index threshold is missing`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateDeliveryThresholdBacktestRequest(
                DeliveryThresholdBacktestRequest(
                    indexKeys = listOf("NIFTY_SMALLCAP_250"),
                    config = DeliveryThresholdBacktestConfig(
                        thresholds = mapOf("NIFTY_LARGEMIDCAP_250" to 55.0),
                        profitPct = 10.0,
                    ),
                ),
            )
        }

        assertEquals("Missing threshold for selected indexKey=NIFTY_SMALLCAP_250.", error.message)
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
}
