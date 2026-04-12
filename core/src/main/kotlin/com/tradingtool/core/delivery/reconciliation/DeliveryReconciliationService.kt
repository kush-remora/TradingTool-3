package com.tradingtool.core.delivery.reconciliation

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.delivery.config.DeliveryConfigService
import com.tradingtool.core.delivery.config.DeliveryDataSource
import com.tradingtool.core.delivery.config.DeliveryUniverseService
import com.tradingtool.core.delivery.source.NseDeliverySourceAdapter
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.delivery.validation.DeliveryFileDescriptor
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDate

@Singleton
class DeliveryReconciliationService @Inject constructor(
    private val configService: DeliveryConfigService,
    private val deliveryUniverseService: DeliveryUniverseService,
    private val stockHandler: StockJdbiHandler,
    private val deliveryHandler: StockDeliveryJdbiHandler,
    private val instrumentCache: InstrumentCache,
    private val kiteClient: KiteConnectClient,
    private val sourceAdapter: NseDeliverySourceAdapter,
) {
    private val log = LoggerFactory.getLogger(DeliveryReconciliationService::class.java)

    suspend fun latestAvailableTradingDate(): LocalDate? {
        return sourceAdapter.discoverDeliveryReports()?.resolvedTradingDate
    }

    suspend fun isDateReconciled(tradingDate: LocalDate): Boolean {
        val expectationState = resolveExpectations()
        if (expectationState.totalSymbolCount == 0) {
            return true
        }
        if (expectationState.expectations.isEmpty()) {
            return false
        }

        val existingRows = deliveryHandler.read { dao ->
            dao.findByTradingDateAndInstrumentTokens(
                tradingDate = tradingDate,
                instrumentTokens = expectationState.expectations.map { expectation -> expectation.instrumentToken },
            )
        }

        return DeliveryReconciliationAnalyzer.isDateComplete(
            expectedInstrumentTokens = expectationState.expectations.map { expectation -> expectation.instrumentToken }.toSet(),
            existingRows = existingRows,
        )
    }

    suspend fun reconcileLatestAvailableDate(): DeliveryDateReconciliationResult? {
        val latestTradingDate = latestAvailableTradingDate() ?: return null
        return reconcileDate(latestTradingDate)
    }

    suspend fun findConfiguredRowsForDate(tradingDate: LocalDate): List<StockDeliveryDaily> {
        val expectationState = resolveExpectations()
        if (expectationState.expectations.isEmpty()) {
            return emptyList()
        }

        return deliveryHandler.read { dao ->
            dao.findByTradingDateAndInstrumentTokens(
                tradingDate = tradingDate,
                instrumentTokens = expectationState.expectations.map { expectation -> expectation.instrumentToken },
            )
        }
    }

    suspend fun reconcileDate(tradingDate: LocalDate): DeliveryDateReconciliationResult {
        val expectationState = resolveExpectations()
        if (expectationState.totalSymbolCount == 0) {
            return DeliveryDateReconciliationResult(
                tradingDate = tradingDate,
                expectedCount = 0,
                alreadyComplete = true,
                fetchedFromSource = false,
                presentCount = 0,
                missingFromSourceCount = 0,
            )
        }
        if (expectationState.expectations.isEmpty()) {
            return DeliveryDateReconciliationResult(
                tradingDate = tradingDate,
                expectedCount = expectationState.totalSymbolCount,
                alreadyComplete = true,
                fetchedFromSource = false,
                presentCount = 0,
                missingFromSourceCount = 0,
                unresolvedSymbols = expectationState.unresolvedSymbols,
            )
        }

        val expectedTokens = expectationState.expectations.map { expectation -> expectation.instrumentToken }
        val existingRows = deliveryHandler.read { dao ->
            dao.findByTradingDateAndInstrumentTokens(
                tradingDate = tradingDate,
                instrumentTokens = expectedTokens,
            )
        }

        if (DeliveryReconciliationAnalyzer.isDateComplete(expectedTokens.toSet(), existingRows)) {
            return DeliveryDateReconciliationResult(
                tradingDate = tradingDate,
                expectedCount = expectationState.totalSymbolCount,
                alreadyComplete = true,
                fetchedFromSource = false,
                presentCount = existingRows.count { row -> row.reconciliationStatus.name == "PRESENT" },
                missingFromSourceCount = existingRows.count { row -> row.reconciliationStatus.name == "MISSING_FROM_SOURCE" },
                unresolvedSymbols = expectationState.unresolvedSymbols,
            )
        }

        val descriptor = resolveSourceDescriptor(tradingDate)
        val sourceRows = fetchSourceRows(descriptor)
        val sourceRowsBySymbol = if (sourceRows.isEmpty()) {
            DeliverySourceRowsBySymbol(
                tradingDate = tradingDate,
                rowsBySymbol = emptyMap(),
            )
        } else {
            DeliveryReconciliationAnalyzer.selectBestRowsBySymbol(sourceRows)
        }
        val upserts = DeliveryReconciliationAnalyzer.buildUpserts(
            expectations = expectationState.expectations,
            sourceRows = sourceRowsBySymbol,
            sourceFileName = descriptor.fileName,
            sourceUrl = descriptor.url,
        )

        deliveryHandler.write { dao ->
            upserts.forEach { row ->
                dao.upsert(
                    stockId = row.stockId,
                    instrumentToken = row.instrumentToken,
                    symbol = row.symbol,
                    exchange = row.exchange,
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
            "Reconciled delivery date {} for {} symbols (present={}, missingFromSource={})",
            tradingDate,
            upserts.size,
            upserts.count { row -> row.reconciliationStatus.name == "PRESENT" },
            upserts.count { row -> row.reconciliationStatus.name == "MISSING_FROM_SOURCE" },
        )

        return DeliveryDateReconciliationResult(
            tradingDate = tradingDate,
            expectedCount = expectationState.totalSymbolCount,
            alreadyComplete = false,
            fetchedFromSource = true,
            presentCount = upserts.count { row -> row.reconciliationStatus.name == "PRESENT" },
            missingFromSourceCount = upserts.count { row -> row.reconciliationStatus.name == "MISSING_FROM_SOURCE" },
            unresolvedSymbols = expectationState.unresolvedSymbols,
        )
    }

    private suspend fun resolveExpectations(): DeliveryExpectationState {
        ensureInstrumentCacheLoaded()
        val symbols = deliveryUniverseService.resolveTargetSymbols().toList()
        if (symbols.isEmpty()) {
            return DeliveryExpectationState(
                totalSymbolCount = 0,
                expectations = emptyList(),
                unresolvedSymbols = emptyList(),
            )
        }

        val trackedStocks = stockHandler.read { dao ->
            dao.listBySymbols(symbols, NSE_EXCHANGE)
        }.associateBy { stock -> stock.symbol.uppercase() }

        val expectations = mutableListOf<DeliveryExpectation>()
        val unresolvedSymbols = mutableListOf<String>()

        symbols.forEach { symbol ->
            val instrumentToken = instrumentCache.token(NSE_EXCHANGE, symbol)
            if (instrumentToken == null) {
                unresolvedSymbols += symbol
            } else {
                expectations += DeliveryExpectation(
                    stockId = trackedStocks[symbol]?.id,
                    instrumentToken = instrumentToken,
                    symbol = symbol,
                    exchange = NSE_EXCHANGE,
                )
            }
        }

        return DeliveryExpectationState(
            totalSymbolCount = symbols.size,
            expectations = expectations,
            unresolvedSymbols = unresolvedSymbols,
        )
    }

    private suspend fun ensureInstrumentCacheLoaded() {
        if (!instrumentCache.isEmpty()) {
            return
        }

        val instruments = withContext(Dispatchers.IO) {
            kiteClient.client().getInstruments(NSE_EXCHANGE)
        }
        instrumentCache.refresh(instruments)
    }

    private suspend fun resolveSourceDescriptor(tradingDate: LocalDate): DeliveryFileDescriptor {
        val discovery = sourceAdapter.discoverDeliveryReports(tradingDate)
            ?: error("No NSE delivery report available for $tradingDate")

        return when (configService.loadConfig().source) {
            DeliveryDataSource.CM_BHAVDATA_FULL -> discovery.bhavDataFull
                ?: error("CM-BHAVDATA-FULL report missing for $tradingDate")
            DeliveryDataSource.MTO -> discovery.mto
                ?: error("MTO report missing for $tradingDate")
        }
    }

    private suspend fun fetchSourceRows(descriptor: DeliveryFileDescriptor) =
        when (configService.loadConfig().source) {
            DeliveryDataSource.CM_BHAVDATA_FULL -> sourceAdapter.fetchBhavDataRows(descriptor)
            DeliveryDataSource.MTO -> sourceAdapter.fetchMtoRows(descriptor)
        }

    private companion object {
        const val NSE_EXCHANGE: String = "NSE"
    }
}

private data class DeliveryExpectationState(
    val totalSymbolCount: Int,
    val expectations: List<DeliveryExpectation>,
    val unresolvedSymbols: List<String>,
)
