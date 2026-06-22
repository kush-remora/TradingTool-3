package com.tradingtool.core.volumeshocker.groww

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class GrowwVolumeShockerIngestionServiceTest {

    @Test
    fun `ingest resolves every token and replaces the requested date`() = runBlocking {
        val sourceRows = buildSourceRows()
        var replacedDate: LocalDate? = null
        var replacedRows = emptyList<GrowwVolumeShockerDailyRow>()
        val service = GrowwVolumeShockerIngestionService(
            source = fixedSource(sourceRows),
            instrumentTokenResolver = resolver { _, symbol -> symbol.removePrefix("STOCK").toLong() },
            gateway = gateway { tradeDate, rows ->
                replacedDate = tradeDate
                replacedRows = rows
                rows.size
            },
        )

        val tradeDate = LocalDate.parse("2026-06-22")
        val result = service.ingest(GrowwVolumeShockerIngestionRequest(tradeDate))

        assertEquals(100, result.fetchedCount)
        assertEquals(100, result.storedCount)
        assertEquals(tradeDate, replacedDate)
        assertEquals(100, replacedRows.size)
        assertEquals(1L, replacedRows.first().instrumentToken)
        assertEquals(MarketCapCategory.SMALL, replacedRows.first().marketCapCategory)
    }

    @Test
    fun `ingest rejects an incomplete list before resolving tokens or writing`() {
        var resolverCalled = false
        var gatewayCalled = false
        val service = GrowwVolumeShockerIngestionService(
            source = fixedSource(buildSourceRows().dropLast(1)),
            instrumentTokenResolver = resolver { _, _ ->
                resolverCalled = true
                1L
            },
            gateway = gateway { _, _ ->
                gatewayCalled = true
                0
            },
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.ingest(GrowwVolumeShockerIngestionRequest(LocalDate.parse("2026-06-22")))
            }
        }

        assertEquals(
            "Groww volume-shocker input must contain exactly 100 rows; found 99.",
            error.message,
        )
        assertFalse(resolverCalled)
        assertFalse(gatewayCalled)
    }

    @Test
    fun `ingest rejects duplicate exchange-symbol rows before writing`() {
        var gatewayCalled = false
        val rows = buildSourceRows().map { row ->
            if (row.sourceRank == 2) row.copy(symbol = "STOCK1") else row
        }
        val service = GrowwVolumeShockerIngestionService(
            source = fixedSource(rows),
            instrumentTokenResolver = resolver { _, symbol -> symbol.removePrefix("STOCK").toLong() },
            gateway = gateway { _, _ ->
                gatewayCalled = true
                0
            },
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.ingest(GrowwVolumeShockerIngestionRequest(LocalDate.parse("2026-06-22")))
            }
        }

        assertEquals("Groww volume-shocker input contains duplicate exchange-symbol rows.", error.message)
        assertFalse(gatewayCalled)
    }

    @Test
    fun `ingest rejects an unresolved token before writing`() {
        var gatewayCalled = false
        val service = GrowwVolumeShockerIngestionService(
            source = fixedSource(buildSourceRows()),
            instrumentTokenResolver = resolver { _, symbol ->
                if (symbol == "STOCK50") null else 1L
            },
            gateway = gateway { _, _ ->
                gatewayCalled = true
                0
            },
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.ingest(GrowwVolumeShockerIngestionRequest(LocalDate.parse("2026-06-22")))
            }
        }

        assertEquals(
            "Instrument token could not be resolved for NSE:STOCK50 at rank 50.",
            error.message,
        )
        assertFalse(gatewayCalled)
    }

    @Test
    fun `ingest rejects duplicate resolved instrument tokens before writing`() {
        var gatewayCalled = false
        val service = GrowwVolumeShockerIngestionService(
            source = fixedSource(buildSourceRows()),
            instrumentTokenResolver = resolver { _, _ -> 1L },
            gateway = gateway { _, _ ->
                gatewayCalled = true
                0
            },
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.ingest(GrowwVolumeShockerIngestionRequest(LocalDate.parse("2026-06-22")))
            }
        }

        assertEquals(
            "Groww volume-shocker input resolves multiple rows to the same instrument token.",
            error.message,
        )
        assertFalse(gatewayCalled)
    }

    private fun buildSourceRows(): List<GrowwVolumeShockerSourceRow> {
        return (1..100).map { rank ->
            GrowwVolumeShockerSourceRow(
                sourceRank = rank,
                exchange = "NSE",
                symbol = "STOCK$rank",
                companyName = "Stock $rank",
                ltp = BigDecimal("100.00"),
                close = BigDecimal("95.00"),
                marketCapCrore = BigDecimal("10000.00"),
                yearLow = BigDecimal("80.00"),
                yearHigh = BigDecimal("120.00"),
                volume = 1_000_000L,
                weeklyAverageVolume = BigDecimal("100000.00"),
            )
        }
    }

    private fun fixedSource(rows: List<GrowwVolumeShockerSourceRow>): GrowwVolumeShockerSource {
        return object : GrowwVolumeShockerSource {
            override suspend fun fetchRows(): List<GrowwVolumeShockerSourceRow> = rows
        }
    }

    private fun resolver(
        resolve: suspend (exchange: String, symbol: String) -> Long?,
    ): GrowwVolumeShockerInstrumentTokenResolver {
        return object : GrowwVolumeShockerInstrumentTokenResolver {
            override suspend fun resolve(exchange: String, symbol: String): Long? {
                return resolve(exchange, symbol)
            }
        }
    }

    private fun gateway(
        replace: suspend (tradeDate: LocalDate, rows: List<GrowwVolumeShockerDailyRow>) -> Int,
    ): GrowwVolumeShockerGateway {
        return object : GrowwVolumeShockerGateway {
            override suspend fun replace(
                tradeDate: LocalDate,
                rows: List<GrowwVolumeShockerDailyRow>,
            ): Int {
                return replace(tradeDate, rows)
            }
        }
    }
}
