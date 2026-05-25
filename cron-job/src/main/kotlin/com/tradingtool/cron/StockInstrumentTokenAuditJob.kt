package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentReadDao
import com.tradingtool.core.indexconstituents.dao.IndexConstituentWriteDao
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.InstrumentTokenResolution
import com.tradingtool.core.kite.InstrumentTokenResolverService
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.model.stock.Stock
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.OffsetDateTime
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("StockInstrumentTokenAuditJob")

fun main(args: Array<String>) {
    val runtime = StockInstrumentTokenAuditRuntime.fromEnvironment()
    val exitCode = runBlocking {
        runCatching {
            val report = executeAudit(runtime)
            val outputDir = writeArtifacts(report)
            log.info(
                "Stock instrument token audit complete: mode={} total={} matched={} mismatched={} unresolved={} correctedStocks={} correctedIndexConstituents={} report={}",
                report.totalStocks,
                report.matchedCount,
                report.mismatchedCount,
                report.unresolvedCount,
                report.correctedStocksCount,
                report.correctedIndexConstituentsCount,
                outputDir.toAbsolutePath(),
            )
            require(report.unresolvedCount == 0) {
                "StockInstrumentTokenAuditJob unresolved symbols detected: ${report.unresolvedSymbols.take(30).joinToString(", ")}"
            }
            0
        }.getOrElse { error ->
            log.error("Stock instrument token audit failed: {}", error.message, error)
            1
        }
    }
    exitProcess(exitCode)
}

private data class StockInstrumentTokenAuditCli(
    val applyChanges: Boolean,
)

private data class StockInstrumentTokenAuditRuntime(
    val stockHandler: JdbiHandler<StockReadDao, StockWriteDao>,
    val indexConstituentHandler: JdbiHandler<IndexConstituentReadDao, IndexConstituentWriteDao>,
    val resolver: InstrumentTokenResolverService,
) {
    companion object
}

private data class TokenAuditRow(
    val symbol: String,
    val exchange: String,
    val dbInstrumentToken: Long,
    val resolvedInstrumentToken: Long?,
    val matchedKey: String?,
    val expectedKeys: List<String>,
    val candidateKeys: List<String>,
    val status: String,
)

private data class TokenAuditReport(
    val mode: String,
    val totalStocks: Int,
    val matchedCount: Int,
    val mismatchedCount: Int,
    val unresolvedCount: Int,
    val correctedStocksCount: Int,
    val correctedIndexConstituentsCount: Int,
    val unresolvedSymbols: List<String>,
    val rows: List<TokenAuditRow>,
)

private fun parseArgs(args: Array<String>): StockInstrumentTokenAuditCli {
    val applyChanges = args.any { arg -> arg.trim().equals("--apply", ignoreCase = true) }
    return StockInstrumentTokenAuditCli(applyChanges = applyChanges)
}

private fun StockInstrumentTokenAuditRuntime.Companion.fromEnvironment(): StockInstrumentTokenAuditRuntime {
    val databaseConfig = DatabaseConfig(
        jdbcUrl = ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl"),
    )
    val stockHandler = JdbiHandler(databaseConfig, StockReadDao::class.java, StockWriteDao::class.java)
    val indexConstituentHandler =
        JdbiHandler(databaseConfig, IndexConstituentReadDao::class.java, IndexConstituentWriteDao::class.java)
    val tokenHandler = JdbiHandler(databaseConfig, KiteTokenReadDao::class.java, KiteTokenWriteDao::class.java)
    val kiteClient = buildAuthenticatedKiteClient(tokenHandler)
    val resolver = InstrumentTokenResolverService(
        kiteClient = kiteClient,
        instrumentCache = InstrumentCache(),
    )
    return StockInstrumentTokenAuditRuntime(
        stockHandler = stockHandler,
        indexConstituentHandler = indexConstituentHandler,
        resolver = resolver,
    )
}

