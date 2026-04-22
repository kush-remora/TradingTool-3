package com.tradingtool.core.earnings

import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.EarningsResultJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import java.time.LocalDate

class JdbiEarningsResultGateway(
    private val earningsHandler: EarningsResultJdbiHandler,
) : EarningsResultGateway {
    override suspend fun upsert(stockSymbol: String, instrumentToken: Long, resultDate: LocalDate): Int {
        return earningsHandler.write { dao -> dao.upsert(stockSymbol, instrumentToken, resultDate) }
    }

    override suspend fun findByResultDateRange(from: LocalDate, to: LocalDate): List<EarningsResultRow> {
        return earningsHandler.read { dao -> dao.findByResultDateRange(from, to) }
    }

    override suspend fun backfillInstrumentTokenFromStocks(): Int {
        return earningsHandler.write { dao -> dao.backfillInstrumentTokenFromStocks() }
    }

    override suspend fun findRowsMissingInstrumentToken(limit: Int): List<EarningsMissingTokenRow> {
        return earningsHandler.read { dao -> dao.findRowsMissingInstrumentToken(limit) }
    }

    override suspend fun updateInstrumentToken(id: Long, instrumentToken: Long): Int {
        return earningsHandler.write { dao -> dao.updateInstrumentToken(id, instrumentToken) }
    }

    override suspend fun countRowsMissingInstrumentToken(): Int {
        return earningsHandler.read { dao -> dao.countRowsMissingInstrumentToken() }
    }

    override suspend fun enforceInstrumentTokenNotNull(): Boolean {
        return runCatching {
            earningsHandler.write { dao -> dao.enforceInstrumentTokenNotNull() }
            true
        }.getOrDefault(false)
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

class JdbiEarningsStockTokenLookup(
    private val stockHandler: StockJdbiHandler,
) : EarningsStockTokenLookup {
    override suspend fun findInstrumentTokenBySymbol(symbol: String): Long? {
        return stockHandler.read { dao -> dao.getBySymbol(symbol.uppercase(), "NSE")?.instrumentToken }
    }
}
