package com.tradingtool.core.screener

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.candle.dao.CandleWriteDao
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
import com.tradingtool.core.technical.RollingRsiBounds
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.time.LocalDate

class WeeklyPatternServiceTest {

    private val service = WeeklyPatternService(
        stockHandler = JdbiHandler(DatabaseConfig(""), StockReadDao::class.java, StockWriteDao::class.java),
        candleCache = CandleCacheService(
            candleHandler = JdbiHandler(DatabaseConfig(""), CandleReadDao::class.java, CandleWriteDao::class.java),
            redis = RedisHandler("redis://localhost:6379"),
            objectMapper = jacksonObjectMapper(),
        ),
        patternConfigService = WeeklyPatternConfigService(),
    )

    @AfterTest
    fun cleanupGeneratedConfig() {
        File("weekly_pattern_config.json").delete()
    }

    @Test
    fun `analyzeSeasonality picks monday low and tuesday target-hit exit`() {
        val weeks = buildLtLikeWeeks()
        val rsiMap = supportiveRsiMap(weeks)
        val config = WeeklyPatternConfig(
            lookbackWeeks = weeks.size,
            minWeeksRequired = 4,
            targetRecommendation = TargetRecommendationConfig(
                candidateTargetsPct = listOf(5.0, 6.0, 7.0),
                minSamples = 3,
                minWinRatePct = 50.0,
                maxStopLossRatePct = 45.0,
                fallbackTargetPct = 5.0,
            ),
        )

        val analysis = invokePrivate<Any>("analyzeSeasonality", weeks, rsiMap, config)
        val eval = analysis.getProperty<WeeklyPatternService.DayPairEval>("eval")

        assertEquals(1, eval.buyDay)
        assertEquals(2, eval.effectiveSellDay)
        assertTrue(eval.avgSwingPct >= 5.0, "Expected observed Mon->Tue swing, got ${eval.avgSwingPct}")
        assertEquals(8, eval.reboundConsistency)
        assertEquals(3, eval.swingConsistency)
    }

    @Test
    fun `evaluateTargetScenarios counts stop losses correctly`() {
        val weeks = buildLtLikeWeeks()
        val rsiMap = supportiveRsiMap(weeks)
        val config = WeeklyPatternConfig(
            lookbackWeeks = weeks.size,
            minWeeksRequired = 4,
            targetRecommendation = TargetRecommendationConfig(
                candidateTargetsPct = listOf(5.0),
                minSamples = 3,
                minWinRatePct = 50.0,
                maxStopLossRatePct = 45.0,
                fallbackTargetPct = 5.0,
            ),
        )

        @Suppress("UNCHECKED_CAST")
        val scenarios = invokePrivate<List<TargetScenario>>(
            "evaluateTargetScenarios",
            weeks,
            1,
            5,
            rsiMap,
            config,
            false,
        )

        val scenario = scenarios.single()
        assertEquals(8, scenario.entries)
        assertTrue(abs(scenario.winRatePct - 37.5) < 0.01, "Expected three target hits in eight entries")
        assertTrue(abs(scenario.stopLossRatePct - 50.0) < 0.01, "Expected four stop-loss weeks in eight entries")
    }

    @Test
    fun `analyzeSeasonality prefers tuesday when monday lows do not become trades`() {
        val weeks = buildTuesdayWinnerWeeks()
        val rsiMap = supportiveRsiMap(weeks)
        val config = WeeklyPatternConfig(
            lookbackWeeks = weeks.size,
            minWeeksRequired = 4,
            targetRecommendation = TargetRecommendationConfig(
                candidateTargetsPct = listOf(5.0),
                minSamples = 3,
                minWinRatePct = 50.0,
                maxStopLossRatePct = 45.0,
                fallbackTargetPct = 5.0,
            ),
        )

        val analysis = invokePrivate<Any>("analyzeSeasonality", weeks, rsiMap, config)
        val eval = analysis.getProperty<WeeklyPatternService.DayPairEval>("eval")

        assertEquals(2, eval.buyDay)
        assertEquals(3, eval.effectiveSellDay)
    }

    @Test
    fun `evaluateTargetScenarios skips entries only when rsi is near recent max`() {
        val weeks = buildLtLikeWeeks()
        val config = WeeklyPatternConfig(
            lookbackWeeks = weeks.size,
            minWeeksRequired = 4,
            rsiEntry = RsiEntryConfig(
                lookbackDays = 50,
                overboughtPercentile = 90.0,
            ),
            targetRecommendation = TargetRecommendationConfig(
                candidateTargetsPct = listOf(5.0),
                minSamples = 3,
                minWinRatePct = 50.0,
                maxStopLossRatePct = 45.0,
                fallbackTargetPct = 5.0,
            ),
        )

        @Suppress("UNCHECKED_CAST")
        val scenarios = invokePrivate<List<TargetScenario>>(
            "evaluateTargetScenarios",
            weeks,
            1,
            5,
            overboughtRsiMap(weeks),
            config,
            false,
        )

        assertEquals(0, scenarios.single().entries)
    }

