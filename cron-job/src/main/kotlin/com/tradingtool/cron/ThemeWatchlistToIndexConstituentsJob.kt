package com.tradingtool.cron

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.indexconstituents.KiteIndexConstituentTokenResolver
import com.tradingtool.core.indexconstituents.dao.IndexConstituentReadDao
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.indexconstituents.dao.IndexConstituentWriteDao
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.model.DatabaseConfig
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.OffsetDateTime
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("ThemeWatchlistToIndexConstituentsJob")

fun main() {
    val runtime = ThemeWatchlistRuntime.fromEnvironment()

    val exitCode = runBlocking {
        runCatching {
            val report = runtime.run()
            val outputDir = writeArtifacts(report)
            log.info(
                "Theme import completed: themes={} processed={} upserted={} unresolved={} output={}",
                report.themeCount,
                report.stockCount,
                report.upsertedCount,
                report.unresolved.size,
                outputDir.toAbsolutePath(),
            )
            0
        }.getOrElse { error ->
            log.error("Theme import failed: {}", error.message, error)
            1
        }
    }

    exitProcess(exitCode)
}

private data class ThemeWatchlistRuntime(
    val objectMapper: ObjectMapper,
    val inputFile: Path,
    val indexWriteDaoHandler: JdbiHandler<IndexConstituentReadDao, IndexConstituentWriteDao>,
    val tokenResolver: KiteIndexConstituentTokenResolver,
) {
    suspend fun run(): ThemeImportReport {
        val payload = objectMapper.readValue(inputFile.toFile(), ThemeWatchlistPayload::class.java)
        val syncedAt = OffsetDateTime.now()

        val unresolved = mutableListOf<UnresolvedStock>()
        val rows = mutableListOf<IndexConstituentUpsertRow>()

        payload.themes.forEach { theme ->
            val indexKey = toIndexKey(theme.theme)
            val uniqueStocks = theme.stocks
                .asSequence()
                .map { stock -> stock.copy(symbol = stock.symbol.trim().uppercase()) }
                .filter { stock -> stock.symbol.isNotBlank() }
                .distinctBy { stock -> stock.symbol }
                .toList()

            uniqueStocks.forEach { stock ->
                val resolution = tokenResolver.resolveDetailed(exchange = NSE_EXCHANGE, symbol = stock.symbol)
                val token = resolution.resolvedToken
                if (token == null || token <= 0L) {
                    unresolved += UnresolvedStock(
                        theme = theme.theme,
                        indexKey = indexKey,
                        symbol = stock.symbol,
                    )
                    return@forEach
                }

                rows += IndexConstituentUpsertRow(
                    indexKey = indexKey,
                    symbol = stock.symbol,
                    instrumentToken = token,
                    companyName = stock.companyName.trim().ifBlank { stock.symbol },
                    industry = DUMMY_VALUE,
                    series = DUMMY_VALUE,
                    isinCode = DUMMY_VALUE,
                    sourceUrl = SOURCE_URL,
                )
            }
        }

        val upsertedCount = rows
            .chunked(BATCH_SIZE)
            .sumOf { batch ->
                indexWriteDaoHandler.write { dao ->
                    dao.upsertBatch(batch, syncedAt).sum()
                }
            }

        return ThemeImportReport(
            inputFile = inputFile.toString(),
            themeCount = payload.themes.size,
            stockCount = rows.size,
            upsertedCount = upsertedCount,
            unresolved = unresolved,
        )
    }

    private fun toIndexKey(themeName: String): String {
        val normalized = themeName
            .trim()
            .lowercase()
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "theme" }

        val key = "theme_$normalized"
        return if (key.length > MAX_INDEX_KEY_LENGTH) key.take(MAX_INDEX_KEY_LENGTH) else key
    }

    companion object {
        fun fromEnvironment(): ThemeWatchlistRuntime {
            val objectMapper = ObjectMapper()
                .findAndRegisterModules()
                .registerKotlinModule()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

            val databaseConfig = DatabaseConfig(
                jdbcUrl = ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl"),
            )

            val indexHandler = JdbiHandler(databaseConfig, IndexConstituentReadDao::class.java, IndexConstituentWriteDao::class.java)
            val tokenHandler = JdbiHandler(databaseConfig, KiteTokenReadDao::class.java, KiteTokenWriteDao::class.java)

            val tokenResolver = KiteIndexConstituentTokenResolver(
                kiteClient = buildAuthenticatedKiteClient(tokenHandler),
                instrumentCache = InstrumentCache(),
            )

            return ThemeWatchlistRuntime(
                objectMapper = objectMapper,
                inputFile = Paths.get(DEFAULT_INPUT_FILE),
                indexWriteDaoHandler = indexHandler,
                tokenResolver = tokenResolver,
            )
        }

        private const val DEFAULT_INPUT_FILE = "manual-input/watchlist_group_themes.json"
        private const val MAX_INDEX_KEY_LENGTH = 80
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

private fun writeArtifacts(report: ThemeImportReport): Path {
    val outputDir = Paths.get("build", "reports", "theme-watchlist-import")
    Files.createDirectories(outputDir)

    val objectMapper = ObjectMapper()
        .findAndRegisterModules()
        .registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    Files.writeString(outputDir.resolve("latest.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report))
    return outputDir
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ThemeWatchlistPayload(
    @JsonProperty("themes")
    val themes: List<ThemeEntry>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ThemeEntry(
    @JsonProperty("theme")
    val theme: String,
    @JsonProperty("stocks")
    val stocks: List<ThemeStock>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ThemeStock(
    @JsonProperty("symbol")
    val symbol: String,
    @JsonProperty("company_name")
    val companyName: String,
)

private data class ThemeImportReport(
    val inputFile: String,
    val themeCount: Int,
    val stockCount: Int,
    val upsertedCount: Int,
    val unresolved: List<UnresolvedStock>,
)

private data class UnresolvedStock(
    val theme: String,
    val indexKey: String,
    val symbol: String,
)

private const val NSE_EXCHANGE = "NSE"
private const val DUMMY_VALUE = ""
private const val SOURCE_URL = "manual://watchlist-group-pdf"
private const val BATCH_SIZE = 200
