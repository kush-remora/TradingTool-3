package com.tradingtool.core.strategy.phasedbreakout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PhaseCExportWakeUpVolumeTest {
    @Test
    fun `builds wake-up export when latest and previous day volumes exist`() {
        val result = buildWakeUpVolumeExport(
            watchlistRow(
                volume = 250_000L,
                previousDayVolume = 100_000L,
            ),
        )

        requireNotNull(result)
        assertEquals(250_000L, result.latestDayVolume)
        assertEquals(100_000L, result.previousDayVolume)
        assertEquals(2.5, result.volumeRatioVsPreviousDay)
        assertTrue(result.volumeIs2xOrMore)
    }

    @Test
    fun `marks wake-up export false when ratio stays below 2x`() {
        val result = buildWakeUpVolumeExport(
            watchlistRow(
                volume = 150_000L,
                previousDayVolume = 100_000L,
            ),
        )

        requireNotNull(result)
        assertEquals(1.5, result.volumeRatioVsPreviousDay)
        assertFalse(result.volumeIs2xOrMore)
    }

    @Test
    fun `returns null when previous day volume is unavailable or invalid`() {
        assertNull(buildWakeUpVolumeExport(watchlistRow(volume = 150_000L, previousDayVolume = null)))
        assertNull(buildWakeUpVolumeExport(watchlistRow(volume = 150_000L, previousDayVolume = 0L)))
        assertNull(buildWakeUpVolumeExport(watchlistRow(volume = null, previousDayVolume = 100_000L)))
    }

    private fun watchlistRow(
        volume: Long?,
        previousDayVolume: Long?,
    ): PhaseCWatchlistRow {
        return PhaseCWatchlistRow(
            symbol = "INFY",
            instrumentToken = 408065L,
            addedOn = LocalDate.of(2026, 6, 24),
            lastSeenOn = LocalDate.of(2026, 6, 24),
            status = "chartinkFilter",
            stockName = "Infosys",
            marketCapBucket = "Large Cap",
            closePrice = 1_540.5,
            pctChange = "2.10%",
            volume = volume,
            previousDayVolume = previousDayVolume,
            sector = "IT",
            industry = "Software",
            rocePct = 31.2,
            ronwPct = 28.1,
            netProfitAfterTax = 1000.0,
            debtEquityRatio = 0.0,
            volDry200dMinCount = 2,
            volDry60dMinCount = 3,
            volDry200dMin105Count = 5,
            volDry60dMin105Count = 6,
            indianPromoterPct = 14.8,
            foreignPromoterPct = 33.1,
            quarterlyGrossSales = 100.0,
            high52w = 1800.0,
            low52w = 1490.0,
            dist200dHighPct = -12.5,
            dist200dLowPct = 1.0,
            atrLt2pctCount = 4,
            marketFieldsUpdatedOn = LocalDate.of(2026, 6, 24),
            phase2DeliveryStatus = "NOT_RUN",
            phase2Reason = "awaiting_delivery_validation",
            phase2EvaluatedOn = null,
            deliveryQuantityToday = null,
            deliveryPctToday = null,
            wholesaleBaseDq = null,
            deliverySpikeRatio = null,
            deliverySpikeDays10d = null,
            deliverySpikeDays20d = null,
            deliverySupportDays10d = null,
            deliverySupportDays20d = null,
        )
    }
}