    private fun buildLtLikeWeeks(): List<WeeklyPatternService.WeekInstance> {
        val base = LocalDate.of(2026, 1, 5)
        val mondayLowWeeks = listOf(
            week(
                base,
                monday = candle(base, 105.0, 102.0, 100.0, 101.0),
                tuesday = candle(base.plusDays(1), 101.0, 107.0, 100.5, 106.0),
                wednesday = candle(base.plusDays(2), 106.0, 106.5, 103.5, 104.0),
                thursday = candle(base.plusDays(3), 104.0, 105.0, 102.5, 103.0),
                friday = candle(base.plusDays(4), 103.0, 104.0, 101.5, 102.0),
            ),
            week(
                base.plusWeeks(1),
                monday = candle(base.plusWeeks(1), 106.0, 102.5, 100.0, 101.0),
                tuesday = candle(base.plusWeeks(1).plusDays(1), 101.0, 106.0, 100.8, 105.5),
                wednesday = candle(base.plusWeeks(1).plusDays(2), 105.5, 108.0, 104.5, 107.0),
                thursday = candle(base.plusWeeks(1).plusDays(3), 107.0, 107.5, 104.0, 105.0),
                friday = candle(base.plusWeeks(1).plusDays(4), 105.0, 105.5, 103.0, 104.0),
            ),
            week(
                base.plusWeeks(2),
                monday = candle(base.plusWeeks(2), 105.0, 102.2, 100.0, 101.0),
                tuesday = candle(base.plusWeeks(2).plusDays(1), 101.0, 107.0, 100.6, 106.0),
                wednesday = candle(base.plusWeeks(2).plusDays(2), 106.0, 106.2, 103.0, 104.0),
                thursday = candle(base.plusWeeks(2).plusDays(3), 104.0, 105.0, 102.0, 103.0),
                friday = candle(base.plusWeeks(2).plusDays(4), 103.0, 103.5, 101.0, 102.0),
            ),
            week(
                base.plusWeeks(3),
                monday = candle(base.plusWeeks(3), 105.0, 102.0, 100.0, 101.0),
                tuesday = candle(base.plusWeeks(3).plusDays(1), 101.0, 103.0, 97.0, 98.5),
                wednesday = candle(base.plusWeeks(3).plusDays(2), 98.5, 100.0, 97.5, 99.0),
                thursday = candle(base.plusWeeks(3).plusDays(3), 99.0, 100.5, 98.0, 99.5),
                friday = candle(base.plusWeeks(3).plusDays(4), 99.5, 100.0, 98.5, 99.0),
            ),
            week(
                base.plusWeeks(4),
                monday = candle(base.plusWeeks(4), 105.0, 102.0, 100.0, 101.5),
                tuesday = candle(base.plusWeeks(4).plusDays(1), 101.5, 104.0, 100.8, 103.0),
                wednesday = candle(base.plusWeeks(4).plusDays(2), 103.0, 104.4, 101.0, 103.5),
                thursday = candle(base.plusWeeks(4).plusDays(3), 103.5, 104.2, 102.0, 103.4),
                friday = candle(base.plusWeeks(4).plusDays(4), 103.4, 104.5, 102.5, 104.0),
            ),
        )

        val mixedWeeks = listOf(
            week(
                base.plusWeeks(5),
                monday = candle(base.plusWeeks(5), 105.0, 106.0, 103.5, 104.5),
                tuesday = candle(base.plusWeeks(5).plusDays(1), 104.5, 105.5, 102.5, 103.0),
                wednesday = candle(base.plusWeeks(5).plusDays(2), 103.0, 104.0, 100.0, 101.0),
                thursday = candle(base.plusWeeks(5).plusDays(3), 101.0, 103.0, 100.5, 102.5),
                friday = candle(base.plusWeeks(5).plusDays(4), 102.5, 104.0, 101.5, 103.0),
            ),
            week(
                base.plusWeeks(6),
                monday = candle(base.plusWeeks(6), 105.0, 105.5, 103.0, 104.0),
                tuesday = candle(base.plusWeeks(6).plusDays(1), 104.0, 104.5, 101.5, 102.0),
                wednesday = candle(base.plusWeeks(6).plusDays(2), 102.0, 103.0, 100.0, 101.0),
                thursday = candle(base.plusWeeks(6).plusDays(3), 101.0, 106.0, 100.5, 105.5),
                friday = candle(base.plusWeeks(6).plusDays(4), 105.5, 105.8, 103.5, 104.0),
            ),
            week(
                base.plusWeeks(7),
                monday = candle(base.plusWeeks(7), 105.0, 106.0, 102.5, 104.5),
                tuesday = candle(base.plusWeeks(7).plusDays(1), 104.5, 108.0, 103.0, 107.0),
                wednesday = candle(base.plusWeeks(7).plusDays(2), 107.0, 107.2, 104.0, 105.0),
                thursday = candle(base.plusWeeks(7).plusDays(3), 105.0, 105.5, 102.5, 103.0),
                friday = candle(base.plusWeeks(7).plusDays(4), 103.0, 104.0, 100.0, 101.0),
            ),
        )

        return mondayLowWeeks + mixedWeeks
    }

