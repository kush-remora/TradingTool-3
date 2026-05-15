package com.tradingtool.core.indexconstituents

import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import java.time.OffsetDateTime

class JdbiIndexConstituentGateway(
    private val indexHandler: IndexConstituentJdbiHandler,
) : IndexConstituentGateway {
    override suspend fun upsertBatch(rows: List<IndexConstituentRow>, syncedAt: OffsetDateTime): Int {
        if (rows.isEmpty()) return 0

        val payload = rows.map { row ->
            IndexConstituentUpsertRow(
                indexKey = row.indexKey,
                symbol = row.symbol,
                instrumentToken = row.instrumentToken,
                companyName = row.companyName,
                industry = row.industry,
                series = row.series,
                isinCode = row.isinCode,
                sourceUrl = row.sourceUrl,
            )
        }

        val updates = indexHandler.write { dao ->
            dao.upsertBatch(payload, syncedAt)
        }

        return updates.sum()
    }

    override suspend fun deactivateMissing(indexKey: String, activeSymbols: Set<String>, syncedAt: OffsetDateTime): Int {
        return indexHandler.write { dao ->
            if (activeSymbols.isEmpty()) {
                dao.deactivateAllByIndex(indexKey, syncedAt)
            } else {
                dao.deactivateMissing(indexKey, activeSymbols.toList(), syncedAt)
            }
        }
    }
}
