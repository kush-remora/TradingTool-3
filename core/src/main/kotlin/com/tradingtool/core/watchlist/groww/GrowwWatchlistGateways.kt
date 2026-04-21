package com.tradingtool.core.watchlist.groww

import com.fasterxml.jackson.databind.ObjectMapper
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.model.stock.StockTag

class JdbiGrowwWatchlistStockGateway(
    private val stockHandler: StockJdbiHandler,
    private val objectMapper: ObjectMapper,
) : GrowwWatchlistStockGateway {

    override suspend fun upsertGrowwStock(stock: GrowwWatchlistStock): Int {
        return stockHandler.write { dao ->
            dao.upsertFromGrowwWatchlist(
                symbol = stock.symbol,
                instrumentToken = stock.instrumentToken,
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
