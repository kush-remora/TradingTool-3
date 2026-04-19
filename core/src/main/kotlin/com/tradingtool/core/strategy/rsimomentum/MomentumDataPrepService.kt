package com.tradingtool.core.strategy.rsimomentum

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.RsiMomentumSnapshotJdbiHandler
import com.tradingtool.core.technical.roundTo2
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class MomentumDataPrepareRequest(
    val profileId: String,
    val fromDate: String,
    val toDate: String,
)

data class MomentumDataPrepareResponse(
    val profileId: String,
    val fromDate: String,
    val toDate: String,
    val tradingDaysProcessed: Int,
    val todayAsOfDateUsed: String?,
    val top50Count: Int,
    val warnings: List<String>,
)

data class MomentumRangeExportDocument(
    val schema_version: String,
    val generated_at: String,
    val tag: String,
    val from_date: String,
    val to_date: String,
    val trading_days_count: Int,
    val days: List<MomentumRangeExportDay>,
)

data class MomentumRangeExportDay(
    val date: String,
    val profile_id: String,
    val top50_count: Int,
    val rows: List<MomentumRangeExportRow>,
)

data class MomentumRangeExportRow(
    val rank: Int,
    val symbol: String,
    val company_name: String,
    val instrument_token: Long,
    val momentum_score: Double,
    val rsi_22: Double,
    val rsi_44: Double,
    val rsi_66: Double,
    val rank_5d_ago: Int?,
    val rank_improvement: Int?,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val close: Double?,
    val ema_20: Double,
    val extension_above_ema20_pct: Double,
    val avg_vol_3d: Double,
    val avg_vol_20d: Double,
    val volume_ratio_3d_vs_20d: Double,
    val traded_value_cr_20d_avg: Double,
    val low_30d: Double,
    val move_from_30d_low_pct: Double,
    val high_52w: Double?,
    val distance_from_52w_high_pct: Double?,
    val entry_action: String?,
    val entry_blocked: Boolean,
    val entry_block_reason: String?,
)

data class MomentumTodayExportDocument(
    val schema_version: String,
    val generated_at: String,
    val tag: String,
    val requested_date: String,
    val as_of_date_used: String,
    val anchor_policy: String,
    val top50_count: Int,
    val stocks: List<MomentumTodayExportStock>,
)

data class MomentumTodayExportStock(
    val rank: Int,
    val symbol: String,
    val company_name: String,
    val instrument_token: Long,
    val momentum_score: Double,
    val rsi_22: Double,
    val rsi_44: Double,
    val rsi_66: Double,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val close: Double?,
    val ema_20: Double,
    val high_52w: Double?,
    val distance_from_52w_high_pct: Double?,
    val candles_1y: List<MomentumDailyCandleRow>,
)

data class MomentumDailyCandleRow(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)

