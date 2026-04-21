package com.tradingtool.core.watchlist.groww

import com.fasterxml.jackson.databind.ObjectMapper
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.model.stock.StockTag
import org.slf4j.LoggerFactory

class JdbiGrowwWatchlistStockGateway(
    private val stockHandler: StockJdbiHandler,
    private val objectMapper: ObjectMapper,
    private val instrumentTokenResolver: GrowwWatchlistInstrumentTokenResolver? = null,
) : GrowwWatchlistStockGateway {
    private val log = LoggerFactory.getLogger(JdbiGrowwWatchlistStockGateway::class.java)

    override suspend fun upsertGrowwStock(stock: GrowwWatchlistStock): Int {
        val existing = stockHandler.read { dao ->
            dao.getBySymbol(stock.symbol, stock.exchange)
        }
        val resolvedToken = when {
            stock.instrumentToken > 0L -> stock.instrumentToken
            existing?.instrumentToken != null && existing.instrumentToken > 0L -> existing.instrumentToken
            instrumentTokenResolver != null -> instrumentTokenResolver.resolve(stock.exchange, stock.symbol)
            else -> null
        }
        if (resolvedToken == null) {
            log.warn("Skipping watchlist upsert for {} due to missing instrument token", stock.symbol)
            return 0
        }

        return stockHandler.write { dao ->
            dao.upsertFromGrowwWatchlist(
                symbol = stock.symbol,
                instrumentToken = resolvedToken,
                companyName = stock.companyName,
                exchange = stock.exchange,
                growwTagName = GROWW_TAG_NAME,
                growwTagJson = objectMapper.writeValueAsString(listOf(StockTag(name = GROWW_TAG_NAME, color = GROWW_TAG_COLOR))),
            )
        }
    }

    companion object {
        private const val GROWW_TAG_NAME = "GROWW"
        private const val GROWW_TAG_COLOR = "#0ea5e9"
    }
}
