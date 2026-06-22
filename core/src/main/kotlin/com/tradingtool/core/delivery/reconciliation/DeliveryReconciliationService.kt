package com.tradingtool.core.delivery.reconciliation

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.delivery.config.DeliveryDataSource
import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.DeliverySourceRow
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.delivery.source.DeliverySourceUnavailableException
import com.tradingtool.core.delivery.source.NseDeliverySourceAdapter
import com.tradingtool.core.delivery.validation.DeliveryFileDescriptor
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.InstrumentTokenResolution
import com.tradingtool.core.kite.InstrumentTokenResolverService
import com.tradingtool.core.kite.KiteConnectClient
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId

@Singleton
class DeliveryReconciliationService @Inject constructor(
    private val deliveryHandler: StockDeliveryJdbiHandler,
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val instrumentCache: InstrumentCache,
    private val kiteClient: KiteConnectClient,
    private val sourceAdapter: NseDeliverySourceAdapter,
) {
    private val log = LoggerFactory.getLogger(DeliveryReconciliationService::class.java)
    private val tokenResolver = InstrumentTokenResolverService(kiteClient, instrumentCache)

    suspend fun latestAvailableTradingDate(): LocalDate? {
        return sourceAdapter.discoverDeliveryReports()?.resolvedTradingDate
    }

    suspend fun isDateReconciled(tradingDate: LocalDate): Boolean {
        val existingRows = deliveryHandler.read { dao ->
            dao.findAllByTradingDate(tradingDate)
        }
        return existingRows.isNotEmpty()
    }

    suspend fun reconcileLatestAvailableDate(): DeliveryDateReconciliationResult? {
        val latestTradingDate = latestAvailableTradingDate() ?: return null
        return reconcileDate(latestTradingDate)
    }

    suspend fun findConfiguredRowsForDate(tradingDate: LocalDate): List<StockDeliveryDaily> {
        return deliveryHandler.read { dao ->
            dao.findAllByTradingDate(tradingDate)
        }
    }

    suspend fun reconcileDate(tradingDate: LocalDate): DeliveryDateReconciliationResult {
        val existingRows = deliveryHandler.read { dao ->
            dao.findAllByTradingDate(tradingDate)
        }
        if (existingRows.isNotEmpty()) {
            return DeliveryDateReconciliationResult(
                tradingDate = tradingDate,
                expectedCount = existingRows.size,
                alreadyComplete = true,
                fetchedFromSource = false,
                presentCount = existingRows.count { row -> row.reconciliationStatus == DeliveryReconciliationStatus.PRESENT },
                missingFromSourceCount = 0,
            )
        }

        val sourcePayload = resolveSourcePayload(tradingDate)
        val sourceRowsBySymbol = when {
            sourcePayload.rows.isEmpty() -> DeliverySourceRowsBySymbol(
                tradingDate = tradingDate,
                rowsBySymbol = emptyMap(),
            )
            else -> DeliveryReconciliationAnalyzer.selectBestRowsBySymbol(sourcePayload.rows)
        }

        val sourceSymbols = sourceRowsBySymbol.rowsBySymbol.keys.toList()

        val unresolvedResolutions = mutableListOf<UnresolvedDeliverySymbol>()
        val resolvedPairs = sourceRowsBySymbol.rowsBySymbol.values.mapNotNull { sourceRow ->
            val resolution = resolveInstrumentToken(sourceRow)
            val resolvedToken = resolution.resolvedToken
            if (resolvedToken == null) {
                unresolvedResolutions += UnresolvedDeliverySymbol(
                    symbol = sourceRow.symbol.uppercase(),
                    companyName = null,
                    resolution = resolution,
                )
                null
            } else {
                sourceRow to resolvedToken
            }
        }
        val resolvedTokens = resolvedPairs.map { (_, token) -> token }.distinct()
        val universeByToken = loadUniverseByInstrumentToken(resolvedTokens)
        val upserts = resolvedPairs.map { (sourceRow, token) ->
            DeliveryReconciliationUpsert(
                instrumentToken = token,
                symbol = sourceRow.symbol.uppercase(),
                exchange = NSE_EXCHANGE,
                universe = universeByToken[token] ?: UNKNOWN_UNIVERSE,
                tradingDate = sourceRow.tradingDate,
                reconciliationStatus = DeliveryReconciliationStatus.PRESENT,
                series = sourceRow.series,
                ttlTrdQnty = sourceRow.tradedQuantity,
                delivQty = sourceRow.deliverableQuantity,
                delivPer = sourceRow.deliveryPercent,
                sourceFileName = sourcePayload.descriptor.fileName,
                sourceUrl = sourcePayload.descriptor.url,
            )
        }

        deliveryHandler.write { dao ->
            upserts.forEach { row ->
                dao.upsert(
                    instrumentToken = row.instrumentToken,
                    symbol = row.symbol,
                    exchange = row.exchange,
                    universe = row.universe,
                    tradingDate = row.tradingDate,
                    reconciliationStatus = row.reconciliationStatus.name,
                    series = row.series,
                    ttlTrdQnty = row.ttlTrdQnty,
                    delivQty = row.delivQty,
                    delivPer = row.delivPer,
                    sourceFileName = row.sourceFileName,
                    sourceUrl = row.sourceUrl,
                )
            }
        }

        log.info(
            "Reconciled delivery date {} with source={} for {} symbols (unresolved={})",
            tradingDate,
            sourcePayload.source.name,
            upserts.size,
            0,
        )

        return DeliveryDateReconciliationResult(
            tradingDate = tradingDate,
            expectedCount = sourceRowsBySymbol.rowsBySymbol.size,
            alreadyComplete = false,
            fetchedFromSource = true,
            presentCount = upserts.size,
            missingFromSourceCount = 0,
            unresolvedSymbols = unresolvedResolutions.map { unresolved -> unresolved.symbol },
        )
    }

    private suspend fun resolveSourcePayload(tradingDate: LocalDate): ResolvedSourcePayload {
        val preferredSources = selectSourcePriority(tradingDate)

        preferredSources.forEach { source ->
            val descriptor = resolveSourceDescriptorForSource(tradingDate, source) ?: return@forEach
            val rows = runCatching { fetchSourceRows(source, descriptor) }
                .onFailure { error ->
                    log.warn("Failed to fetch {} source for {}: {}", source.name, tradingDate, error.message)
                }
                .getOrNull() ?: return@forEach

            return ResolvedSourcePayload(
                source = source,
                descriptor = descriptor,
                rows = rows,
            )
        }

        throw DeliverySourceUnavailableException("No delivery source available for trading date $tradingDate")
    }

    private suspend fun resolveSourceDescriptorForSource(
        tradingDate: LocalDate,
        source: DeliveryDataSource,
    ): DeliveryFileDescriptor? {
        val discovery = sourceAdapter.discoverDeliveryReports(tradingDate)
        if (discovery != null) {
            val descriptor = when (source) {
                DeliveryDataSource.CM_BHAVDATA_FULL -> discovery.bhavDataFull
                DeliveryDataSource.MTO -> discovery.mto
            }
            if (descriptor != null) {
                return descriptor
            }
        }

        return when (source) {
            DeliveryDataSource.CM_BHAVDATA_FULL -> {
                log.info("Falling back to NSE archive descriptor for {}", tradingDate)
                sourceAdapter.buildArchiveDescriptor(tradingDate, source)
            }
            DeliveryDataSource.MTO -> null
        }
    }

    private suspend fun fetchSourceRows(
        source: DeliveryDataSource,
        descriptor: DeliveryFileDescriptor,
    ) =
        when (source) {
            DeliveryDataSource.CM_BHAVDATA_FULL -> sourceAdapter.fetchBhavDataRows(descriptor)
            DeliveryDataSource.MTO -> sourceAdapter.fetchMtoRows(descriptor)
        }

    private fun selectSourcePriority(tradingDate: LocalDate): List<DeliveryDataSource> {
        val today = LocalDate.now(MARKET_ZONE)
        return if (tradingDate >= today.minusDays(1)) {
            listOf(DeliveryDataSource.MTO, DeliveryDataSource.CM_BHAVDATA_FULL)
        } else {
            listOf(DeliveryDataSource.CM_BHAVDATA_FULL, DeliveryDataSource.MTO)
        }
    }

    private suspend fun resolveInstrumentToken(sourceRow: DeliverySourceRow): InstrumentTokenResolution {
        return tokenResolver.resolveDetailed(NSE_EXCHANGE, sourceRow.symbol)
    }

    private fun buildUnresolvedTokenMessage(unresolvedResolutions: List<UnresolvedDeliverySymbol>): String {
        val details = unresolvedResolutions
            .take(MAX_UNRESOLVED_SYMBOLS_IN_MESSAGE)
            .joinToString(separator = "; ") { unresolved ->
                formatUnresolvedDeliverySymbol(unresolved)
            }
        return "DeliveryReconciliationJob unresolved instrument tokens: count=${unresolvedResolutions.size}; $details"
    }

    private suspend fun loadUniverseByInstrumentToken(tokens: List<Long>): Map<Long, String> {
        if (tokens.isEmpty()) {
            return emptyMap()
        }

        return indexConstituentHandler.read { dao ->
            dao.findUniverseByInstrumentTokens(tokens)
        }.associate { row -> row.instrumentToken to row.universe }
    }

    private companion object {
        const val NSE_EXCHANGE: String = "NSE"
        const val UNKNOWN_UNIVERSE: String = "UNKNOWN"
        const val MAX_UNRESOLVED_SYMBOLS_IN_MESSAGE: Int = 30
        val MARKET_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    }
}

internal data class UnresolvedDeliverySymbol(
    val symbol: String,
    val companyName: String?,
    val resolution: InstrumentTokenResolution,
)

internal fun formatUnresolvedDeliverySymbol(unresolved: UnresolvedDeliverySymbol): String {
    val displayName = unresolved.companyName?.takeIf { it.isNotBlank() } ?: unresolved.symbol
    val candidates = if (unresolved.resolution.candidateKeys.isEmpty()) {
        "none"
    } else {
        unresolved.resolution.candidateKeys.joinToString(" | ")
    }
    return "${unresolved.symbol} [$displayName] (candidates=$candidates)"
}

private data class ResolvedSourcePayload(
    val source: DeliveryDataSource,
    val descriptor: DeliveryFileDescriptor,
    val rows: List<DeliverySourceRow>,
)