@Singleton
class MomentumDataPrepService @Inject constructor(
    private val configService: RsiMomentumConfigService,
    private val backfillService: RsiMomentumBackfillService,
    private val snapshotHandler: RsiMomentumSnapshotJdbiHandler,
    private val candleHandler: CandleJdbiHandler,
) {
    private val log = LoggerFactory.getLogger(MomentumDataPrepService::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val ist: ZoneId = ZoneId.of("Asia/Kolkata")

    suspend fun prepare(request: MomentumDataPrepareRequest): MomentumDataPrepareResponse {
        val profileId = request.profileId.trim()
        val fromDate = parseDate(request.fromDate, "fromDate")
        val toDate = parseDate(request.toDate, "toDate")
        validateProfile(profileId)
        require(!fromDate.isAfter(toDate)) { "fromDate must be on or before toDate." }

        backfillService.backfill(
            BackfillRequest(
                profileId = profileId,
                fromDate = fromDate.toString(),
                toDate = toDate.toString(),
                skipExisting = true,
            ),
        )

        val historyRows = snapshotHandler.read { dao ->
            dao.listByProfileAndDateRange(profileId, fromDate, toDate)
        }

        val todayDate = LocalDate.now(ist)
        ensureRecentBackfill(profileId, todayDate)
        val todayRecord = snapshotHandler.read { dao -> dao.getLatestOnOrBefore(profileId, todayDate) }
        val todaySnapshot = todayRecord?.let { safeParseSnapshot(it.snapshotJson) }

        val warnings = buildList {
            if (historyRows.isEmpty()) add("No snapshots found in selected date range.")
            if (todayRecord == null) add("No snapshot found on or before today for profile '$profileId'.")
            if (todaySnapshot == null && todayRecord != null) add("Latest snapshot exists but could not be parsed.")
        }

        return MomentumDataPrepareResponse(
            profileId = profileId,
            fromDate = fromDate.toString(),
            toDate = toDate.toString(),
            tradingDaysProcessed = historyRows.size,
            todayAsOfDateUsed = todayRecord?.asOfDate?.toString(),
            top50Count = todaySnapshot?.topCandidates?.take(TOP_COUNT)?.size ?: 0,
            warnings = warnings,
        )
    }

    suspend fun buildRangeExport(
        profileIdRaw: String,
        fromRaw: String,
        toRaw: String,
    ): MomentumRangeExportDocument {
        val profileId = profileIdRaw.trim()
        val fromDate = parseDate(fromRaw, "from")
        val toDate = parseDate(toRaw, "to")
        validateProfile(profileId)
        require(!fromDate.isAfter(toDate)) { "from must be on or before to." }

        backfillService.backfill(
            BackfillRequest(
                profileId = profileId,
                fromDate = fromDate.toString(),
                toDate = toDate.toString(),
                skipExisting = true,
            ),
        )

        val records = snapshotHandler.read { dao ->
            dao.listByProfileAndDateRange(profileId, fromDate, toDate)
        }

        val parsedDays = records.mapNotNull { record ->
            val snapshot = safeParseSnapshot(record.snapshotJson) ?: return@mapNotNull null
            record.asOfDate to snapshot
        }

        val symbolsByToken = parsedDays
            .flatMap { (_, snapshot) -> snapshot.topCandidates.take(TOP_COUNT) }
            .associate { stock -> stock.instrumentToken to stock.symbol }

        val candlesByToken = loadCandlesByToken(
            symbolsByToken = symbolsByToken,
            fromDate = fromDate.minusDays(LOOKBACK_52W_DAYS + 1),
            toDate = toDate,
        )

        val days = parsedDays.map { (asOfDate, snapshot) ->
            val rows = snapshot.topCandidates.take(TOP_COUNT).map { stock ->
                val candles = candlesByToken[stock.instrumentToken].orEmpty()
                val candleForDate = candles.firstOrNull { candle -> candle.candleDate == asOfDate }
                val high52w = trailingHigh52w(candles, asOfDate)
                val closeValue = candleForDate?.close ?: stock.close
                val ema20 = computeEma20(candles, asOfDate) ?: stock.sma20
                val extensionAboveEma20Pct = computeExtensionAboveMovingAveragePct(closeValue, ema20)
                    ?: stock.extensionAboveSma20Pct
                val distanceFrom52wHighPct = computeDistanceFromHighPct(closeValue, high52w)

                MomentumRangeExportRow(
                    rank = stock.rank,
                    symbol = stock.symbol,
                    company_name = stock.companyName,
                    instrument_token = stock.instrumentToken,
                    momentum_score = stock.avgRsi,
                    rsi_22 = stock.rsi22,
                    rsi_44 = stock.rsi44,
                    rsi_66 = stock.rsi66,
                    rank_5d_ago = stock.rank5DaysAgo,
                    rank_improvement = stock.rankImprovement,
                    open = candleForDate?.open,
                    high = candleForDate?.high,
                    low = candleForDate?.low,
                    close = closeValue,
                    ema_20 = ema20,
                    extension_above_ema20_pct = extensionAboveEma20Pct,
                    avg_vol_3d = stock.avgVol3d,
                    avg_vol_20d = stock.avgVol20d,
                    volume_ratio_3d_vs_20d = stock.volumeRatio,
                    traded_value_cr_20d_avg = stock.avgTradedValueCr,
                    low_30d = deriveLow30d(stock.close, stock.moveFrom30DayLowPct),
                    move_from_30d_low_pct = stock.moveFrom30DayLowPct,
                    high_52w = high52w,
                    distance_from_52w_high_pct = distanceFrom52wHighPct,
                    entry_action = stock.entryAction,
                    entry_blocked = stock.entryBlocked,
                    entry_block_reason = stock.entryBlockReason,
                )
            }

            MomentumRangeExportDay(
                date = asOfDate.toString(),
                profile_id = profileId,
                top50_count = rows.size,
                rows = rows,
            )
        }

        return MomentumRangeExportDocument(
            schema_version = SCHEMA_VERSION,
            generated_at = Instant.now().toString(),
            tag = profileId,
            from_date = fromDate.toString(),
            to_date = toDate.toString(),
            trading_days_count = days.size,
            days = days,
        )
    }

    suspend fun buildTodayExport(profileIdRaw: String): MomentumTodayExportDocument {
        val profileId = profileIdRaw.trim()
        validateProfile(profileId)

        val todayDate = LocalDate.now(ist)
        ensureRecentBackfill(profileId, todayDate)

        val latestRecord = snapshotHandler.read { dao ->
            dao.getLatestOnOrBefore(profileId, todayDate)
        } ?: throw IllegalArgumentException("No snapshot found on or before today for profile '$profileId'.")

        val snapshot = safeParseSnapshot(latestRecord.snapshotJson)
            ?: throw IllegalArgumentException("Failed to parse latest snapshot for profile '$profileId'.")

        val topStocks = snapshot.topCandidates.take(TOP_COUNT)
        val symbolsByToken = topStocks.associate { stock -> stock.instrumentToken to stock.symbol }
        val candlesByToken = loadCandlesByToken(
            symbolsByToken = symbolsByToken,
            fromDate = latestRecord.asOfDate.minusDays(LOOKBACK_1Y_DAYS + 1),
            toDate = latestRecord.asOfDate,
        )

        val stocks = topStocks.map { stock ->
            val candles = candlesByToken[stock.instrumentToken].orEmpty()
            val dayCandle = candles.firstOrNull { candle -> candle.candleDate == latestRecord.asOfDate }
            val high52w = trailingHigh52w(candles, latestRecord.asOfDate)
            val closeValue = dayCandle?.close ?: stock.close
            val ema20 = computeEma20(candles, latestRecord.asOfDate) ?: stock.sma20
            val distanceFrom52wHighPct = computeDistanceFromHighPct(closeValue, high52w)
            val oneYearCandles = candles
                .filter { candle ->
                    !candle.candleDate.isBefore(latestRecord.asOfDate.minusDays(LOOKBACK_1Y_DAYS - 1))
                            && !candle.candleDate.isAfter(latestRecord.asOfDate)
                }
                .map { candle ->
                    MomentumDailyCandleRow(
                        date = candle.candleDate.toString(),
                        open = candle.open,
                        high = candle.high,
                        low = candle.low,
                        close = candle.close,
                        volume = candle.volume,
                    )
                }

            MomentumTodayExportStock(
                rank = stock.rank,
                symbol = stock.symbol,
                company_name = stock.companyName,
                instrument_token = stock.instrumentToken,
                momentum_score = stock.avgRsi,
                rsi_22 = stock.rsi22,
                rsi_44 = stock.rsi44,
                rsi_66 = stock.rsi66,
                open = dayCandle?.open,
                high = dayCandle?.high,
                low = dayCandle?.low,
                close = closeValue,
                ema_20 = ema20,
                high_52w = high52w,
                distance_from_52w_high_pct = distanceFrom52wHighPct,
                candles_1y = oneYearCandles,
            )
        }

        return MomentumTodayExportDocument(
            schema_version = SCHEMA_VERSION,
            generated_at = Instant.now().toString(),
            tag = profileId,
            requested_date = todayDate.toString(),
            as_of_date_used = latestRecord.asOfDate.toString(),
            anchor_policy = "latest_on_or_before_today",
            top50_count = stocks.size,
            stocks = stocks,
        )
    }

    fun toJson(value: Any): String = mapper.writeValueAsString(value)

    private suspend fun ensureRecentBackfill(profileId: String, todayDate: LocalDate) {
        val recentFrom = todayDate.minusDays(RECENT_BACKFILL_WINDOW_DAYS)
        runCatching {
            backfillService.backfill(
                BackfillRequest(
                    profileId = profileId,
                    fromDate = recentFrom.toString(),
                    toDate = todayDate.toString(),
                    skipExisting = true,
                ),
            )
        }.onFailure { error ->
            log.warn("Recent backfill failed for profile={} in {}..{}: {}", profileId, recentFrom, todayDate, error.message)
        }
    }

    private fun validateProfile(profileId: String) {
        require(profileId.isNotBlank()) { "profileId is required." }
        val allowedProfiles = configService.loadConfig().profiles.map { profile -> profile.id }.toSet()
        require(profileId in allowedProfiles) {
            "Unknown profileId '$profileId'. Allowed values: ${allowedProfiles.sorted().joinToString(", ")}"
        }
    }

    private fun parseDate(rawDate: String, fieldName: String): LocalDate =
        runCatching { LocalDate.parse(rawDate) }.getOrElse {
            throw IllegalArgumentException("Invalid $fieldName. Expected YYYY-MM-DD.")
        }

    private fun safeParseSnapshot(snapshotJson: String): RsiMomentumSnapshot? =
        runCatching { mapper.readValue(snapshotJson, RsiMomentumSnapshot::class.java) }
            .onFailure { error -> log.warn("Failed to parse snapshot JSON: {}", error.message) }
            .getOrNull()

    private suspend fun loadCandlesByToken(
        symbolsByToken: Map<Long, String>,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): Map<Long, List<DailyCandle>> {
        return symbolsByToken.keys.associateWith { token ->
            candleHandler.read { dao ->
                dao.getDailyCandles(token, fromDate, toDate)
            }.sortedBy { candle -> candle.candleDate }
        }
    }

    private fun trailingHigh52w(candles: List<DailyCandle>, asOfDate: LocalDate): Double? {
        val startDate = asOfDate.minusDays(LOOKBACK_52W_DAYS)
        val window = candles.filter { candle ->
            !candle.candleDate.isBefore(startDate) && !candle.candleDate.isAfter(asOfDate)
        }
        return window.maxOfOrNull { candle -> candle.high }?.roundTo2()
    }

    private fun computeDistanceFromHighPct(close: Double?, high: Double?): Double? {
        if (close == null || high == null || high <= 0.0) {
            return null
        }
        return (((close - high) / high) * 100.0).roundTo2()
    }

    private fun computeExtensionAboveMovingAveragePct(close: Double?, average: Double?): Double? {
        if (close == null || average == null || average <= 0.0) {
            return null
        }
        return (((close / average) - 1.0) * 100.0).roundTo2()
    }

    private fun computeEma20(candles: List<DailyCandle>, asOfDate: LocalDate): Double? {
        val closes = candles
            .asSequence()
            .filter { candle -> !candle.candleDate.isAfter(asOfDate) }
            .map { candle -> candle.close }
            .toList()

        if (closes.isEmpty()) {
            return null
        }

        val smoothing = 2.0 / (EMA_PERIOD + 1.0)
        var ema = closes.first()
        for (index in 1 until closes.size) {
            ema = (closes[index] * smoothing) + (ema * (1.0 - smoothing))
        }
        return ema.roundTo2()
    }

    private fun deriveLow30d(close: Double, moveFrom30DayLowPct: Double): Double {
        val ratio = 1.0 + (moveFrom30DayLowPct / 100.0)
        if (ratio <= 0.0) {
            return 0.0
        }
        return (close / ratio).roundTo2()
    }

    companion object {
        private const val TOP_COUNT: Int = 50
        private const val EMA_PERIOD: Int = 20
        private const val LOOKBACK_52W_DAYS: Long = 365
        private const val LOOKBACK_1Y_DAYS: Long = 365
        private const val RECENT_BACKFILL_WINDOW_DAYS: Long = 45
        private const val SCHEMA_VERSION: String = "1.0"
    }
}
