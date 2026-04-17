package com.tradingtool.core.strategy.rsimomentum

import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.technical.calculateRsi
import com.tradingtool.core.technical.roundTo2
import com.tradingtool.core.technical.toTa4jSeries
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class RsiMomentumCalibrationEngine(
    private val configService: RsiMomentumConfigService,
    private val candleHandler: CandleJdbiHandler,
    private val stockHandler: StockJdbiHandler,
    private val instrumentCache: InstrumentCache,
    private val zoneId: ZoneId,
) {

    suspend fun calibrate(
        config: RsiMomentumConfig,
        options: RsiMomentumCalibrationOptions = RsiMomentumCalibrationOptions(),
    ): RsiMomentumCalibrationReport {
        val rawToDate = options.toDate ?: LocalDate.now(zoneId).minusDays(1)
        val rawFromDate = options.fromDate ?: rawToDate.minusYears(DEFAULT_LOOKBACK_YEARS)
        val (fromDate, toDate) = if (rawFromDate.isAfter(rawToDate)) {
            Pair(rawToDate, rawFromDate)
        } else {
            Pair(rawFromDate, rawToDate)
        }
        val sampleRange = "$fromDate..$toDate"
        val watchlistStocks = stockHandler.read { dao -> dao.listAll() }
        val selectedProfiles = if (options.profileIds.isEmpty()) {
            config.profiles
        } else {
            config.profiles.filter { profile -> profile.id in options.profileIds }
        }
        val profileResults = mutableListOf<RsiMomentumProfileCalibrationResult>()
        for (profile in selectedProfiles) {
            profileResults.add(
                calibrateProfile(
                    profile = profile,
                    fromDate = fromDate,
                    toDate = toDate,
                    sampleRange = sampleRange,
                    watchlistStocks = watchlistStocks,
                    options = options,
                ),
            )
        }

        return RsiMomentumCalibrationReport(
            runAt = Instant.now().toString(),
            method = CALIBRATION_METHOD,
            sampleRange = sampleRange,
            transactionCostBps = options.transactionCostBps,
            profileResults = profileResults,
        )
    }

    private suspend fun calibrateProfile(
        profile: RsiMomentumProfileConfig,
        fromDate: LocalDate,
        toDate: LocalDate,
        sampleRange: String,
        watchlistStocks: List<com.tradingtool.core.model.stock.Stock>,
        options: RsiMomentumCalibrationOptions,
    ): RsiMomentumProfileCalibrationResult {
        val baseSymbols = configService.loadBaseUniverseSymbols(profile.baseUniversePreset)
        val universe = RsiMomentumUniverseBuilder.build(
            baseSymbols = baseSymbols,
            watchlistStocks = watchlistStocks,
            tokenLookup = { symbol -> instrumentCache.token("NSE", symbol) },
            companyNameLookup = { symbol -> instrumentCache.find("NSE", symbol)?.name?.takeIf { it.isNotBlank() } },
        )

        val periodSets = candidatePeriodSets(profile, options)
        val requiredPeriods = periodSets.flatten().toSet() + setOf(14, 22, 44, 66)
        val historyFrom = fromDate.minusDays(MAX_CALCULATION_LOOKBACK_DAYS)
        val preparedUniverse = mutableListOf<PreparedSecurity>()
        for (member in universe.members) {
            val prepared = prepareSecurity(
                member = member,
                fromDate = historyFrom,
                toDate = toDate,
                requiredPeriods = requiredPeriods,
            )
            if (prepared != null) {
                preparedUniverse.add(prepared)
            }
        }
        val rebalanceDates = buildWeeklyRebalanceDates(
            preparedUniverse = preparedUniverse,
            fromDate = fromDate,
            toDate = toDate,
            rebalanceDay = profile.rebalanceDay,
        )

        val candidates = periodSets.map { periods ->
            evaluateCandidate(
                profile = profile,
                periods = periods,
                preparedUniverse = preparedUniverse,
                rebalanceDates = rebalanceDates,
                transactionCostBps = options.transactionCostBps,
                annualizationWeeks = options.annualizationWeeks,
            )
        }.sortedWith(
            compareByDescending<RsiMomentumCalibrationCandidateResult> { it.annualizedSortino }
                .thenBy { it.averageTurnoverPct }
                .thenBy { it.rsiPeriods.maxOrNull() ?: Int.MAX_VALUE },
        )

        val selection = selectCalibrationCandidate(candidates)

        return RsiMomentumProfileCalibrationResult(
            profileId = profile.id,
            profileLabel = profile.label,
            baseUniversePreset = profile.baseUniversePreset,
            sampleRange = sampleRange,
            selectedRsiPeriods = selection.periods,
            selectionReason = selection.reason,
            candidates = candidates,
        )
    }

    private fun candidatePeriodSets(
        profile: RsiMomentumProfileConfig,
        options: RsiMomentumCalibrationOptions,
    ): List<List<Int>> = resolveCandidatePeriodSets(profile, options)

    private fun evaluateCandidate(
        profile: RsiMomentumProfileConfig,
        periods: List<Int>,
        preparedUniverse: List<PreparedSecurity>,
        rebalanceDates: List<LocalDate>,
        transactionCostBps: Double,
        annualizationWeeks: Int,
    ): RsiMomentumCalibrationCandidateResult {
        if (preparedUniverse.isEmpty() || rebalanceDates.size < 2) {
            return RsiMomentumCalibrationCandidateResult(
                rsiPeriods = periods,
                sampleWeeks = 0,
                annualizedSortino = 0.0,
                annualizedSharpe = 0.0,
                cagrPct = 0.0,
                maxDrawdownPct = 0.0,
                annualizedVolatilityPct = 0.0,
                averageWeeklyReturnPct = 0.0,
                averageTurnoverPct = 0.0,
                firstHalfSortino = 0.0,
                secondHalfSortino = 0.0,
                isStable = false,
                rejectionReasons = listOf("INSUFFICIENT_DATA"),
            )
        }

        val weeklyReturns = mutableListOf<Double>()
        val turnoverRatios = mutableListOf<Double>()
        var previousHoldings = emptyList<String>()

        for (index in 0 until (rebalanceDates.size - 1)) {
            val asOfDate = rebalanceDates[index]
            val nextDate = rebalanceDates[index + 1]

            val metrics = buildMetricsForDate(
                preparedUniverse = preparedUniverse,
                asOfDate = asOfDate,
                periods = periods,
                minAverageTradedValue = profile.minAverageTradedValue,
            )

            val ranked = RsiMomentumRanker.rank(
                metrics = metrics,
                previousHoldings = previousHoldings,
                candidateCount = profile.candidateCount,
                boardDisplayCount = profile.boardDisplayCount,
                replacementPoolCount = profile.replacementPoolCount,
                holdingCount = profile.holdingCount,
                maxMoveFrom30DayLowPct = profile.safeRules.maxMoveFrom30DayLowPct,
                minVolumeExhaustionRatio = profile.safeRules.minVolumeExhaustionRatio,
                blockedEntryDays = profile.blockedEntryDays,
            )

            val holdings = ranked.holdings.map { stock -> stock.symbol }
            val turnoverRatio = (
                (ranked.rebalance.entries.size + ranked.rebalance.exits.size).toDouble() /
                    max(profile.holdingCount, 1).toDouble()
                ).coerceAtLeast(0.0)

            val grossReturn = portfolioReturn(
                holdings = holdings,
                preparedBySymbol = preparedUniverse.associateBy { prepared -> prepared.member.symbol },
                fromDate = asOfDate,
                toDate = nextDate,
            )
            val costReturn = turnoverRatio * (transactionCostBps / 10_000.0)
            val netReturn = grossReturn - costReturn

            weeklyReturns.add(netReturn)
            turnoverRatios.add(turnoverRatio)
            previousHoldings = holdings
        }

        return scoreCandidate(
            periods = periods,
            weeklyReturns = weeklyReturns,
            turnoverRatios = turnoverRatios,
            annualizationWeeks = annualizationWeeks,
        )
    }

    private fun buildMetricsForDate(
        preparedUniverse: List<PreparedSecurity>,
        asOfDate: LocalDate,
        periods: List<Int>,
        minAverageTradedValue: Double,
    ): List<SecurityMetrics> {
        if (periods.isEmpty()) {
            return emptyList()
        }

        val requiredIndex = max((periods.maxOrNull() ?: 2), AVERAGE_WINDOW_DAYS)

        return preparedUniverse.mapNotNull { prepared ->
            val index = prepared.dateToIndex[asOfDate] ?: return@mapNotNull null
            if (index < requiredIndex) {
                return@mapNotNull null
            }

            val sma20 = prepared.sma20[index]
            if (!sma20.isFinite() || sma20 <= 0.0) {
                return@mapNotNull null
            }

            val avgValueCr = prepared.avgTradedValueCr20[index]
            if (!avgValueCr.isFinite() || avgValueCr < minAverageTradedValue) {
                return@mapNotNull null
            }

            val periodRsiValues = periods.mapNotNull { period -> prepared.rsi(period, index) }
            if (periodRsiValues.size != periods.size) {
                return@mapNotNull null
            }

            val close = prepared.close[index]
            val avgRsi = periodRsiValues.average().roundTo2()

            SecurityMetrics(
                member = prepared.member,
                asOfDate = asOfDate,
                avgRsi = avgRsi,
                rsi22 = prepared.rsi(22, index) ?: avgRsi,
                rsi44 = prepared.rsi(44, index) ?: avgRsi,
                rsi66 = prepared.rsi(66, index) ?: avgRsi,
                close = close,
                sma20 = sma20,
                buyZoneLow10w = close,
                buyZoneHigh10w = close,
                lowestRsi50d = prepared.rsi(14, index)?.roundTo2() ?: 50.0,
                highestRsi50d = prepared.rsi(14, index)?.roundTo2() ?: 50.0,
                avgTradedValueCr = avgValueCr,
            )
        }
    }

    private fun scoreCandidate(
        periods: List<Int>,
        weeklyReturns: List<Double>,
        turnoverRatios: List<Double>,
        annualizationWeeks: Int,
    ): RsiMomentumCalibrationCandidateResult {
        val sampleWeeks = weeklyReturns.size
        val annualizedSortino = annualizedSortino(weeklyReturns, annualizationWeeks)
        val annualizedSharpe = annualizedSharpe(weeklyReturns, annualizationWeeks)
        val cagrPct = cagrPct(weeklyReturns, annualizationWeeks)
        val maxDrawdownPct = maxDrawdownPct(weeklyReturns)
        val annualizedVolatilityPct = annualizedVolatilityPct(weeklyReturns, annualizationWeeks)
        val averageWeeklyReturnPct = (weeklyReturns.averageOrZero() * 100.0).roundTo2()
        val averageTurnoverPct = (turnoverRatios.averageOrZero() * 100.0).roundTo2()

        val firstHalf = weeklyReturns.take(sampleWeeks / 2)
        val secondHalf = weeklyReturns.drop(sampleWeeks / 2)
        val firstHalfSortino = annualizedSortino(firstHalf, annualizationWeeks)
        val secondHalfSortino = annualizedSortino(secondHalf, annualizationWeeks)

        val rejectionReasons = buildList {
            if (sampleWeeks < MIN_SAMPLE_WEEKS) {
                add("INSUFFICIENT_SAMPLE_WEEKS")
            }
            if (maxDrawdownPct > MAX_STABLE_DRAWDOWN_PCT) {
                add("MAX_DRAWDOWN_TOO_HIGH")
            }
            if (averageTurnoverPct > MAX_STABLE_TURNOVER_PCT) {
                add("TURNOVER_TOO_HIGH")
            }
            if (secondHalfSortino < MIN_SECOND_HALF_SORTINO) {
                add("WEAK_RECENT_SORTINO")
            }
            if (abs(firstHalfSortino - secondHalfSortino) > MAX_SORTINO_SPLIT_GAP) {
                add("UNSTABLE_SPLIT_SORTINO")
            }
        }

        return RsiMomentumCalibrationCandidateResult(
            rsiPeriods = periods,
            sampleWeeks = sampleWeeks,
            annualizedSortino = annualizedSortino,
            annualizedSharpe = annualizedSharpe,
            cagrPct = cagrPct,
            maxDrawdownPct = maxDrawdownPct,
            annualizedVolatilityPct = annualizedVolatilityPct,
            averageWeeklyReturnPct = averageWeeklyReturnPct,
            averageTurnoverPct = averageTurnoverPct,
            firstHalfSortino = firstHalfSortino,
            secondHalfSortino = secondHalfSortino,
            isStable = rejectionReasons.isEmpty(),
            rejectionReasons = rejectionReasons,
        )
    }

    private fun selectCalibrationCandidate(candidates: List<RsiMomentumCalibrationCandidateResult>): CalibrationSelection {
        return selectCalibrationCandidate(candidates, SORTINO_CLOSE_BAND)
    }

    private fun portfolioReturn(
        holdings: List<String>,
        preparedBySymbol: Map<String, PreparedSecurity>,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): Double {
        if (holdings.isEmpty()) {
            return 0.0
        }

        val returns = holdings.mapNotNull { symbol ->
            val prepared = preparedBySymbol[symbol] ?: return@mapNotNull null
            val entry = prepared.closeOn(fromDate) ?: return@mapNotNull null
            val exit = prepared.closeOn(toDate) ?: return@mapNotNull null
            if (entry <= 0.0) {
                return@mapNotNull null
            }
            (exit / entry) - 1.0
        }

        return returns.averageOrZero()
    }

    private suspend fun prepareSecurity(
        member: UniverseMember,
        fromDate: LocalDate,
        toDate: LocalDate,
        requiredPeriods: Set<Int>,
    ): PreparedSecurity? {
        val candles = candleHandler.read { dao ->
            dao.getDailyCandles(member.instrumentToken, fromDate, toDate)
        }.sortedBy { candle -> candle.candleDate }

        if (candles.isEmpty()) {
            return null
        }

        val n = candles.size
        val close = DoubleArray(n)
        val sma20 = DoubleArray(n) { Double.NaN }
        val avgTradedValueCr20 = DoubleArray(n) { Double.NaN }
        val dateToIndex = HashMap<LocalDate, Int>(n)

        var closeWindowSum = 0.0
        var tradedValueWindowSum = 0.0

        for (i in candles.indices) {
            val candle = candles[i]
            close[i] = candle.close
            dateToIndex[candle.candleDate] = i

            val tradedValue = candle.close * candle.volume.toDouble()
            closeWindowSum += candle.close
            tradedValueWindowSum += tradedValue

            if (i >= AVERAGE_WINDOW_DAYS) {
                closeWindowSum -= candles[i - AVERAGE_WINDOW_DAYS].close
                tradedValueWindowSum -= candles[i - AVERAGE_WINDOW_DAYS].close * candles[i - AVERAGE_WINDOW_DAYS].volume.toDouble()
            }

            if (i >= (AVERAGE_WINDOW_DAYS - 1)) {
                sma20[i] = (closeWindowSum / AVERAGE_WINDOW_DAYS).roundTo2()
                avgTradedValueCr20[i] = ((tradedValueWindowSum / AVERAGE_WINDOW_DAYS) / CRORE_DIVISOR).roundTo2()
            }
        }

        val series = candles.toTa4jSeries(member.symbol)
        val rsiByPeriod = requiredPeriods.associateWith { period ->
            val indicator = series.calculateRsi(period)
            DoubleArray(n) { index -> indicator.getValue(index).doubleValue() }
        }

        return PreparedSecurity(
            member = member,
            dateToIndex = dateToIndex,
            close = close,
            sma20 = sma20,
            avgTradedValueCr20 = avgTradedValueCr20,
            rsiByPeriod = rsiByPeriod,
        )
    }

    private fun buildWeeklyRebalanceDates(
        preparedUniverse: List<PreparedSecurity>,
        fromDate: LocalDate,
        toDate: LocalDate,
        rebalanceDay: String,
    ): List<LocalDate> {
        if (preparedUniverse.isEmpty()) {
            return emptyList()
        }

        val targetDay = parseRebalanceDay(rebalanceDay)
        val globalDates = preparedUniverse
            .flatMap { prepared -> prepared.dateToIndex.keys }
            .filter { date -> !date.isBefore(fromDate) && !date.isAfter(toDate) }
            .distinct()
            .sorted()

        val weekFields = WeekFields.ISO
        val picks = linkedMapOf<String, WeeklyPick>()

        for (date in globalDates) {
            val key = "${date.get(weekFields.weekBasedYear())}-${date.get(weekFields.weekOfWeekBasedYear())}"
            val pick = picks.getOrPut(key) { WeeklyPick() }
            if (pick.latest == null || date.isAfter(pick.latest)) {
                pick.latest = date
            }
            if (date.dayOfWeek.value <= targetDay.value) {
                if (pick.beforeOrOnTarget == null || date.isAfter(pick.beforeOrOnTarget)) {
                    pick.beforeOrOnTarget = date
                }
            }
        }

        return picks.values
            .mapNotNull { pick -> pick.beforeOrOnTarget ?: pick.latest }
            .sorted()
    }

    private fun parseRebalanceDay(rebalanceDay: String): DayOfWeek =
        runCatching { DayOfWeek.valueOf(rebalanceDay.trim().uppercase()) }
            .getOrDefault(DayOfWeek.FRIDAY)

    private data class WeeklyPick(
        var beforeOrOnTarget: LocalDate? = null,
        var latest: LocalDate? = null,
    )

    private data class PreparedSecurity(
        val member: UniverseMember,
        val dateToIndex: Map<LocalDate, Int>,
        val close: DoubleArray,
        val sma20: DoubleArray,
        val avgTradedValueCr20: DoubleArray,
        val rsiByPeriod: Map<Int, DoubleArray>,
    ) {
        fun closeOn(date: LocalDate): Double? = dateToIndex[date]?.let { index -> close.getOrNull(index) }

        fun rsi(period: Int, index: Int): Double? {
            val arr = rsiByPeriod[period] ?: return null
            val value = arr.getOrNull(index) ?: return null
            return if (value.isFinite()) value.roundTo2() else null
        }
    }

    companion object {
        const val CALIBRATION_METHOD: String = "RISK_ADJUSTED_SORTINO_WITH_GUARDRAILS_V1"
        private const val CRORE_DIVISOR: Double = 10_000_000.0
        private const val AVERAGE_WINDOW_DAYS: Int = 20
        private const val MAX_CALCULATION_LOOKBACK_DAYS: Long = 400
        private const val DEFAULT_LOOKBACK_YEARS: Long = 5
        private const val MIN_SAMPLE_WEEKS: Int = 52
        private const val MAX_STABLE_DRAWDOWN_PCT: Double = 45.0
        private const val MAX_STABLE_TURNOVER_PCT: Double = 140.0
        private const val MIN_SECOND_HALF_SORTINO: Double = 0.0
        private const val MAX_SORTINO_SPLIT_GAP: Double = 1.25
        private const val SORTINO_CLOSE_BAND: Double = 0.15
    }
}

