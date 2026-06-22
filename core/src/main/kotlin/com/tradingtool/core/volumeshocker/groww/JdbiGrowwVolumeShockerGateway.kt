package com.tradingtool.core.volumeshocker.groww

import com.tradingtool.core.database.GrowwVolumeShockerJdbiHandler
import java.time.LocalDate

class JdbiGrowwVolumeShockerGateway(
    private val handler: GrowwVolumeShockerJdbiHandler,
) : GrowwVolumeShockerGateway {

    override suspend fun replace(
        tradeDate: LocalDate,
        rows: List<GrowwVolumeShockerDailyRow>,
    ): Int {
        return handler.transaction { readDao, writeDao ->
            writeDao.deleteByTradeDate(tradeDate)
            val insertedCount = writeDao.insertBatch(rows).sum()
            check(insertedCount == rows.size) {
                "Expected to insert ${rows.size} volume-shocker rows, but inserted $insertedCount."
            }

            val storedCount = readDao.countByTradeDate(tradeDate)
            check(storedCount == rows.size) {
                "Expected ${rows.size} stored volume-shocker rows, but found $storedCount."
            }
            storedCount
        }
    }
}
