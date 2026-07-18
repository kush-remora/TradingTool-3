package com.tradingtool.core.strategy.chartinkevidence

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.time.LocalDate

class ChartinkEvidenceServiceTest {

    @Test
    fun `upload deduplicates rows and replaces one accumulation universe`() = runBlocking {
        val store = FakeEvidenceStore()
        val service = ChartinkEvidenceService(store, memberships())

        val result = service.upload(
            request(
                slot = "ACCUMULATION_NIFTY_100",
                csv = csv(
                    "2026-07-17,INFY,Largecap,IT",
                    "2026-07-17,INFY,Largecap,IT",
                    "2026-07-16,TCS,Largecap,IT",
                ),
            ),
        )

        assertEquals(2, result.storedCount)
        assertEquals(1, result.duplicateCount)
        assertEquals(0, result.skippedOutsideUniverseCount)
        assertEquals(setOf("INFY", "TCS"), store.events.map(ChartinkScanEvent::symbol).toSet())
    }

    @Test
    fun `cash upload skips symbols outside managed universes`() = runBlocking {
        val store = FakeEvidenceStore()
        val service = ChartinkEvidenceService(store, memberships())

        val result = service.upload(
            request(
                slot = "PHASE_D",
                csv = csv(
                    "2026-07-17,INFY,Largecap,IT",
                    "2026-07-17,OUTSIDE,Unknown,Other",
                ),
            ),
        )

        assertEquals(1, result.storedCount)
        assertEquals(1, result.skippedOutsideUniverseCount)
        assertEquals("OUTSIDE", result.skippedSymbols.single().symbol)
        assertEquals(ChartinkEvidenceSkipReason.NO_ACTIVE_BASE_UNIVERSE_MEMBERSHIP, result.skippedSymbols.single().reason)
        assertEquals(listOf("INFY"), store.events.map(ChartinkScanEvent::symbol))
    }

    @Test
    fun `accumulation upload reports symbols now in another universe`() = runBlocking {
        val store = FakeEvidenceStore()
        val service = ChartinkEvidenceService(store, memberships())

        val result = service.upload(
            request(
                slot = "ACCUMULATION_NIFTY_MIDCAP_150",
                csv = csv("2026-07-17,INFY,Largecap,IT"),
            ),
        )

        assertEquals(0, result.storedCount)
        assertEquals(ChartinkEvidenceSkipReason.ACTIVE_IN_DIFFERENT_UNIVERSE, result.skippedSymbols.single().reason)
        assertEquals("nifty_100", result.skippedSymbols.single().resolvedUniverseKey)
    }

    @Test
    fun `upload accepts Chartink day-month-year dates`() = runBlocking {
        val store = FakeEvidenceStore()
        val service = ChartinkEvidenceService(store, memberships())

        service.upload(
            request(
                slot = "ACCUMULATION_NIFTY_100",
                csv = csv("26-11-2025,KOTAKBANK,Largecap,Bank"),
            ),
        )

        assertEquals(LocalDate.of(2025, 11, 26), store.events.single().eventDate)
    }

    @Test
    fun `invalid headers leave stored events unchanged`() = runBlocking {
        val store = FakeEvidenceStore()
        store.events += event("INFY", ChartinkEvidenceSource.PHASE_D, "2026-07-01")
        val service = ChartinkEvidenceService(store, memberships())

        assertFailsWith<IllegalArgumentException> {
            service.upload(request("PHASE_D", "Date,Symbol\n2026-07-17,INFY"))
        }

        assertEquals(1, store.events.size)
    }

    @Test
    fun `dashboard filters source dates and sorts curated watchlists first`() = runBlocking {
        val store = FakeEvidenceStore().apply {
            events += event("TCS", ChartinkEvidenceSource.PHASE_D, "2026-07-17")
            events += event("INFY", ChartinkEvidenceSource.ACCUMULATION, "2026-07-10")
            events += event("INFY", ChartinkEvidenceSource.T2_HIGH, "2026-05-10")
        }
        val service = ChartinkEvidenceService(store, memberships())

        val dashboard = service.getDashboard(months = 1, asOfDate = LocalDate.parse("2026-07-18"))

        assertEquals(listOf("INFY", "TCS"), dashboard.rows.map(ChartinkEvidenceDashboardRow::symbol))
        assertEquals("2026-07-10", dashboard.rows.first().accumulationLatestDate.toString())
        assertEquals(null, dashboard.rows.first().t2HighLatestDate)
        assertTrue(dashboard.rows.first().curatedWatchlists.contains("growth_watchlist"))
    }

    @Test
    fun `dashboard reports the latest upload for each fixed slot`() = runBlocking {
        val store = FakeEvidenceStore().apply {
            uploads += StoredChartinkEvidenceUpload(
                ChartinkEvidenceSource.ACCUMULATION,
                "nifty_midcap_150",
                "midcap.csv",
                "2026-07-18T12:00:00Z",
            )
        }
        val service = ChartinkEvidenceService(store, memberships())

        val dashboard = service.getDashboard(months = 1, asOfDate = LocalDate.parse("2026-07-18"))

        assertEquals("ACCUMULATION_NIFTY_MIDCAP_150", dashboard.uploadStatuses.single().slot)
        assertEquals("midcap.csv", dashboard.uploadStatuses.single().sourceFileName)
    }

    private fun memberships(): ChartinkUniverseMembershipStore = object : ChartinkUniverseMembershipStore {
        override suspend fun findActiveMemberships(symbols: List<String>): List<IndexMembership> = listOf(
            IndexMembership("INFY", "nifty_100"),
            IndexMembership("INFY", "growth_watchlist"),
            IndexMembership("KOTAKBANK", "nifty_100"),
            IndexMembership("TCS", "nifty_100"),
        ).filter { membership -> membership.symbol in symbols }
    }

    private fun request(slot: String, csv: String): ChartinkEvidenceUploadRequest =
        ChartinkEvidenceUploadRequest(slot = slot, csvContent = csv, fileName = "scanner.csv")

    private fun csv(vararg rows: String): String =
        "Date,Symbol,Marketcapname,Sector\n${rows.joinToString("\n")}" 

    private fun event(symbol: String, source: ChartinkEvidenceSource, date: String): ChartinkScanEvent =
        ChartinkScanEvent(source, "nifty_100", LocalDate.parse(date), symbol, "Largecap", "IT", "scanner.csv")

    private class FakeEvidenceStore : ChartinkEvidenceStore {
        val events = mutableListOf<ChartinkScanEvent>()
        val uploads = mutableListOf<StoredChartinkEvidenceUpload>()

        override suspend fun replaceAccumulation(universeKey: String, events: List<ChartinkScanEvent>) {
            this.events.removeIf { event -> event.source == ChartinkEvidenceSource.ACCUMULATION && event.universeKey == universeKey }
            this.events += events
        }

        override suspend fun replaceCashSource(source: ChartinkEvidenceSource, events: List<ChartinkScanEvent>) {
            this.events.removeIf { event -> event.source == source }
            this.events += events
        }

        override suspend fun findFromDate(fromDate: LocalDate): List<ChartinkScanEvent> =
            events.filter { event -> event.eventDate >= fromDate }

        override suspend fun findLatestUploads(): List<StoredChartinkEvidenceUpload> = uploads
    }
}
