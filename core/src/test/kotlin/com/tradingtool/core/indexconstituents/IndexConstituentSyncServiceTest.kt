package com.tradingtool.core.indexconstituents

import com.tradingtool.core.kite.InstrumentTokenResolution
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IndexConstituentSyncServiceTest {

    @Test
    fun `sync normalizes dedupes batches and deactivates missing`() = runBlocking {
        val source = FakeSource(
            mapOf(
                "nifty_50" to listOf(
                    IndexConstituentCsvRow(" tcs ", "Tata", "IT", "EQ", "ISIN1"),
                    IndexConstituentCsvRow("TCS", "Tata", "IT", "EQ", "ISIN1"),
                    IndexConstituentCsvRow("infy", "Infosys", "IT", "EQ", "ISIN2"),
                    IndexConstituentCsvRow("", "", "", "", ""),
                ),
            )
        )
        val resolver = FakeResolver(
            mapOf(
                "TCS" to 123L,
                "INFY" to 456L,
            )
        )
        val gateway = FakeGateway()

        val service = IndexConstituentSyncService(source, resolver, gateway)
        val report = service.sync(
            IndexSyncConfig(
                batchSize = 1,
                indices = listOf(IndexDefinition("nifty_50", true, "https://example.com/nifty50.csv")),
            ),
        )

        assertEquals(1, report.indexReports.size)
        val row = report.indexReports.first()
        assertEquals(4, row.fetchedCount)
        assertEquals(2, row.parsedCount)
        assertEquals(2, row.upsertedCount)
        assertEquals(0, row.deactivatedCount)
        assertEquals(emptyList(), row.unresolvedSymbols)

        assertEquals(listOf(1, 1), gateway.batchSizes)
        assertEquals(setOf("TCS", "INFY"), gateway.lastActiveSymbols)
    }

    @Test
    fun `sync fails fast when unresolved symbols are present`() = runBlocking {
        val source = FakeSource(
            mapOf(
                "nifty_50" to listOf(
                    IndexConstituentCsvRow("TCS", "Tata", "IT", "EQ", "ISIN1"),
                    IndexConstituentCsvRow("UNKNOWN", "Unknown Ltd", "NA", "EQ", "ISINX"),
                )
            )
        )
        val resolver = FakeResolver(mapOf("TCS" to 123L))
        val gateway = FakeGateway()

        val service = IndexConstituentSyncService(source, resolver, gateway)
        assertFailsWith<IllegalStateException> {
            service.sync(
                IndexSyncConfig(
                    batchSize = 50,
                    indices = listOf(IndexDefinition("nifty_50", true, "https://example.com/nifty50.csv")),
                ),
            )
        }
        assertEquals(emptyList(), gateway.batchSizes)
    }

    private class FakeSource(
        private val data: Map<String, List<IndexConstituentCsvRow>>,
    ) : IndexConstituentSource {
        override suspend fun fetchRows(index: IndexDefinition): List<IndexConstituentCsvRow> = data[index.key].orEmpty()
    }

    private class FakeResolver(
        private val tokenBySymbol: Map<String, Long>,
    ) : IndexConstituentTokenResolver {
        override suspend fun resolveDetailed(exchange: String, symbol: String): InstrumentTokenResolution {
            val resolvedToken = tokenBySymbol[symbol]
            return InstrumentTokenResolution(
                exchange = exchange,
                symbol = symbol,
                expectedKeys = listOf("$exchange:$symbol"),
                resolvedToken = resolvedToken,
                matchedKey = resolvedToken?.let { "$exchange:$symbol" },
                candidateKeys = emptyList(),
            )
        }
    }

    private class FakeGateway : IndexConstituentGateway {
        val batchSizes = mutableListOf<Int>()
        var lastActiveSymbols: Set<String> = emptySet()

        override suspend fun upsertBatch(rows: List<IndexConstituentRow>, syncedAt: OffsetDateTime): Int {
            batchSizes += rows.size
            return rows.size
        }

        override suspend fun deactivateMissing(indexKey: String, activeSymbols: Set<String>, syncedAt: OffsetDateTime): Int {
            lastActiveSymbols = activeSymbols
            return 0
        }
    }
}
