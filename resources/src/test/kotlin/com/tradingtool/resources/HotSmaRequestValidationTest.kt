package com.tradingtool.resources

import com.tradingtool.core.strategy.hotsma.HotSmaRunRequest
import com.tradingtool.core.strategy.hotsma.HotSmaSignalTag
import com.tradingtool.core.strategy.hotsma.HotSmaTelegramRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HotSmaRequestValidationTest {

    @Test
    fun `validate hot sma run rejects blank index key`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateHotSmaRunRequest(HotSmaRunRequest(indexKey = "  "))
        }

        assertEquals("indexKey is required.", error.message)
    }

    @Test
    fun `validate hot sma run normalizes mixed key format`() {
        val result = validateHotSmaRunRequest(HotSmaRunRequest(indexKey = " nifty midcap 150 "))

        assertEquals("NIFTY_MIDCAP_150", result.indexKey)
    }

    @Test
    fun `validate hot sma telegram rejects missing symbol`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateHotSmaTelegramRequest(
                HotSmaTelegramRequest(
                    indexKey = "NIFTY_100",
                    symbol = "   ",
                    signalTag = HotSmaSignalTag.WATCH_ZONE,
                    currentPrice = 100.0,
                    sma50 = 98.0,
                    sma100 = 95.0,
                    sma200 = 90.0,
                    pctToSma50 = 2.0,
                    pctToSma100 = 5.0,
                    pctToSma200 = 10.0,
                    rsi14 = 52.0,
                ),
            )
        }

        assertEquals("symbol is required.", error.message)
    }

    @Test
    fun `validate hot sma telegram normalizes symbol and index key`() {
        val result = validateHotSmaTelegramRequest(
            HotSmaTelegramRequest(
                indexKey = "nifty smallcap 250",
                symbol = " infy ",
                signalTag = HotSmaSignalTag.STANDARD_BUY,
                currentPrice = 100.0,
                sma50 = 99.0,
                sma100 = 98.0,
                sma200 = 97.0,
                pctToSma50 = 1.0,
                pctToSma100 = 2.0,
                pctToSma200 = 3.0,
                rsi14 = 49.5,
            ),
        )

        assertEquals("NIFTY_SMALLCAP_250", result.indexKey)
        assertEquals("INFY", result.symbol)
    }

    @Test
    fun `validate hot sma telegram rejects non positive current price`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateHotSmaTelegramRequest(
                HotSmaTelegramRequest(
                    indexKey = "NIFTY_100",
                    symbol = "INFY",
                    signalTag = HotSmaSignalTag.AGGRESSIVE_BUY,
                    currentPrice = 0.0,
                    sma50 = 100.0,
                    sma100 = 99.0,
                    sma200 = 98.0,
                    pctToSma50 = 0.0,
                    pctToSma100 = 1.0,
                    pctToSma200 = 2.0,
                    rsi14 = 50.0,
                ),
            )
        }

        assertEquals("currentPrice must be a positive number.", error.message)
    }
}
