package com.tradingtool.core.delivery.config

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.model.stock.Stock

@Singleton
class DeliveryUniverseService @Inject constructor(
    private val configService: DeliveryConfigService,
    private val stockHandler: StockJdbiHandler,
) {
    suspend fun resolveTargetSymbols(): Set<String> {
        return configService.resolveConfiguredUniverseSymbols(loadNseWatchlistSymbols())
    }

    suspend fun loadNseWatchlistSymbolsForDelivery(): List<String> {
        return loadNseWatchlistSymbols()
    }

    suspend fun resolveKnownNseTargetStocks(): List<Stock> {
        val targetSymbols = resolveTargetSymbols().toList()
        if (targetSymbols.isEmpty()) {
            return emptyList()
        }

        return stockHandler.read { dao ->
            dao.listBySymbols(targetSymbols, NSE_EXCHANGE)
        }
    }

    internal fun extractNseWatchlistSymbols(stocks: List<Stock>): List<String> {
        return stocks.asSequence()
            .filter { stock -> stock.exchange.equals(NSE_EXCHANGE, ignoreCase = true) }
            .map { stock -> stock.symbol }
            .toList()
    }

    private suspend fun loadNseWatchlistSymbols(): List<String> {
        val stocks = stockHandler.read { dao -> dao.listAll() }
        return extractNseWatchlistSymbols(stocks)
    }

    private companion object {
        const val NSE_EXCHANGE: String = "NSE"
    }
}
