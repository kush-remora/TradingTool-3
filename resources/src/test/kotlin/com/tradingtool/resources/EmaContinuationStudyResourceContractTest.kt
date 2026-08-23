package com.tradingtool.resources

import com.tradingtool.core.strategy.emacontinuation.EmaContinuationStudyRequest
import com.tradingtool.core.strategy.emacontinuation.EmaContinuationWatchlistStudyRequest
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EmaContinuationStudyResourceContractTest {
    @Test
    fun `study request validates watchlist and one stock selection`() {
        val request = validateEmaContinuationStudyRequest(
            EmaContinuationStudyRequest(
                watchlistKey = " leaders ",
                symbol = " netweb ",
                instrumentToken = 123L,
            ),
        )

        assertEquals("leaders", request.watchlistKey)
        assertEquals("NETWEB", request.symbol)
    }

    @Test
    fun `study request rejects missing single stock token`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateEmaContinuationStudyRequest(
                EmaContinuationStudyRequest(watchlistKey = "leaders", symbol = "NETWEB", instrumentToken = null),
            )
        }

        assertEquals("instrumentToken must be positive.", error.message)
    }

    @Test
    fun `watchlist study request trims and validates watchlist`() {
        val request = validateEmaContinuationWatchlistStudyRequest(
            EmaContinuationWatchlistStudyRequest(" leaders "),
        )

        assertEquals("leaders", request.watchlistKey)
    }

    @Test
    fun `watchlist study request rejects a blank watchlist`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateEmaContinuationWatchlistStudyRequest(EmaContinuationWatchlistStudyRequest("  "))
        }

        assertEquals("watchlistKey is required.", error.message)
    }

    @Test
    fun `resource exposes watchlists study and current endpoints`() {
        val paths = StrategyResource::class.java.declaredMethods
            .filter { method -> method.getAnnotation(Path::class.java) != null }
            .mapNotNull { method -> method.getAnnotation(Path::class.java)?.value }

        assertEquals(
            setOf("/ema-continuation/watchlists", "/ema-continuation/study", "/ema-continuation/watchlist-study", "/ema-continuation/current"),
            paths.filter { path -> path.startsWith("/ema-continuation/") }.toSet(),
        )
        assertEquals(4, StrategyResource::class.java.declaredMethods.count { method ->
            method.getAnnotation(Path::class.java)?.value?.startsWith("/ema-continuation/") == true &&
                (method.getAnnotation(GET::class.java) != null || method.name == "runEmaContinuationStudy" || method.name == "runEmaContinuationWatchlistStudy")
        })
    }
}
