package com.tradingtool.core.delivery.validation

import com.tradingtool.core.delivery.model.DeliverySourceRow
import com.tradingtool.core.delivery.model.DeliverySourceType
import kotlin.math.abs
import kotlin.math.floor

object DeliverySourceValidationAnalyzer {
    private const val MIN_TARGET_COVERAGE_RATIO: Double = 0.95
    private const val MAX_MISMATCH_RATIO: Double = 0.01
    private const val MATERIAL_DELIVERY_PERCENT_DIFF: Double = 0.05
    private const val MATERIAL_COVERAGE_GAP: Double = 0.02

    fun summarize(
        source: DeliverySourceType,
        descriptor: DeliveryFileDescriptor?,
        rows: List<DeliverySourceRow>?,
        targetSymbols: Set<String>,
        error: String? = null,
    ): DeliverySourceAssessment {
        if (descriptor == null) {
            return DeliverySourceAssessment(
                source = source,
                available = false,
                downloadSucceeded = false,
                parseSucceeded = false,
                targetSymbolCount = targetSymbols.size,
                error = error ?: "Source file not present in discovery response.",
            )
        }

        if (rows == null) {
            return DeliverySourceAssessment(
                source = source,
                available = true,
                downloadSucceeded = false,
                parseSucceeded = false,
                fileName = descriptor.fileName,
                fileUrl = descriptor.url,
                targetSymbolCount = targetSymbols.size,
                error = error ?: "Download or parsing failed.",
            )
        }

        val bySymbol = rows.associateBy { row -> row.symbol.uppercase() }
        val matchedTargetSymbols = targetSymbols.filter { symbol -> bySymbol.containsKey(symbol) }
        val missingTargetSymbols = targetSymbols.filterNot { symbol -> bySymbol.containsKey(symbol) }
        val coverageRatio = if (targetSymbols.isEmpty()) 0.0 else matchedTargetSymbols.size.toDouble() / targetSymbols.size.toDouble()

        return DeliverySourceAssessment(
            source = source,
            available = true,
            downloadSucceeded = true,
            parseSucceeded = true,
            fileName = descriptor.fileName,
            fileUrl = descriptor.url,
            rowCount = rows.size,
            targetSymbolCount = targetSymbols.size,
            matchedTargetCount = matchedTargetSymbols.size,
            missingTargetSymbols = missingTargetSymbols.sorted(),
            coverageRatio = coverageRatio,
            error = error,
        )
    }

    fun compare(
        bhavRows: List<DeliverySourceRow>?,
        mtoRows: List<DeliverySourceRow>?,
        targetSymbols: Set<String>,
    ): DeliveryComparisonSummary {
        if (bhavRows == null || mtoRows == null) {
            return DeliveryComparisonSummary()
        }

        val bhavBySymbol = bhavRows.associateBy { row -> row.symbol.uppercase() }
        val mtoBySymbol = mtoRows.associateBy { row -> row.symbol.uppercase() }
        val targetBhavSymbols = targetSymbols.filter { symbol -> bhavBySymbol.containsKey(symbol) }.toSet()
        val targetMtoSymbols = targetSymbols.filter { symbol -> mtoBySymbol.containsKey(symbol) }.toSet()
        val comparedSymbols = targetBhavSymbols.intersect(targetMtoSymbols).sorted()

        val mismatches = comparedSymbols.mapNotNull { symbol ->
            val bhav = bhavBySymbol[symbol] ?: return@mapNotNull null
            val mto = mtoBySymbol[symbol] ?: return@mapNotNull null
            val absoluteDifference = abs(bhav.deliveryPercent - mto.deliveryPercent)
            if (absoluteDifference < MATERIAL_DELIVERY_PERCENT_DIFF) {
                null
            } else {
                DeliveryMismatch(
                    symbol = symbol,
                    bhavDeliveryPercent = bhav.deliveryPercent,
                    mtoDeliveryPercent = mto.deliveryPercent,
                    absoluteDifference = absoluteDifference,
                )
            }
        }.sortedByDescending { mismatch -> mismatch.absoluteDifference }

        val maxMismatchCount = floor(comparedSymbols.size * MAX_MISMATCH_RATIO).toInt()
        return DeliveryComparisonSummary(
            comparedSymbolCount = comparedSymbols.size,
            bhavOnlySymbols = (targetBhavSymbols - targetMtoSymbols).sorted(),
            mtoOnlySymbols = (targetMtoSymbols - targetBhavSymbols).sorted(),
            materialMismatches = mismatches,
            materiallyConsistent = comparedSymbols.isNotEmpty() && mismatches.size <= maxMismatchCount,
        )
    }

    fun recommend(
        bhavAssessment: DeliverySourceAssessment,
        mtoAssessment: DeliverySourceAssessment,
        comparison: DeliveryComparisonSummary,
    ): DeliveryRecommendation {
        val bhavPassesCoverage = bhavAssessment.parseSucceeded && bhavAssessment.coverageRatio >= MIN_TARGET_COVERAGE_RATIO
        val mtoPassesCoverage = mtoAssessment.parseSucceeded && mtoAssessment.coverageRatio >= MIN_TARGET_COVERAGE_RATIO

        if (bhavPassesCoverage) {
            val materiallyWeakerThanMto = mtoPassesCoverage &&
                bhavAssessment.coverageRatio + MATERIAL_COVERAGE_GAP < mtoAssessment.coverageRatio
            val comparisonIsAcceptable = !mtoAssessment.parseSucceeded || comparison.materiallyConsistent
            if (!materiallyWeakerThanMto && comparisonIsAcceptable) {
                return DeliveryRecommendation.BHAVDATA_FULL
            }
        }

        if (mtoPassesCoverage) {
            return DeliveryRecommendation.MTO
        }

        return DeliveryRecommendation.PIVOT_REQUIRED
    }
}
