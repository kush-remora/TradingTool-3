package com.tradingtool.core.fundamentals.screener

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.http.Result
import kotlinx.coroutines.delay

@Singleton
class ScreenerFundamentalsValidationService @Inject constructor(
    private val sourceAdapter: ScreenerFundamentalsSourceAdapter,
) {
    suspend fun validate(symbols: List<String>): ScreenerFundamentalsValidationReport {
        val normalizedSymbols = symbols.map { symbol -> symbol.trim().uppercase() }
            .filter { symbol -> symbol.isNotEmpty() }
            .distinct()

        val rows = mutableListOf<ScreenerFundamentalsValidationRow>()
        normalizedSymbols.forEachIndexed { index, symbol ->
            rows += validateSymbol(symbol)
            if (index != normalizedSymbols.lastIndex) {
                delay(1_200)
            }
        }

        return ScreenerFundamentalsValidationReport(
            requestedSymbols = normalizedSymbols,
            testedCount = rows.size,
            reachableCount = rows.count { row -> row.reachable },
            parsedCount = rows.count { row -> row.parsed },
            marketCapCount = rows.count { row -> row.snapshot?.marketCapCr != null },
            stockPeCount = rows.count { row -> row.snapshot?.stockPe != null },
            roceCount = rows.count { row -> row.snapshot?.rocePercent != null },
            roeCount = rows.count { row -> row.snapshot?.roePercent != null },
            promoterHoldingCount = rows.count { row -> row.snapshot?.promoterHoldingPercent != null },
            broadIndustryCount = rows.count { row -> row.snapshot?.broadIndustry != null },
            industryCount = rows.count { row -> row.snapshot?.industry != null },
            debtToEquityCount = rows.count { row -> row.snapshot?.debtToEquity != null },
            pledgedPercentCount = rows.count { row -> row.snapshot?.pledgedPercent != null },
            rows = rows,
        )
    }

    private suspend fun validateSymbol(symbol: String): ScreenerFundamentalsValidationRow {
        return when (val response = sourceAdapter.fetchCompanyPage(symbol)) {
            is Result.Success -> {
                runCatching {
                    val snapshot = ScreenerFundamentalsParser.parse(symbol, response.data)
                    ScreenerFundamentalsValidationRow(
                        symbol = symbol,
                        reachable = true,
                        parsed = true,
                        snapshot = snapshot,
                        error = null,
                    )
                }.getOrElse { error ->
                    ScreenerFundamentalsValidationRow(
                        symbol = symbol,
                        reachable = true,
                        parsed = false,
                        snapshot = null,
                        error = error.message ?: "Parse failed",
                    )
                }
            }
            is Result.Failure -> {
                ScreenerFundamentalsValidationRow(
                    symbol = symbol,
                    reachable = false,
                    parsed = false,
                    snapshot = null,
                    error = response.error.describe(),
                )
            }
        }
    }
}