private suspend fun executeAudit(
    runtime: StockInstrumentTokenAuditRuntime
): TokenAuditReport {
    val stocks = runtime.stockHandler.read { dao -> dao.listAll() }
        .filter { stock -> stock.exchange.equals(NSE_EXCHANGE, ignoreCase = true) }

    val rows = mutableListOf<TokenAuditRow>()
    var correctedStocksCount = 0
    var correctedIndexConstituentsCount = 0

    for (stock in stocks) {
        val resolution = runtime.resolver.resolveDetailed(stock.exchange, stock.symbol)
        val row = buildAuditRow(stock, resolution)
        rows += row

        if (row.status == STATUS_MISMATCH && row.resolvedInstrumentToken != null) {
            correctedStocksCount += runtime.stockHandler.write { dao ->
                dao.updateInstrumentTokenBySymbolExchange(
                    symbol = row.symbol,
                    exchange = row.exchange,
                    instrumentToken = row.resolvedInstrumentToken,
                )
            }
            correctedIndexConstituentsCount += runtime.indexConstituentHandler.write { dao ->
                dao.updateInstrumentTokenBySymbol(
                    symbol = row.symbol,
                    instrumentToken = row.resolvedInstrumentToken,
                    syncedAt = OffsetDateTime.now(),
                )
            }
        }
    }

    val sortedRows = rows.sortedWith(compareBy<TokenAuditRow>({ it.status }, { it.symbol }))
    val unresolvedSymbols = sortedRows
        .filter { row -> row.status == STATUS_UNRESOLVED }
        .map { row -> row.symbol }

    return TokenAuditReport(
        mode = "APPLY",
        totalStocks = sortedRows.size,
        matchedCount = sortedRows.count { row -> row.status == STATUS_MATCHED },
        mismatchedCount = sortedRows.count { row -> row.status == STATUS_MISMATCH },
        unresolvedCount = unresolvedSymbols.size,
        correctedStocksCount = correctedStocksCount,
        correctedIndexConstituentsCount = correctedIndexConstituentsCount,
        unresolvedSymbols = unresolvedSymbols,
        rows = sortedRows,
    )
}

private fun buildAuditRow(stock: Stock, resolution: InstrumentTokenResolution): TokenAuditRow {
    val resolvedToken = resolution.resolvedToken
    val status = when {
        resolvedToken == null -> STATUS_UNRESOLVED
        stock.instrumentToken == resolvedToken -> STATUS_MATCHED
        else -> STATUS_MISMATCH
    }

    return TokenAuditRow(
        symbol = stock.symbol.uppercase(),
        exchange = stock.exchange.uppercase(),
        dbInstrumentToken = stock.instrumentToken,
        resolvedInstrumentToken = resolvedToken,
        matchedKey = resolution.matchedKey,
        expectedKeys = resolution.expectedKeys,
        candidateKeys = resolution.candidateKeys,
        status = status,
    )
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

private fun writeArtifacts(report: TokenAuditReport): Path {
    val outputDir = Paths.get("build", "reports", "stock-instrument-token-audit")
    Files.createDirectories(outputDir)

    val objectMapper = ObjectMapper()
        .findAndRegisterModules()
        .registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    Files.writeString(
        outputDir.resolve("latest.json"),
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report),
    )
    Files.writeString(
        outputDir.resolve("latest.csv"),
        buildCsv(report.rows),
    )
    return outputDir
}

private fun buildCsv(rows: List<TokenAuditRow>): String {
    return buildString {
        appendLine(
            "symbol,exchange,db_instrument_token,resolved_instrument_token,matched_key,expected_keys,candidate_keys,status",
        )
        rows.forEach { row ->
            appendLine(
                listOf(
                    row.symbol,
                    row.exchange,
                    row.dbInstrumentToken.toString(),
                    row.resolvedInstrumentToken?.toString().orEmpty(),
                    row.matchedKey.orEmpty(),
                    row.expectedKeys.joinToString(" | "),
                    row.candidateKeys.joinToString(" | "),
                    row.status,
                ).joinToString(",") { value -> csvEscape(value) },
            )
        }
    }
}

private fun csvEscape(value: String): String {
    val mustQuote = value.contains(',') || value.contains('"') || value.contains('\n')
    if (!mustQuote) return value
    return "\"" + value.replace("\"", "\"\"") + "\""
}

private const val NSE_EXCHANGE: String = "NSE"
private const val STATUS_MATCHED: String = "MATCHED"
private const val STATUS_MISMATCH: String = "MISMATCH"
private const val STATUS_UNRESOLVED: String = "UNRESOLVED"