    private fun buildTuesdayWinnerWeeks(): List<WeeklyPatternService.WeekInstance> {
        val base = LocalDate.of(2026, 3, 2)
        return (0..5).map { offset ->
            val mondayDate = base.plusWeeks(offset.toLong())
            week(
                mondayDate,
                monday = candle(mondayDate, 105.0, 100.8, 100.0, 100.2),
                tuesday = candle(mondayDate.plusDays(1), 101.5, 103.2, 101.0, 102.8),
                wednesday = candle(mondayDate.plusDays(2), 102.8, 108.0, 102.5, 107.0),
                thursday = candle(mondayDate.plusDays(3), 107.0, 107.5, 105.5, 106.5),
                friday = candle(mondayDate.plusDays(4), 106.5, 107.0, 105.0, 105.5),
            )
        }
    }

    private fun supportiveRsiMap(
        weeks: List<WeeklyPatternService.WeekInstance>,
        current: Double = 30.0,
        lowest: Double = 25.0,
        highest: Double = 75.0,
    ): Map<LocalDate, RollingRsiBounds> {
        return buildRsiMap(weeks, current, lowest, highest)
    }

    private fun overboughtRsiMap(
        weeks: List<WeeklyPatternService.WeekInstance>,
        current: Double = 72.0,
        lowest: Double = 25.0,
        highest: Double = 75.0,
    ): Map<LocalDate, RollingRsiBounds> {
        return buildRsiMap(weeks, current, lowest, highest)
    }

    private fun buildRsiMap(
        weeks: List<WeeklyPatternService.WeekInstance>,
        current: Double,
        lowest: Double,
        highest: Double,
    ): Map<LocalDate, RollingRsiBounds> {
        val bounds = RollingRsiBounds(
            current = current,
            lowest = lowest,
            highest = highest,
        )
        return weeks
            .flatMap { it.dailyCandles.values }
            .associate { candle -> candle.candleDate to bounds }
    }

    private fun week(
        start: LocalDate,
        monday: DailyCandle,
        tuesday: DailyCandle,
        wednesday: DailyCandle,
        thursday: DailyCandle,
        friday: DailyCandle,
    ): WeeklyPatternService.WeekInstance {
        return WeeklyPatternService.WeekInstance(
            isoYear = start.year,
            isoWeek = start.dayOfYear,
            startDate = start,
            endDate = start.plusDays(4),
            dailyCandles = mapOf(
                1 to monday,
                2 to tuesday,
                3 to wednesday,
                4 to thursday,
                5 to friday,
            ),
        )
    }

    private fun candle(date: LocalDate, open: Double, high: Double, low: Double, close: Double): DailyCandle {
        return DailyCandle(
            instrumentToken = 1L,
            symbol = "LT",
            candleDate = date,
            open = open,
            high = high,
            low = low,
            close = close,
            volume = 1_000_000L,
        )
    }

    private inline fun <reified T> invokePrivate(methodName: String, vararg args: Any): T {
        val parameterTypes = args.map { arg ->
            when (arg) {
                is List<*> -> List::class.java
                is Map<*, *> -> Map::class.java
                is Boolean -> java.lang.Boolean.TYPE
                is Int -> java.lang.Integer.TYPE
                is Double -> java.lang.Double.TYPE
                else -> arg::class.java
            }
        }.toTypedArray()

        val method = service.javaClass.getDeclaredMethod(methodName, *parameterTypes)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(service, *args) as T
    }

    private inline fun <reified T> Any.getProperty(name: String): T {
        val getter = javaClass.getDeclaredMethod("get${name.replaceFirstChar { it.uppercase() }}")
        getter.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return getter.invoke(this) as T
    }
}
