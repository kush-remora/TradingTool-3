package com.tradingtool.core.watchlist.groww

import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

class JdbiGrowwWatchlistStockGateway(
    private val indexHandler: IndexConstituentJdbiHandler,
    private val instrumentTokenResolver: GrowwWatchlistInstrumentTokenResolver? = null,
) : GrowwWatchlistStockGateway {
    private val log = LoggerFactory.getLogger(JdbiGrowwWatchlistStockGateway::class.java)

    override suspend fun upsertGrowwStock(stock: GrowwWatchlistStock, indexKey: String): Int {
        val resolvedBySymbol = instrumentTokenResolver?.resolve(stock.exchange, stock.symbol)
        val resolvedToken = when {
            resolvedBySymbol != null && resolvedBySymbol > 0L -> resolvedBySymbol
            stock.instrumentToken > 0L -> stock.instrumentToken
            else -> null
        }
        if (resolvedToken == null) {
            log.warn("Skipping watchlist upsert for {} due to missing instrument token", stock.symbol)
            return 0
        }

        val syncedAt = OffsetDateTime.now()
        val payload = listOf(
            IndexConstituentUpsertRow(
                indexKey = indexKey,
                symbol = stock.symbol,
                instrumentToken = resolvedToken,
                companyName = stock.companyName,
                industry = DUMMY_VALUE,
                series = DUMMY_VALUE,
                isinCode = DUMMY_VALUE,
                sourceUrl = DUMMY_SOURCE_URL,
            )
        )

        return indexHandler.write { dao ->
            dao.upsertBatch(payload, syncedAt).sum()
        }
    }

    companion object {
        private const val DUMMY_VALUE = ""
        private const val DUMMY_SOURCE_URL = "groww://watchlist"
    }
}
