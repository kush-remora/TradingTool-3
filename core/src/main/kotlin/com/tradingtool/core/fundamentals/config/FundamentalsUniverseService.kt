package com.tradingtool.core.fundamentals.config

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.delivery.model.DeliveryUniverse
import com.tradingtool.core.model.stock.Stock

@Singleton
class FundamentalsUniverseService @Inject constructor(
    private val configService: FundamentalsConfigService,
    private val stockHandler: StockJdbiHandler,
) {
    suspend fun resolveTargetAssignments(symbolsOverride: List<String>? = null): Map<String, DeliveryUniverse> {
        return configService.resolveConfiguredUniverseAssignments(
            watchlistSymbols = loadNseWatchlistSymbols(),
            symbolsOverride = symbolsOverride,
        )
    }

    suspend fun resolveTargetSymbols(symbolsOverride: List<String>? = null): Set<String> {
        return resolveTargetAssignments(symbolsOverride).keys
    }

    suspend fun loadNseWatchlistSymbolsForFundamentals(): List<String> {
        return loadNseWatchlistSymbols()
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
