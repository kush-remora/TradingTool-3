package com.tradingtool.core.kite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.zerodhatech.models.Instrument

data class InstrumentTokenResolution(
    val exchange: String,
    val symbol: String,
    val expectedKeys: List<String>,
    val resolvedToken: Long?,
    val matchedKey: String?,
    val candidateKeys: List<String>,
)

class InstrumentTokenResolverService(
    private val kiteClient: KiteConnectClient,
    private val instrumentCache: InstrumentCache,
) {
    private val refreshMutex = Mutex()

    suspend fun resolve(exchange: String, symbol: String): Long? {
        return resolveDetailed(exchange, symbol).resolvedToken
    }

    suspend fun resolveDetailed(exchange: String, symbol: String): InstrumentTokenResolution {
        ensureCacheLoaded()

        val normalizedExchange = exchange.trim().uppercase()
        val normalizedSymbol = symbol.trim().uppercase()
        val expectedKeys = buildExpectedKeys(normalizedExchange, normalizedSymbol)

        val exactMatch = instrumentCache.find(normalizedExchange, normalizedSymbol)
        if (exactMatch != null) {
            return InstrumentTokenResolution(
                exchange = normalizedExchange,
                symbol = normalizedSymbol,
                expectedKeys = expectedKeys,
                resolvedToken = exactMatch.instrument_token,
                matchedKey = "${exactMatch.exchange.uppercase()}:${exactMatch.tradingsymbol.uppercase()}",
                candidateKeys = emptyList(),
            )
        }

        if (normalizedExchange == NSE_EXCHANGE) {
            for (suffix in NSE_FALLBACK_SUFFIXES) {
                val fallbackMatch = instrumentCache.find(normalizedExchange, "$normalizedSymbol$suffix")
                if (fallbackMatch != null) {
                    return InstrumentTokenResolution(
                        exchange = normalizedExchange,
                        symbol = normalizedSymbol,
                        expectedKeys = expectedKeys,
                        resolvedToken = fallbackMatch.instrument_token,
                        matchedKey = "${fallbackMatch.exchange.uppercase()}:${fallbackMatch.tradingsymbol.uppercase()}",
                        candidateKeys = emptyList(),
                    )
                }
            }
        }

        return InstrumentTokenResolution(
            exchange = normalizedExchange,
            symbol = normalizedSymbol,
            expectedKeys = expectedKeys,
            resolvedToken = null,
            matchedKey = null,
            candidateKeys = findCandidateKeys(normalizedSymbol),
        )
    }

    private fun buildExpectedKeys(exchange: String, symbol: String): List<String> {
        if (exchange != NSE_EXCHANGE) {
            return listOf("$exchange:$symbol")
        }
        return buildList {
            add("$exchange:$symbol")
            NSE_FALLBACK_SUFFIXES.forEach { suffix ->
                add("$exchange:$symbol$suffix")
            }
        }
    }

    private fun findCandidateKeys(symbol: String): List<String> {
        val prefix = "$symbol-"
        return instrumentCache.all()
            .asSequence()
            .filter { instrument ->
                val normalizedTradingSymbol = instrument.tradingsymbol.uppercase()
                normalizedTradingSymbol == symbol || normalizedTradingSymbol.startsWith(prefix)
            }
            .sortedWith(
                compareBy<Instrument>(
                    { if (it.exchange.equals(NSE_EXCHANGE, ignoreCase = true)) 0 else 1 },
                    { it.exchange.uppercase() },
                    { it.tradingsymbol.uppercase() },
                ),
            )
            .map { instrument -> "${instrument.exchange.uppercase()}:${instrument.tradingsymbol.uppercase()}" }
            .distinct()
            .take(MAX_CANDIDATES)
            .toList()
    }

    private suspend fun ensureCacheLoaded() {
        if (!instrumentCache.isEmpty()) return

        refreshMutex.withLock {
            if (!instrumentCache.isEmpty()) return

            val instruments = withContext(Dispatchers.IO) {
                kiteClient.client().getInstruments()
            }
            instrumentCache.refresh(instruments)
        }
    }

    private companion object {
        const val NSE_EXCHANGE: String = "NSE"
        const val MAX_CANDIDATES: Int = 8
        val NSE_FALLBACK_SUFFIXES: List<String> = listOf("-BE", "-IV")
    }
}
