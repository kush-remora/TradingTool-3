package com.tradingtool.core.earnings

import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.EarningsResultJdbiHandler
import java.time.LocalDate

class JdbiEarningsResultGateway(
    private val earningsHandler: EarningsResultJdbiHandler,
) : EarningsResultGateway {
    override suspend fun upsert(stockSymbol: String, resultDate: LocalDate): Int {
        return earningsHandler.write { dao -> dao.upsert(stockSymbol, resultDate) }
    }

    override suspend fun findByResultDateRange(from: LocalDate, to: LocalDate): List<EarningsResultRow> {
        return earningsHandler.read { dao -> dao.findByResultDateRange(from, to) }
    }

    override suspend fun updateBehaviorPayload(id: Long, behaviorPayloadJson: String): Int {
        return earningsHandler.write { dao -> dao.updateBehaviorPayload(id, behaviorPayloadJson) }
    }
}

class JdbiCandleGateway(
    private val candleHandler: CandleJdbiHandler,
) : EarningsCandleGateway {
    override suspend fun findDailyCandlesBySymbol(symbol: String, from: LocalDate, to: LocalDate) =
        candleHandler.read { dao -> dao.getDailyCandlesBySymbol(symbol, from, to) }
}
