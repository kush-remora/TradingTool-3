package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.candle.dao.CandleWriteDao
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.strategy.fiftytwohigh.ChartinkFiftyTwoWeekHighBacktestConfig
import com.tradingtool.core.strategy.fiftytwohigh.ChartinkFiftyTwoWeekHighBacktestEngine
import com.tradingtool.core.strategy.fiftytwohigh.ChartinkFiftyTwoWeekHighBacktestReport
import com.tradingtool.core.strategy.fiftytwohigh.ChartinkFiftyTwoWeekHighBacktestService
import com.tradingtool.core.strategy.fiftytwohigh.ChartinkFiftyTwoWeekHighBacktestStrategy
import com.tradingtool.core.strategy.fiftytwohigh.ChartinkFiftyTwoWeekHighSignalCsvSource
import kotlinx.coroutines.runBlocking
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.ZoneId
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("ChartinkFiftyTwoWeekHighBacktestJob")

fun main() {
    val runtime = ChartinkFiftyTwoWeekHighBacktestRuntime.fromEnvironment()

    val exitCode = runBlocking {
        runCatching {
            val report = runtime.service.run(runtime.config)
            writeArtifacts(runtime.outputDir, report, runtime.objectMapper)
            log.info(
                "Chartink 52-week-high backtest completed: input={} signals={} trades={} output={}",
                runtime.config.inputFile,
                report.signalCount,
                report.trades.size,
                runtime.outputDir.toAbsolutePath(),
            )
            0
        }.getOrElse { error ->
            log.error("Chartink 52-week-high backtest failed: {}", error.message, error)
            1
        }
    }

    exitProcess(exitCode)
}

private data class ChartinkFiftyTwoWeekHighBacktestRuntime(
    val config: ChartinkFiftyTwoWeekHighBacktestConfig,
    val outputDir: Path,
    val objectMapper: ObjectMapper,
    val service: ChartinkFiftyTwoWeekHighBacktestService,
) {
    companion object {
        fun fromEnvironment(): ChartinkFiftyTwoWeekHighBacktestRuntime {
            val objectMapper = ObjectMapper()
                .findAndRegisterModules()
                .registerKotlinModule()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

            val databaseConfig = DatabaseConfig(
                jdbcUrl = ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl"),
            )
            val candleHandler = CandleJdbiHandler(
                databaseConfig,
                CandleReadDao::class.java,
                CandleWriteDao::class.java,
            )

            val service = ChartinkFiftyTwoWeekHighBacktestService(
                signalCsvSource = ChartinkFiftyTwoWeekHighSignalCsvSource(),
                engine = ChartinkFiftyTwoWeekHighBacktestEngine(),
                candleHandler = candleHandler,
            )

            val config = ChartinkFiftyTwoWeekHighBacktestConfig(
                inputFile = Paths.get(DEFAULT_INPUT_FILE),
                strategies = listOf(
                    ChartinkFiftyTwoWeekHighBacktestStrategy(
                        name = "target_5_stop_5",
                        profitTargetPct = 5.0,
                        stopLossPct = 5.0,
                    ),
                    ChartinkFiftyTwoWeekHighBacktestStrategy(
                        name = "target_10_stop_5",
                        profitTargetPct = 10.0,
                        stopLossPct = 5.0,
                    ),
                ),
                priceDataToDate = LocalDate.now(INDIA_TIME_ZONE),
            )

            return ChartinkFiftyTwoWeekHighBacktestRuntime(
                config = config,
                outputDir = Paths.get(DEFAULT_OUTPUT_DIR),
                objectMapper = objectMapper,
                service = service,
            )
        }
    }
}

private fun writeArtifacts(outputDir: Path, report: ChartinkFiftyTwoWeekHighBacktestReport, objectMapper: ObjectMapper) {
    Files.createDirectories(outputDir)
    Files.writeString(
        outputDir.resolve("latest.json"),
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report),
    )

    Files.newBufferedWriter(outputDir.resolve("latest-trades.csv")).use { writer ->
        CSVPrinter(
            writer,
            CSVFormat.DEFAULT.builder()
                .setHeader(
                    "strategy_name",
                    "symbol",
                    "market_cap_name",
                    "market_cap_bucket",
                    "sector",
                    "signal_date",
                    "entry_date",
                    "exit_date",
                    "entry_price",
                    "exit_price",
                    "target_price",
                    "stop_price",
                    "outcome",
                    "success",
                    "holding_trading_days",
                    "holding_calendar_days",
                    "return_pct",
                    "max_favorable_excursion_pct",
                    "max_adverse_excursion_pct",
                    "forward_5d_return_pct",
                    "forward_10d_return_pct",
                    "forward_20d_return_pct",
                    "forward_60d_return_pct",
                    "exit_was_ambiguous",
                    "latest_available_date",
                )
                .build(),
        ).use { csv ->
            report.trades.forEach { trade ->
                csv.printRecord(
                    trade.strategyName,
                    trade.symbol,
                    trade.marketCapName,
                    trade.marketCapBucket,
                    trade.sector,
                    trade.signalDate,
                    trade.entryDate,
                    trade.exitDate,
                    trade.entryPrice,
                    trade.exitPrice,
                    trade.targetPrice,
                    trade.stopPrice,
                    trade.outcome,
                    trade.success,
                    trade.holdingTradingDays,
                    trade.holdingCalendarDays,
                    trade.returnPct,
                    trade.maxFavorableExcursionPct,
                    trade.maxAdverseExcursionPct,
                    trade.forward5dReturnPct,
                    trade.forward10dReturnPct,
                    trade.forward20dReturnPct,
                    trade.forward60dReturnPct,
                    trade.exitWasAmbiguous,
                    trade.latestAvailableDate,
                )
            }
        }
    }

    Files.newBufferedWriter(outputDir.resolve("latest-summary.csv")).use { writer ->
        CSVPrinter(
            writer,
            CSVFormat.DEFAULT.builder()
                .setHeader(
                    "strategy_name",
                    "market_cap_bucket",
                    "total_signals",
                    "entered_trades",
                    "success_count",
                    "stop_loss_count",
                    "end_exit_count",
                    "no_entry_count",
                    "success_rate_pct",
                    "avg_holding_trading_days",
                    "median_holding_trading_days",
                )
                .build(),
        ).use { csv ->
            report.summaries.forEach { summary ->
                csv.printRecord(
                    summary.strategyName,
                    "Overall",
                    summary.totalSignals,
                    summary.enteredTrades,
                    summary.successCount,
                    summary.stopLossCount,
                    summary.endExitCount,
                    summary.noEntryCount,
                    summary.successRatePct,
                    summary.avgHoldingTradingDays,
                    summary.medianHoldingTradingDays,
                )

                summary.buckets.forEach { bucket ->
                    csv.printRecord(
                        bucket.strategyName,
                        bucket.marketCapBucket,
                        bucket.totalSignals,
                        bucket.enteredTrades,
                        bucket.successCount,
                        bucket.stopLossCount,
                        bucket.endExitCount,
                        bucket.noEntryCount,
                        bucket.successRatePct,
                        bucket.avgHoldingTradingDays,
                        bucket.medianHoldingTradingDays,
                    )
                }
            }
        }
    }
}

private const val DEFAULT_INPUT_FILE: String = "manual-input/Backtest 52 week high first time.csv"
private const val DEFAULT_OUTPUT_DIR: String = "build/reports/chartink-fiftytwo-week-high-backtest"
private val INDIA_TIME_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
