package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.database.GrowwVolumeShockerJdbiHandler
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.InstrumentTokenResolverService
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.volumeshocker.groww.FileGrowwVolumeShockerSource
import com.tradingtool.core.volumeshocker.groww.GrowwVolumeShockerIngestionRequest
import com.tradingtool.core.volumeshocker.groww.GrowwVolumeShockerIngestionService
import com.tradingtool.core.volumeshocker.groww.GrowwVolumeShockerInstrumentTokenResolver
import com.tradingtool.core.volumeshocker.groww.JdbiGrowwVolumeShockerGateway
import com.tradingtool.core.volumeshocker.groww.dao.GrowwVolumeShockerReadDao
import com.tradingtool.core.volumeshocker.groww.dao.GrowwVolumeShockerWriteDao
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.ZoneId
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("GrowwVolumeShockerIngestionJob")

fun main() {
    val exitCode = runCatching {
        val tradeDate = LocalDate.now(INDIA_TIME_ZONE)
        val runtime = GrowwVolumeShockerRuntime.fromEnvironment()

        runBlocking {
            val result = runtime.service.ingest(
                GrowwVolumeShockerIngestionRequest(tradeDate = tradeDate),
            )
            log.info(
                "Groww volume-shocker ingestion completed: date={} input={} fetched={} stored={}",
                tradeDate,
                runtime.inputFile,
                result.fetchedCount,
                result.storedCount,
            )
        }
        0
    }.getOrElse { error ->
        log.error("Groww volume-shocker ingestion failed: {}", error.message, error)
        1
    }

    exitProcess(exitCode)
}

private data class GrowwVolumeShockerRuntime(
    val inputFile: Path,
    val service: GrowwVolumeShockerIngestionService,
) {
    companion object {
        fun fromEnvironment(): GrowwVolumeShockerRuntime {
            val objectMapper = buildGrowwVolumeShockerObjectMapper()
            val databaseConfig = DatabaseConfig(
                jdbcUrl = ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl"),
            )
            val volumeShockerHandler = GrowwVolumeShockerJdbiHandler(
                databaseConfig,
                GrowwVolumeShockerReadDao::class.java,
                GrowwVolumeShockerWriteDao::class.java,
            )
            val tokenHandler = JdbiHandler(
                databaseConfig,
                KiteTokenReadDao::class.java,
                KiteTokenWriteDao::class.java,
            )
            val tokenResolver = InstrumentTokenResolverService(
                kiteClient = buildGrowwVolumeShockerKiteClient(tokenHandler),
                instrumentCache = InstrumentCache(),
            )
            val inputFile = Paths.get(DEFAULT_INPUT_FILE)

            val service = GrowwVolumeShockerIngestionService(
                source = FileGrowwVolumeShockerSource(inputFile, objectMapper),
                instrumentTokenResolver = object : GrowwVolumeShockerInstrumentTokenResolver {
                    override suspend fun resolve(exchange: String, symbol: String): Long? {
                        return tokenResolver.resolve(exchange, symbol)
                    }
                },
                gateway = JdbiGrowwVolumeShockerGateway(volumeShockerHandler),
            )

            return GrowwVolumeShockerRuntime(
                inputFile = inputFile,
                service = service,
            )
        }
    }
}

private fun buildGrowwVolumeShockerKiteClient(
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

private fun buildGrowwVolumeShockerObjectMapper(): ObjectMapper {
    return ObjectMapper()
        .findAndRegisterModules()
        .registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}

private const val DEFAULT_INPUT_FILE = "manual-input/groww_volume_shocker"
private val INDIA_TIME_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