internal fun annualizedSortino(weeklyReturns: List<Double>, annualizationWeeks: Int = 52): Double {
    if (weeklyReturns.isEmpty()) {
        return 0.0
    }

    val mean = weeklyReturns.average()
    val downsideDeviation = sqrt(
        weeklyReturns
            .map { value -> minOf(0.0, value).pow(2) }
            .average(),
    )

    if (downsideDeviation <= 0.0) {
        return when {
            mean > 0.0 -> 20.0
            mean < 0.0 -> -20.0
            else -> 0.0
        }
    }

    return ((mean / downsideDeviation) * sqrt(annualizationWeeks.toDouble())).roundTo2()
}

internal fun annualizedSharpe(weeklyReturns: List<Double>, annualizationWeeks: Int = 52): Double {
    if (weeklyReturns.isEmpty()) {
        return 0.0
    }

    val mean = weeklyReturns.average()
    val std = standardDeviation(weeklyReturns)
    if (std <= 0.0) {
        return 0.0
    }

    return ((mean / std) * sqrt(annualizationWeeks.toDouble())).roundTo2()
}

internal fun annualizedVolatilityPct(weeklyReturns: List<Double>, annualizationWeeks: Int = 52): Double {
    if (weeklyReturns.isEmpty()) {
        return 0.0
    }

    return (standardDeviation(weeklyReturns) * sqrt(annualizationWeeks.toDouble()) * 100.0).roundTo2()
}

