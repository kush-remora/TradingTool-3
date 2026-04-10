package com.tradingtool.core.strategy.rsimomentum

import com.tradingtool.core.model.stock.Stock

object RsiMomentumUniverseBuilder {

    fun build(
        baseSymbols: List<String>,
        watchlistStocks: List<Stock>,
        tokenLookup: (String) -> Long?,
        companyNameLookup: (String) -> String?,
    ): UniverseBuildResult {
        val entries = linkedMapOf<String, MutableUniverseEntry>()

        baseSymbols.forEach { rawSymbol ->
            val symbol = rawSymbol.trim().uppercase()
            if (symbol.isEmpty()) return@forEach
            val entry = entries.getOrPut(symbol) { MutableUniverseEntry(symbol = symbol) }
            entry.inBaseUniverse = true
        }

        watchlistStocks
            .filter { stock -> stock.exchange.equals("NSE", ignoreCase = true) }
            .forEach { stock ->
                val symbol = stock.symbol.trim().uppercase()
                if (symbol.isEmpty()) return@forEach

                val entry = entries.getOrPut(symbol) { MutableUniverseEntry(symbol = symbol) }
                entry.inWatchlist = true
                if (stock.instrumentToken > 0) {
                    entry.instrumentToken = stock.instrumentToken
                }
                if (entry.companyName.isBlank()) {
                    entry.companyName = stock.companyName
                }
            }

        val unresolvedSymbols = mutableListOf<String>()
        val members = mutableListOf<UniverseMember>()

        entries.values.forEach { entry ->
            val instrumentToken = entry.instrumentToken ?: tokenLookup(entry.symbol)
            if (instrumentToken == null || instrumentToken <= 0) {
                unresolvedSymbols += entry.symbol
                return@forEach
            }

            val companyName = entry.companyName.takeIf { it.isNotBlank() }
                ?: companyNameLookup(entry.symbol)
                ?: entry.symbol

            members += UniverseMember(
                symbol = entry.symbol,
                instrumentToken = instrumentToken,
                companyName = companyName,
                inBaseUniverse = entry.inBaseUniverse,
                inWatchlist = entry.inWatchlist,
            )
        }

        val watchlistSymbolSet = watchlistStocks
            .filter { stock -> stock.exchange.equals("NSE", ignoreCase = true) }
            .map { stock -> stock.symbol.trim().uppercase() }
            .filter { symbol -> symbol.isNotEmpty() }
            .toSet()

        return UniverseBuildResult(
            members = members,
            unresolvedSymbols = unresolvedSymbols.sorted(),
            baseUniverseCount = baseSymbols.map { symbol -> symbol.trim().uppercase() }
                .filter { symbol -> symbol.isNotEmpty() }
                .distinct()
                .size,
            watchlistCount = watchlistSymbolSet.size,
            watchlistAdditionsCount = members.count { member -> member.inWatchlist && !member.inBaseUniverse },
        )
    }

    private data class MutableUniverseEntry(
        val symbol: String,
        var instrumentToken: Long? = null,
        var companyName: String = "",
        var inBaseUniverse: Boolean = false,
        var inWatchlist: Boolean = false,
    )
}

