package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.candle.dao.CandleWriteDao
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.earnings.EarningsRefreshRequest
import com.tradingtool.core.earnings.EarningsRefreshResult
import com.tradingtool.core.earnings.EarningsResultService
import com.tradingtool.core.earnings.EarningsStockTokenLookup
import com.tradingtool.core.earnings.FileCorporateEventAdapter
import com.tradingtool.core.earnings.JdbiCandleGateway
import com.tradingtool.core.earnings.JdbiEarningsResultGateway
import com.tradingtool.core.earnings.dao.EarningsResultReadDao
import com.tradingtool.core.earnings.dao.EarningsResultWriteDao
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.watchlist.groww.KiteInstrumentTokenResolver
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("EarningsResultsRefreshJob")

fun main(args: Array<String>) {
    val runtime = EarningsResultsRuntime.fromEnvironment()

    val today = LocalDate.now()
    val from = today
    val to = from.plusMonths(1)

    val exitCode = runBlocking {
        runCatching {
            val result = runtime.service.refresh(
                EarningsRefreshRequest(
                    from = from,
                    to = to,
                    pastDays = DEFAULT_PAST_DAYS,
                    chunkDays = DEFAULT_CHUNK_DAYS,
                ),
            )
            val outputDir = writeArtifacts(result)
            log.info(
                "Earnings refresh complete: from={} to={} chunks={} fetched={} unique={} upserted={} unresolved={} nullAfter={} notNullEnforced={} pastRows={} behaviorUpdated={} output={}",
                result.from,
                result.to,
                result.chunkCount,
                result.fetchedEvents,
                result.uniqueEvents,
                result.upsertedRows,
                result.unresolvedTokenCount,
                result.nullInstrumentTokenRowsAfterRefresh,
                result.instrumentTokenNotNullEnforced,
                result.pastRowsEvaluated,
                result.behaviorRowsUpdated,
                outputDir.toAbsolutePath(),
            )
            0
        }.getOrElse { error ->
            log.error("Earnings refresh failed: {}", error.message, error)
            1
        }
    }

    exitProcess(exitCode)
}

private data class EarningsResultsRuntime(
    val service: EarningsResultService,
) {
    companion object {
        fun fromEnvironment(): EarningsResultsRuntime {
            val databaseConfig = DatabaseConfig(
                jdbcUrl = ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl"),
            )
            val objectMapper = buildObjectMapper()

            val earningsHandler = JdbiHandler(databaseConfig, EarningsResultReadDao::class.java, EarningsResultWriteDao::class.java)
            val candleHandler = JdbiHandler(databaseConfig, CandleReadDao::class.java, CandleWriteDao::class.java)
            val tokenHandler = JdbiHandler(databaseConfig, KiteTokenReadDao::class.java, KiteTokenWriteDao::class.java)

            val kiteTokenLookup = runCatching {
                val resolver = KiteInstrumentTokenResolver(
                    kiteClient = buildAuthenticatedKiteClient(tokenHandler),
                    instrumentCache = InstrumentCache(),
                )
                KiteOnlyEarningsTokenLookup(resolver)
            }.getOrElse { error ->
                log.warn("Kite token resolver unavailable; earnings rows with unresolved symbols will be skipped: {}", error.message)
                null
            }

            val service = EarningsResultService(
                corporateEventSource = FileCorporateEventAdapter(
                    filePath = Paths.get(DEFAULT_EARNINGS_EVENTS_FILE),
                    objectMapper = objectMapper,
                ),
                earningsGateway = JdbiEarningsResultGateway(earningsHandler),
                candleGateway = JdbiCandleGateway(candleHandler),
                stockTokenLookup = kiteTokenLookup,
                enableStockBackfill = false,
                objectMapper = objectMapper,
            )

            return EarningsResultsRuntime(service = service)
        }
    }
}

private class KiteOnlyEarningsTokenLookup(
    private val resolver: KiteInstrumentTokenResolver,
) : EarningsStockTokenLookup {
    override suspend fun findInstrumentTokenBySymbol(symbol: String): Long? {
        return resolver.resolve("NSE", symbol.uppercase())
    }
}

private fun buildAuthenticatedKiteClient(
    tokenHandler: JdbiHandler<KiteTokenReadDao, KiteTokenWriteDao>,
): KiteConnectClient {
    val kiteClient = KiteConnectClient(
        KiteConfig(
            apiKey = ConfigLoader.get("KITE_API_KEY", "kite.apiKey"),
            apiSecret = ConfigLoader.get("KITE_API_SECRET", "kite.apiSecret"),
        ),
    )

    val latestToken = runBlocking {
        tokenHandler.read { dao -> dao.getLatestToken() }
    }?.takeIf { token -> token.isNotBlank() }
        ?: error("Kite authentication required. No token found in kite_tokens table.")

    kiteClient.applyAccessToken(latestToken)
    return kiteClient
}

private fun writeArtifacts(result: EarningsRefreshResult): Path {
    val outputDir = Paths.get("build", "reports", "earnings-results-refresh")
    Files.createDirectories(outputDir)

    val objectMapper = buildObjectMapper()
    Files.writeString(outputDir.resolve("latest.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result))
    Files.writeString(outputDir.resolve("latest.md"), result.toMarkdown())
    return outputDir
}

private fun EarningsRefreshResult.toMarkdown(): String {
    return buildString {
        appendLine("# Earnings Results Refresh Report")
        appendLine()
        appendLine("- Window: `${from}` → `${to}`")
        appendLine("- Chunk days: `${chunkDays}`")
        appendLine("- Chunk count: `${chunkCount}`")
        appendLine("- Fetched events: `${fetchedEvents}`")
        appendLine("- Unique events: `${uniqueEvents}`")
        appendLine("- Upserted rows: `${upsertedRows}`")
        appendLine("- Unresolved tokens: `${unresolvedTokenCount}`")
        appendLine("- Null instrument_token rows after refresh: `${nullInstrumentTokenRowsAfterRefresh}`")
        appendLine("- instrument_token NOT NULL enforced: `${instrumentTokenNotNullEnforced}`")
        if (unresolvedSymbolsSample.isNotEmpty()) {
            appendLine("- Unresolved symbol sample: `${unresolvedSymbolsSample.joinToString(", ")}`")
        }
        appendLine("- Past behavior window: `${pastFrom}` → `${pastTo}`")
        appendLine("- Past rows evaluated: `${pastRowsEvaluated}`")
        appendLine("- Behavior rows updated: `${behaviorRowsUpdated}`")
        appendLine("- Stage updates: pre_result=`${preResultUpdates}`, result_day=`${resultDayUpdates}`, next_day=`${nextDayUpdates}`, plus_5d=`${plus5dUpdates}`")
    }
}

private fun buildObjectMapper(): ObjectMapper {
    return ObjectMapper()
        .findAndRegisterModules()
        .registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}

private const val DEFAULT_EARNINGS_EVENTS_FILE = "manual-input/groww-corporate-events.json"
private const val DEFAULT_PAST_DAYS = 30
private const val DEFAULT_CHUNK_DAYS = 5
