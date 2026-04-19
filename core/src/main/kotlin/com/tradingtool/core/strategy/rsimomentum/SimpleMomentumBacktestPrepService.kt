package com.tradingtool.core.strategy.rsimomentum

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import org.slf4j.LoggerFactory
import java.time.LocalDate

data class SimpleMomentumBacktestPrepareRequest(
    val profileId: String,
    val fromDate: String,
    val toDate: String,
)

data class SimpleMomentumBacktestPrepareResponse(
    val profileId: String,
    val profileLabel: String,
    val baseUniversePreset: String,
    val requestedFromDate: String,
    val requestedToDate: String,
    val symbolsTargeted: Int,
    val candleSync: CandleDataService.DailyCandleSyncResult,
    val snapshotBackfill: BackfillResult,
    val warnings: List<String>,
)

@Singleton
class SimpleMomentumBacktestPrepService @Inject constructor(
    private val configService: RsiMomentumConfigService,
    private val candleDataService: CandleDataService,
    private val backfillService: RsiMomentumBackfillService,
    private val kiteClient: KiteConnectClient,
) {
    private val log = LoggerFactory.getLogger(SimpleMomentumBacktestPrepService::class.java)

    suspend fun prepare(request: SimpleMomentumBacktestPrepareRequest): SimpleMomentumBacktestPrepareResponse {
        val profileId = request.profileId.trim()
        require(profileId.isNotEmpty()) { "profileId is required." }

        val fromDate = parseDate(request.fromDate, "fromDate")
        val toDate = parseDate(request.toDate, "toDate")
        require(!fromDate.isAfter(toDate)) { "fromDate must be on or before toDate." }

        val config = configService.loadConfig()
        val profile = config.profiles.find { profileCfg -> profileCfg.id == profileId }
            ?: throw IllegalArgumentException("Profile '$profileId' not found in RSI momentum config.")

        val baseSymbols = configService.loadBaseUniverseSymbols(profile.baseUniversePreset)
            .map { symbol -> symbol.trim().uppercase() }
            .filter { symbol -> symbol.isNotEmpty() }
            .distinct()

        if (baseSymbols.isEmpty()) {
            throw IllegalArgumentException(
                "No symbols found for preset '${profile.baseUniversePreset}'. Check universe resource configuration.",
            )
        }

        log.info(
            "Simple momentum prepare started: profile={} from={} to={} symbols={}",
            profileId,
            fromDate,
            toDate,
            baseSymbols.size,
        )

        val candleSync = candleDataService.syncDailyRange(
            symbols = baseSymbols,
            fromDate = fromDate,
            toDate = toDate,
            kiteClient = kiteClient,
        )

        val snapshotBackfill = backfillService.backfill(
            BackfillRequest(
                profileId = profileId,
                fromDate = fromDate.toString(),
                toDate = toDate.toString(),
                skipExisting = true,
            ),
        )

        val warnings = buildList {
            if (candleSync.symbolsFailed > 0) {
                add("Daily candle sync failed for ${candleSync.symbolsFailed} symbol(s).")
            }
            if (snapshotBackfill.tradingDatesFound == 0) {
                add("No trading dates found in daily candles for selected range.")
            }
            if (snapshotBackfill.datesFailed > 0) {
                add("Snapshot backfill failed for ${snapshotBackfill.datesFailed} date(s).")
            }
        }

        return SimpleMomentumBacktestPrepareResponse(
            profileId = profile.id,
            profileLabel = profile.label,
            baseUniversePreset = profile.baseUniversePreset,
            requestedFromDate = fromDate.toString(),
            requestedToDate = toDate.toString(),
            symbolsTargeted = baseSymbols.size,
            candleSync = candleSync,
            snapshotBackfill = snapshotBackfill,
            warnings = warnings,
        )
    }

    private fun parseDate(raw: String, field: String): LocalDate {
        val normalized = raw.trim()
        require(normalized.isNotEmpty()) { "$field is required." }
        return try {
            LocalDate.parse(normalized)
        } catch (error: Exception) {
            throw IllegalArgumentException("$field must be in YYYY-MM-DD format.")
        }
    }
}
