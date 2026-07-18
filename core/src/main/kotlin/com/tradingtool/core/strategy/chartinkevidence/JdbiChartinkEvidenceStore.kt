package com.tradingtool.core.strategy.chartinkevidence

import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentReadDao
import com.tradingtool.core.strategy.chartinkevidence.dao.ChartinkEvidenceReadDao
import com.tradingtool.core.strategy.chartinkevidence.dao.ChartinkEvidenceWriteDao
import java.time.LocalDate

typealias ChartinkEvidenceJdbiHandler = JdbiHandler<ChartinkEvidenceReadDao, ChartinkEvidenceWriteDao>

class JdbiChartinkEvidenceStore(
    private val handler: ChartinkEvidenceJdbiHandler,
) : ChartinkEvidenceStore {
    override suspend fun replaceAccumulation(universeKey: String, events: List<ChartinkScanEvent>) {
        handler.transaction { _, writeDao ->
            writeDao.deleteAccumulation(ChartinkEvidenceSource.ACCUMULATION, universeKey)
            insertEvents(writeDao, events)
        }
    }

    override suspend fun replaceCashSource(source: ChartinkEvidenceSource, events: List<ChartinkScanEvent>) {
        handler.transaction { _, writeDao ->
            writeDao.deleteCashSource(source)
            insertEvents(writeDao, events)
        }
    }

    override suspend fun findFromDate(fromDate: LocalDate): List<ChartinkScanEvent> =
        handler.read { readDao -> readDao.findFromDate(fromDate) }

    private fun insertEvents(writeDao: ChartinkEvidenceWriteDao, events: List<ChartinkScanEvent>) {
        if (events.isEmpty()) {
            return
        }
        val insertedCount = writeDao.insertBatch(events).sum()
        check(insertedCount == events.size) {
            "Expected ${events.size} Chartink events to be stored, but stored $insertedCount."
        }
    }
}

class JdbiChartinkUniverseMembershipStore(
    private val handler: com.tradingtool.core.database.IndexConstituentJdbiHandler,
) : ChartinkUniverseMembershipStore {
    override suspend fun findActiveMemberships(symbols: List<String>): List<IndexMembership> {
        if (symbols.isEmpty()) {
            return emptyList()
        }
        return handler.read { readDao: IndexConstituentReadDao ->
            readDao.findActiveMembershipsBySymbols(symbols).map { row ->
                IndexMembership(symbol = row.symbol, indexKey = row.indexKey)
            }
        }
    }
}
