package com.tradingtool.core.strategy.weeklybase

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Singleton
class WeeklyBaseGroupBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient,
    private val configService: WeeklyBaseDefinitionConfigService,
    private val engine: WeeklyBaseGroupBacktestEngine,
) {
    suspend fun run(request: WeeklyBaseGroupBacktestRequest): WeeklyBaseGroupBacktestReport {
        val indexKeys = request.indexKeys.map(String::trim).filter(String::isNotEmpty).distinct()
        require(indexKeys.isNotEmpty()) { "Select at least one index group." }
        val config = configService.loadConfig()
        val memberResults = mutableListOf<MemberBacktestResult>()
        for (indexKey in indexKeys) {
            val members = indexConstituentHandler.read { dao -> dao.listActiveByIndex(indexKey) }
            for (member in members) {
                if (member.instrumentToken <= 0) continue
                memberResults += backtestMember(indexKey, member, config)
            }
        }
        val rows = memberResults.map(MemberBacktestResult::row)
        val groups = indexKeys.map { indexKey -> summarizeGroup(indexKey, rows.filter { it.indexKey == indexKey }) }
        return WeeklyBaseGroupBacktestReport(
            testedFromDate = memberResults.mapNotNull(MemberBacktestResult::testedFromDate).minOrNull() ?: "-",
            testedToDate = memberResults.mapNotNull(MemberBacktestResult::testedToDate).maxOrNull() ?: "-",
            groups = groups,
            rows = rows.sortedWith(compareBy(WeeklyBaseGroupBacktestRow::indexKey, WeeklyBaseGroupBacktestRow::symbol)),
        )
    }

    private suspend fun backtestMember(
        indexKey: String,
        member: IndexConstituentUpsertRow,
        config: WeeklyBaseDefinitionConfig,
    ): MemberBacktestResult {
        val symbol = member.symbol.trim().uppercase()
        val candles = loadCandles(symbol, member.instrumentToken)
        if (candles.isEmpty()) return MemberBacktestResult(WeeklyBaseGroupBacktestRow(indexKey, symbol, member.companyName, 0, 0, 0, 0, null, null, null, emptyList()), null, null)
        val result = engine.run(symbol, candles, config)
        val latestBase = result.latestValidBase
        return MemberBacktestResult(WeeklyBaseGroupBacktestRow(
            indexKey = indexKey,
            symbol = symbol,
            companyName = member.companyName,
            validBaseCount = result.baseReport.validBaseCount,
            filledTradeCount = result.trades.size,
            targetHitCount = result.trades.count { it.outcome == "TARGET_HIT" },
            openTradeCount = result.trades.count { it.outcome == "OPEN" },
            latestZoneFloor = latestBase?.zoneFloor,
            latestZoneCeiling = latestBase?.zoneCeiling,
            latestSmaDistancePct = latestBase?.distanceFromSma200Pct,
            trades = result.trades,
        ), result.baseReport.testedFromDate, result.baseReport.testedToDate)
    }

    private fun summarizeGroup(indexKey: String, rows: List<WeeklyBaseGroupBacktestRow>) = WeeklyBaseGroupBacktestGroupSummary(
        indexKey = indexKey,
        totalStocks = rows.size,
        stocksWithValidBase = rows.count { it.validBaseCount > 0 },
        filledTradeCount = rows.sumOf(WeeklyBaseGroupBacktestRow::filledTradeCount),
        targetHitCount = rows.sumOf(WeeklyBaseGroupBacktestRow::targetHitCount),
        openTradeCount = rows.sumOf(WeeklyBaseGroupBacktestRow::openTradeCount),
    )

    private suspend fun loadCandles(symbol: String, instrumentToken: Long): List<DailyCandle> {
        val toDate = LocalDate.now()
        val fromDate = toDate.minusDays(HISTORY_CALENDAR_DAYS)
        var candles = candleCacheService.getDailyCandles(instrumentToken, symbol, fromDate, toDate).sortedBy(DailyCandle::candleDate)
        val latestDate = candles.lastOrNull()?.candleDate
        val gapDays = latestDate?.let { ChronoUnit.DAYS.between(it, toDate) } ?: Long.MAX_VALUE
        if (candles.isEmpty() || gapDays > MAX_ALLOWED_LATEST_GAP_DAYS) {
            candleDataService.syncDailyRange(listOf(symbol), fromDate, toDate, kiteClient)
            candles = candleCacheService.getDailyCandles(instrumentToken, symbol, fromDate, toDate).sortedBy(DailyCandle::candleDate)
        }
        return candles
    }

    private companion object {
        const val HISTORY_CALENDAR_DAYS = 800L
        const val MAX_ALLOWED_LATEST_GAP_DAYS = 3L
    }

    private data class MemberBacktestResult(
        val row: WeeklyBaseGroupBacktestRow,
        val testedFromDate: String?,
        val testedToDate: String?,
    )
}
