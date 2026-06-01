package com.tradingtool.core.strategy.hotsma

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import com.tradingtool.core.strategy.wyckoff.deliverythreshold.normalizeIndexKeyInCore
import com.tradingtool.core.technical.calculateRsiValues
import com.tradingtool.core.technical.roundTo2
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Singleton
class HotSmaScannerService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient,
) {
    suspend fun listUniverseOptions(): List<HotSmaUniverseOption> {
        return indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
            .map { summary -> HotSmaUniverseOption(value = summary.indexKey, count = summary.count) }
    }

    suspend fun run(config: HotSmaRunConfig): HotSmaRunResponse {
        val indexMembers = resolveIndexMembers(config.indexKey)
        if (indexMembers.members.isEmpty()) {
            return HotSmaRunResponse(
                selectedIndexKey = indexMembers.selectedIndexKey,
                config = configSnapshot(),
                summary = HotSmaSummary(totalSignals = 0, aggressiveCount = 0, standardCount = 0, watchCount = 0),
                rows = emptyList(),
            )
        }

        val rows = mutableListOf<HotSmaRow>()
        for (member in indexMembers.members) {
            val symbol = member.symbol.trim().uppercase()
            if (symbol.isEmpty()) continue
            if (member.instrumentToken <= 0) continue

            val candles = loadDailyCandles(
                symbol = symbol,
                instrumentToken = member.instrumentToken,
                fromDate = LocalDate.now().minusDays(HISTORY_DAYS),
                toDate = LocalDate.now(),
            )
            val row = evaluateSymbol(member = member, selectedIndexKey = indexMembers.selectedIndexKey, candles = candles)
            if (row != null) {
                rows += row
            }
        }

        val aggressiveCount = rows.count { row -> row.signalTag == HotSmaSignalTag.AGGRESSIVE_BUY }
        val standardCount = rows.count { row -> row.signalTag == HotSmaSignalTag.STANDARD_BUY }
        val watchCount = rows.count { row -> row.signalTag == HotSmaSignalTag.WATCH_ZONE }

        return HotSmaRunResponse(
            selectedIndexKey = indexMembers.selectedIndexKey,
            config = configSnapshot(),
            summary = HotSmaSummary(
                totalSignals = rows.size,
                aggressiveCount = aggressiveCount,
                standardCount = standardCount,
                watchCount = watchCount,
            ),
            rows = rows.sortedBy { row -> row.symbol },
        )
    }

    private suspend fun resolveIndexMembers(indexKey: String): ResolvedIndexMembers {
        val requested = indexKey.trim()
        if (requested.isEmpty()) {
            return ResolvedIndexMembers(selectedIndexKey = indexKey, members = emptyList())
        }

        val allIndices = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
        val normalizedRequested = normalizeIndexKeyInCore(requested)
        val matchingKeys = allIndices
            .map { summary -> summary.indexKey }
            .filter { existing -> normalizeIndexKeyInCore(existing) == normalizedRequested }

        val selectedKey = matchingKeys.firstOrNull() ?: requested
        val members = if (matchingKeys.isEmpty()) {
            indexConstituentHandler.read { dao -> dao.listActiveByIndex(selectedKey) }
        } else {
            matchingKeys.flatMap { key -> indexConstituentHandler.read { dao -> dao.listActiveByIndex(key) } }
        }

        val dedupedMembers = members
            .groupBy { row -> row.symbol.trim().uppercase() }
            .mapNotNull { entry -> entry.value.firstOrNull() }

        return ResolvedIndexMembers(
            selectedIndexKey = selectedKey,
            members = dedupedMembers,
        )
    }

    private suspend fun loadDailyCandles(
        symbol: String,
        instrumentToken: Long,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<DailyCandle> {
        var candles = candleCacheService.getDailyCandles(
            token = instrumentToken,
            symbol = symbol,
            from = fromDate,
            to = toDate,
        ).sortedBy { candle -> candle.candleDate }

        val latestDate = candles.lastOrNull()?.candleDate
        val gapDays = latestDate?.let { date -> ChronoUnit.DAYS.between(date, toDate) } ?: Long.MAX_VALUE
        val shouldBackfill = candles.isEmpty() || gapDays > MAX_ALLOWED_LATEST_GAP_DAYS

        if (shouldBackfill) {
            runCatching {
                candleDataService.syncDailyRange(
                    symbols = listOf(symbol),
                    fromDate = fromDate,
                    toDate = toDate,
                    kiteClient = kiteClient,
                )
            }
            candles = candleCacheService.getDailyCandles(
                token = instrumentToken,
                symbol = symbol,
                from = fromDate,
                to = toDate,
            ).sortedBy { candle -> candle.candleDate }
        }

        return candles
    }

    private fun evaluateSymbol(
        member: IndexConstituentUpsertRow,
        selectedIndexKey: String,
        candles: List<DailyCandle>,
    ): HotSmaRow? {
        if (candles.isEmpty()) return null

        val sma50Values = calculateRollingSmaValues(candles, window = SMA50_WINDOW)
        val sma100Values = calculateRollingSmaValues(candles, window = SMA100_WINDOW)
        val sma200Values = calculateRollingSmaValues(candles, window = SMA200_WINDOW)
        val rsiValues = candles.calculateRsiValues(period = RSI_PERIOD, fallback = RSI_FALLBACK)

        val lastIndex = candles.lastIndex
        val latest = candles[lastIndex]

        val sma50 = sma50Values.getOrNull(lastIndex)
        val sma100 = sma100Values.getOrNull(lastIndex)
        val sma200 = sma200Values.getOrNull(lastIndex)
        val rsi14 = rsiValues.getOrNull(lastIndex)

        val sma100TouchDate = detectLatestLowTouch(
            candles = candles,
            smaValues = sma100Values,
            lookbackDays = TOUCH_LOOKBACK_DAYS,
        )
        val sma200TouchDate = detectLatestLowTouch(
            candles = candles,
            smaValues = sma200Values,
            lookbackDays = TOUCH_LOOKBACK_DAYS,
        )

        val pctToSma50 = pctDistance(currentPrice = latest.close, sma = sma50)
        val pctToSma100 = pctDistance(currentPrice = latest.close, sma = sma100)
        val pctToSma200 = pctDistance(currentPrice = latest.close, sma = sma200)

        val signalTag = resolveHotSmaSignalTag(
            sma100TouchDate = sma100TouchDate,
            sma200TouchDate = sma200TouchDate,
            pctToSma200 = pctToSma200,
        ) ?: return null

        return HotSmaRow(
            symbol = member.symbol.trim().uppercase(),
            companyName = member.companyName,
            indexKey = selectedIndexKey,
            instrumentToken = member.instrumentToken,
            latestDate = latest.candleDate.toString(),
            currentPrice = latest.close.roundTo2(),
            sma50 = sma50?.roundTo2(),
            sma100 = sma100?.roundTo2(),
            sma200 = sma200?.roundTo2(),
            pctToSma50 = pctToSma50?.roundTo2(),
            pctToSma100 = pctToSma100?.roundTo2(),
            pctToSma200 = pctToSma200?.roundTo2(),
            rsi14 = rsi14?.roundTo2(),
            sma100TouchedInLast5d = sma100TouchDate != null,
            sma100TouchDate = sma100TouchDate,
            sma200TouchedInLast5d = sma200TouchDate != null,
            sma200TouchDate = sma200TouchDate,
            signalTag = signalTag,
        )
    }

    private fun pctDistance(currentPrice: Double, sma: Double?): Double? {
        if (sma == null || sma <= 0.0) return null
        return ((currentPrice / sma) - 1.0) * 100.0
    }

    private fun configSnapshot(): HotSmaConfigSnapshot {
        return HotSmaConfigSnapshot(
            touchLookbackDays = TOUCH_LOOKBACK_DAYS,
            watchZoneUpperPct = WATCH_ZONE_UPPER_PCT,
            rsiPeriod = RSI_PERIOD,
            sma50Window = SMA50_WINDOW,
            sma100Window = SMA100_WINDOW,
            sma200Window = SMA200_WINDOW,
            useAvailableHistoryForSma200 = true,
        )
    }

    private data class ResolvedIndexMembers(
        val selectedIndexKey: String,
        val members: List<IndexConstituentUpsertRow>,
    )

    companion object {
        const val TOUCH_LOOKBACK_DAYS: Int = 5
        const val WATCH_ZONE_UPPER_PCT: Double = 10.0
        const val RSI_PERIOD: Int = 14
        const val SMA50_WINDOW: Int = 50
        const val SMA100_WINDOW: Int = 100
        const val SMA200_WINDOW: Int = 200
        const val HISTORY_DAYS: Long = 450
        const val MAX_ALLOWED_LATEST_GAP_DAYS: Long = 3
        const val RSI_FALLBACK: Double = 50.0
    }
}

internal fun calculateRollingSmaValues(candles: List<DailyCandle>, window: Int): List<Double> {
    if (candles.isEmpty()) return emptyList()
    if (window <= 0) return candles.map { candle -> candle.close }

    val smaValues = DoubleArray(candles.size)
    var rollingSum = 0.0
    for (index in candles.indices) {
        rollingSum += candles[index].close
        if (index >= window) {
            rollingSum -= candles[index - window].close
        }
        val effectiveWindow = minOf(window, index + 1)
        smaValues[index] = rollingSum / effectiveWindow
    }
    return smaValues.toList()
}

internal fun detectLatestLowTouch(
    candles: List<DailyCandle>,
    smaValues: List<Double>,
    lookbackDays: Int,
): LocalDate? {
    if (candles.isEmpty() || smaValues.isEmpty()) return null
    val startIndex = (candles.size - lookbackDays).coerceAtLeast(0)
    for (index in candles.lastIndex downTo startIndex) {
        val sma = smaValues.getOrNull(index) ?: continue
        val low = candles[index].low
        if (low <= sma) {
            return candles[index].candleDate
        }
    }
    return null
}

internal fun resolveHotSmaSignalTag(
    sma100TouchDate: LocalDate?,
    sma200TouchDate: LocalDate?,
    pctToSma200: Double?,
): HotSmaSignalTag? {
    return when {
        sma200TouchDate != null -> HotSmaSignalTag.AGGRESSIVE_BUY
        sma100TouchDate != null -> HotSmaSignalTag.STANDARD_BUY
        pctToSma200 != null && pctToSma200 >= 0.0 && pctToSma200 <= HotSmaScannerService.WATCH_ZONE_UPPER_PCT -> HotSmaSignalTag.WATCH_ZONE
        else -> null
    }
}
