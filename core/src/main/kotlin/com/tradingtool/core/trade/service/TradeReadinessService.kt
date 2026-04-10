package com.tradingtool.core.trade.service

import com.google.inject.Inject
import com.tradingtool.core.candle.IntradayCandle
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.model.trade.TradeReadinessAlert
import com.tradingtool.core.model.trade.TradeReadinessResponse
import com.tradingtool.core.model.trade.TradeReadinessSymbol
import com.tradingtool.core.telegram.TelegramApiClient
import com.tradingtool.core.technical.calculateRsi
import com.tradingtool.core.technical.getNullableDouble
import com.tradingtool.core.technical.roundTo2
import com.tradingtool.core.technical.toTa4jSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.ta4j.core.BaseBarSeriesBuilder
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class TradeReadinessService @Inject constructor(
    private val stockHandler: StockJdbiHandler,
    private val candleHandler: CandleJdbiHandler,
    private val telegramApiClient: TelegramApiClient,
) {
    private val ist: ZoneId = ZoneId.of("Asia/Kolkata")

    suspend fun getReadiness(symbols: List<String>): TradeReadinessResponse = coroutineScope {
        if (symbols.isEmpty()) return@coroutineScope TradeReadinessResponse(symbols = emptyList())

        val normalizedSymbols = symbols
            .map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(3)

        if (normalizedSymbols.isEmpty()) return@coroutineScope TradeReadinessResponse(symbols = emptyList())

        val stocks = stockHandler.read { dao ->
            dao.listBySymbols(normalizedSymbols, "NSE")
        }.associateBy { it.symbol.uppercase(Locale.ROOT) }

        val alertsDeferred = async {
            fetchParsedAlerts(normalizedSymbols)
        }

        val readinessDeferred = normalizedSymbols.map { symbol ->
            async {
                val stock = stocks[symbol] ?: return@async null
                val rsi14 = loadDailyRsi(stock.instrumentToken)
                val rsi15m = loadIntradayRsi15m(stock.instrumentToken)

                TradeReadinessSymbol(
                    symbol = symbol,
                    companyName = stock.companyName,
                    rsi14 = rsi14,
                    rsi15m = rsi15m,
                    alerts = emptyList(),
                )
            }
        }

        val alertsBySymbol = alertsDeferred.await()

        val rows = readinessDeferred.mapNotNull { it.await() }.map { row ->
            row.copy(alerts = alertsBySymbol[row.symbol] ?: emptyList())
        }

        TradeReadinessResponse(symbols = rows)
    }

    private suspend fun loadDailyRsi(instrumentToken: Long): Double? = withContext(Dispatchers.IO) {
        val candles = candleHandler.read { dao ->
            dao.getRecentDailyCandles(instrumentToken, 80)
        }.sortedBy { it.candleDate }

        if (candles.size < 14) return@withContext null

        val series = candles.toTa4jSeries(name = "daily-rsi-$instrumentToken")
        val indicator = series.calculateRsi(14)
        indicator.getNullableDouble(series.endIndex)?.roundTo2()
    }

    private suspend fun loadIntradayRsi15m(instrumentToken: Long): Double? = withContext(Dispatchers.IO) {
        val to = LocalDateTime.now(ist)
        val from = to.minusDays(10)

        val candles = candleHandler.read { dao ->
            dao.getIntradayCandles(
                token = instrumentToken,
                interval = "15minute",
                from = from,
                to = to,
            )
        }

        if (candles.size < 14) return@withContext null

        val series = intradayToSeries(candles, "15m-rsi-$instrumentToken")
        val indicator = series.calculateRsi(14)
        indicator.getNullableDouble(series.endIndex)?.roundTo2()
    }

    private fun intradayToSeries(candles: List<IntradayCandle>, name: String) =
        BaseBarSeriesBuilder().withName(name).build().apply {
            candles.sortedBy { it.candleTimestamp }.forEach { candle ->
                addBar(
                    candle.candleTimestamp.atZone(ist),
                    candle.open,
                    candle.high,
                    candle.low,
                    candle.close,
                    candle.volume,
                )
            }
        }

    private suspend fun fetchParsedAlerts(symbols: List<String>): Map<String, List<TradeReadinessAlert>> {
        val incomingMessages = telegramApiClient.getRecentMessages(limit = 50).getOrNull() ?: emptyList()
        if (incomingMessages.isEmpty()) return emptyMap()

        val normalizedSymbols = symbols.map { it.uppercase(Locale.ROOT) }.toSet()

        return incomingMessages.mapNotNull { incoming ->
            val parsed = parseAlert(incoming.text)
            val matchedSymbol = normalizedSymbols.firstOrNull { symbol ->
                incoming.text.uppercase(Locale.ROOT).contains(symbol)
            } ?: return@mapNotNull null

            matchedSymbol to TradeReadinessAlert(
                rawText = incoming.text,
                action = parsed?.action,
                limitPrice = parsed?.limitPrice,
                targetPrice = parsed?.targetPrice,
                receivedAt = incoming.receivedAt,
            )
        }.groupBy(
            keySelector = { it.first },
            valueTransform = { it.second },
        ).mapValues { (_, alerts) -> alerts.take(5) }
    }

    private fun parseAlert(text: String): ParsedAlert? {
        val action = Regex("\\b(BUY|SELL)\\b", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.uppercase(Locale.ROOT)

        val limitPrice = Regex("\\b(?:LIMIT|ENTRY|AT)\\s*[:=-]?\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()

        val targetPrice = Regex("\\b(?:TARGET|TGT|TP)\\s*[:=-]?\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()

        if (action == null && limitPrice == null && targetPrice == null) return null

        return ParsedAlert(
            action = action,
            limitPrice = limitPrice,
            targetPrice = targetPrice,
        )
    }

    private data class ParsedAlert(
        val action: String?,
        val limitPrice: Double?,
        val targetPrice: Double?,
    )
}