internal fun cagrPct(weeklyReturns: List<Double>, annualizationWeeks: Int = 52): Double {
    if (weeklyReturns.isEmpty()) {
        return 0.0
    }

    val terminal = weeklyReturns.fold(1.0) { acc, value -> acc * (1.0 + value) }
    if (terminal <= 0.0) {
        return -100.0
    }

    return ((terminal.pow(annualizationWeeks.toDouble() / weeklyReturns.size.toDouble()) - 1.0) * 100.0).roundTo2()
}

internal fun maxDrawdownPct(weeklyReturns: List<Double>): Double {
    if (weeklyReturns.isEmpty()) {
        return 0.0
    }

    var wealth = 1.0
    var peak = 1.0
    var maxDrawdown = 0.0

    weeklyReturns.forEach { value ->
        wealth *= (1.0 + value)
        if (wealth > peak) {
            peak = wealth
        }
        val drawdown = if (peak > 0.0) (peak - wealth) / peak else 0.0
        if (drawdown > maxDrawdown) {
            maxDrawdown = drawdown
        }
    }

    return (maxDrawdown * 100.0).roundTo2()
}

internal fun selectCalibrationCandidate(
    candidates: List<RsiMomentumCalibrationCandidateResult>,
    closeBand: Double = 0.15,
): CalibrationSelection {
    if (candidates.isEmpty()) {
        return CalibrationSelection(
            periods = RsiMomentumProfileConfig.DEFAULT_RSI_PERIODS,
            reason = "No candidate periods available; fallback to default.",
        )
    }

    val stableCandidates = candidates.filter { candidate -> candidate.isStable }
    val rankingPool = if (stableCandidates.isNotEmpty()) stableCandidates else candidates
    val top = rankingPool.maxByOrNull { candidate -> candidate.annualizedSortino } ?: rankingPool.first()
    val closeCandidates = rankingPool.filter { candidate ->
        abs(candidate.annualizedSortino - top.annualizedSortino) <= closeBand
    }

    val chosen = closeCandidates.minWithOrNull(
        compareBy<RsiMomentumCalibrationCandidateResult> { candidate -> candidate.averageTurnoverPct }
            .thenBy { candidate -> candidate.rsiPeriods.maxOrNull() ?: Int.MAX_VALUE },
    ) ?: top

    val reason = if (stableCandidates.isEmpty()) {
        "No stable candidate passed guardrails; chose highest Sortino candidate."
    } else if (closeCandidates.size > 1) {
        "Multiple candidates were statistically close (Sortino band ${closeBand.roundTo2()}); picked lower-turnover, simpler set."
    } else {
        "Chose top stable candidate by annualized Sortino with guardrails."
    }

    return CalibrationSelection(
        periods = chosen.rsiPeriods,
        reason = reason,
    )
}

internal fun resolveCandidatePeriodSets(
    profile: RsiMomentumProfileConfig,
    options: RsiMomentumCalibrationOptions,
): List<List<Int>> {
    val isSmallcap = profile.baseUniversePreset == RsiMomentumProfileConfig.DEFAULT_SMALLCAP_UNIVERSE_PRESET
    val baseSets = if (isSmallcap) options.smallcapPeriodSets else options.largeMidcapPeriodSets
    return (baseSets + listOf(profile.rsiPeriods))
        .map { periods -> periods.map { period -> period.coerceAtLeast(2) }.distinct().sorted() }
        .filter { periods -> periods.size >= 3 }
        .filter { periods -> isSmallcap || (periods.maxOrNull() ?: 0) < 252 }
        .distinct()
}

private fun standardDeviation(values: List<Double>): Double {
    if (values.size < 2) {
        return 0.0
    }

    val mean = values.average()
    val variance = values.map { value -> (value - mean).pow(2) }.average()
    return sqrt(variance)
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

internal data class CalibrationSelection(
    val periods: List<Int>,
    val reason: String,
)
