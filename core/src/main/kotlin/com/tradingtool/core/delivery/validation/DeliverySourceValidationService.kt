package com.tradingtool.core.delivery.validation

import com.tradingtool.core.delivery.config.DeliveryUniverseService
import com.tradingtool.core.delivery.model.DeliverySourceRow
import com.tradingtool.core.delivery.model.DeliverySourceType
import com.tradingtool.core.delivery.source.NseDeliverySourceAdapter
import org.slf4j.LoggerFactory
import java.time.LocalDate

class DeliverySourceValidationService(
    private val deliveryUniverseService: DeliveryUniverseService,
    private val sourceAdapter: NseDeliverySourceAdapter,
) {
    private val log = LoggerFactory.getLogger(DeliverySourceValidationService::class.java)

    suspend fun validate(requestedDate: LocalDate? = null): DeliverySourceValidationReport {
        val targetSymbols = loadTargetSymbols()
        val discovery = sourceAdapter.discoverDeliveryReports(requestedDate)
            ?: return buildDiscoveryFailureReport(requestedDate, targetSymbols, "Unable to resolve delivery report files from NSE daily-reports API.")

        val bhavRows = loadSourceRows(discovery.bhavDataFull, DeliverySourceType.BHAVDATA_FULL)
        val mtoRows = loadSourceRows(discovery.mto, DeliverySourceType.MTO)

        val bhavAssessment = DeliverySourceValidationAnalyzer.summarize(
            source = DeliverySourceType.BHAVDATA_FULL,
            descriptor = discovery.bhavDataFull,
            rows = bhavRows.rows,
            targetSymbols = targetSymbols,
            error = bhavRows.error,
        )
        val mtoAssessment = DeliverySourceValidationAnalyzer.summarize(
            source = DeliverySourceType.MTO,
            descriptor = discovery.mto,
            rows = mtoRows.rows,
            targetSymbols = targetSymbols,
            error = mtoRows.error,
        )
        val comparison = DeliverySourceValidationAnalyzer.compare(bhavRows.rows, mtoRows.rows, targetSymbols)
        val recommendation = DeliverySourceValidationAnalyzer.recommend(
            bhavAssessment = bhavAssessment,
            mtoAssessment = mtoAssessment,
            comparison = comparison,
        )

        return DeliverySourceValidationReport(
            requestedDate = requestedDate,
            resolvedTradingDate = discovery.resolvedTradingDate,
            discoveryWorked = true,
            discoveryBucket = discovery.bucket,
            targetSymbols = targetSymbols.sorted(),
            bhavDataFull = bhavAssessment,
            mto = mtoAssessment,
            comparison = comparison,
            recommendedSource = recommendation,
            verdict = buildVerdict(recommendation),
        )
    }

    private suspend fun loadTargetSymbols(): Set<String> {
        return deliveryUniverseService.resolveTargetSymbols()
    }

    private suspend fun loadSourceRows(
        descriptor: DeliveryFileDescriptor?,
        source: DeliverySourceType,
    ): SourceRowsResult {
        if (descriptor == null) {
            return SourceRowsResult(rows = null, error = "Source file missing in discovery response.")
        }

        return try {
            val rows = when (source) {
                DeliverySourceType.BHAVDATA_FULL -> sourceAdapter.fetchBhavDataRows(descriptor)
                DeliverySourceType.MTO -> sourceAdapter.fetchMtoRows(descriptor)
            }
            SourceRowsResult(rows = rows, error = null)
        } catch (error: Exception) {
            log.warn("Delivery source load failed for {}: {}", source, error.message)
            SourceRowsResult(rows = null, error = error.message ?: "Unknown source load failure.")
        }
    }

    private fun buildDiscoveryFailureReport(
        requestedDate: LocalDate?,
        targetSymbols: Set<String>,
        message: String,
    ): DeliverySourceValidationReport {
        return DeliverySourceValidationReport(
            requestedDate = requestedDate,
            discoveryWorked = false,
            discoveryError = message,
            targetSymbols = targetSymbols.sorted(),
            bhavDataFull = DeliverySourceAssessment(
                source = DeliverySourceType.BHAVDATA_FULL,
                available = false,
                downloadSucceeded = false,
                parseSucceeded = false,
                targetSymbolCount = targetSymbols.size,
                error = message,
            ),
            mto = DeliverySourceAssessment(
                source = DeliverySourceType.MTO,
                available = false,
                downloadSucceeded = false,
                parseSucceeded = false,
                targetSymbolCount = targetSymbols.size,
                error = message,
            ),
            recommendedSource = DeliveryRecommendation.PIVOT_REQUIRED,
            verdict = buildVerdict(DeliveryRecommendation.PIVOT_REQUIRED),
        )
    }

    private fun buildVerdict(recommendation: DeliveryRecommendation): String {
        return when (recommendation) {
            DeliveryRecommendation.BHAVDATA_FULL -> "Proceed with CM-BHAVDATA-FULL as the primary delivery source."
            DeliveryRecommendation.MTO -> "Proceed with MTO as the fallback delivery source."
            DeliveryRecommendation.PIVOT_REQUIRED -> "Pivot required: neither NSE delivery source passed validation for the target universe."
        }
    }

    private data class SourceRowsResult(
        val rows: List<DeliverySourceRow>?,
        val error: String?,
    )
}
