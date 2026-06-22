package com.tradingtool.core.volumeshocker.groww

class GrowwVolumeShockerIngestionService(
    private val source: GrowwVolumeShockerSource,
    private val instrumentTokenResolver: GrowwVolumeShockerInstrumentTokenResolver,
    private val gateway: GrowwVolumeShockerGateway,
) {

    suspend fun ingest(request: GrowwVolumeShockerIngestionRequest): GrowwVolumeShockerIngestionResult {
        val sourceRows = source.fetchRows()
        validateSourceRows(sourceRows)

        val resolvedRows = sourceRows.map { sourceRow ->
            val instrumentKey = "${sourceRow.exchange}:${sourceRow.symbol}"
            val instrumentToken = instrumentTokenResolver.resolve(
                exchange = sourceRow.exchange,
                symbol = sourceRow.symbol,
            )?.takeIf { token -> token > 0L }
                ?: error(
                    "Instrument token could not be resolved for $instrumentKey at rank ${sourceRow.sourceRank}.",
                )

            GrowwVolumeShockerDailyRow(
                tradeDate = request.tradeDate,
                sourceRank = sourceRow.sourceRank,
                exchange = sourceRow.exchange,
                symbol = sourceRow.symbol,
                instrumentToken = instrumentToken,
                companyName = sourceRow.companyName,
                ltp = sourceRow.ltp,
                close = sourceRow.close,
                marketCapCrore = sourceRow.marketCapCrore,
                marketCapCategory = MarketCapCategory.fromMarketCap(sourceRow.marketCapCrore),
                yearLow = sourceRow.yearLow,
                yearHigh = sourceRow.yearHigh,
                volume = sourceRow.volume,
                weeklyAverageVolume = sourceRow.weeklyAverageVolume,
            )
        }

        require(resolvedRows.distinctBy { row -> row.instrumentToken }.size == EXPECTED_ROW_COUNT) {
            "Groww volume-shocker input resolves multiple rows to the same instrument token."
        }

        val storedCount = gateway.replace(request.tradeDate, resolvedRows)
        check(storedCount == EXPECTED_ROW_COUNT) {
            "Expected to store $EXPECTED_ROW_COUNT Groww volume-shocker rows, but stored $storedCount."
        }

        return GrowwVolumeShockerIngestionResult(
            fetchedCount = sourceRows.size,
            storedCount = storedCount,
        )
    }

    private fun validateSourceRows(rows: List<GrowwVolumeShockerSourceRow>) {
        require(rows.size == EXPECTED_ROW_COUNT) {
            "Groww volume-shocker input must contain exactly $EXPECTED_ROW_COUNT rows; found ${rows.size}."
        }

        val expectedRanks = (1..EXPECTED_ROW_COUNT).toSet()
        require(rows.map { row -> row.sourceRank }.toSet() == expectedRanks) {
            "Groww volume-shocker ranks must be unique and cover 1 through $EXPECTED_ROW_COUNT."
        }

        val uniqueSymbols = rows.distinctBy { row -> row.exchange to row.symbol }
        require(uniqueSymbols.size == EXPECTED_ROW_COUNT) {
            "Groww volume-shocker input contains duplicate exchange-symbol rows."
        }
    }

    private companion object {
        const val EXPECTED_ROW_COUNT = 100
    }
}
