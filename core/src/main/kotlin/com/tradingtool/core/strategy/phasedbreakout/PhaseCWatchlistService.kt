package com.tradingtool.core.strategy.phasedbreakout

import com.tradingtool.core.kite.InstrumentTokenResolverService
import java.time.LocalDate

class PhaseCWatchlistService(
    private val jdbiHandler: PhaseCWatchlistJdbiHandler,
    private val instrumentTokenResolver: InstrumentTokenResolverService
) {
    suspend fun uploadChartinkCsv(request: PhaseCWatchlistUploadRequest): PhaseCWatchlistUploadResponse {
        val today = LocalDate.now()
        
        // Resolve tokens serially or concurrently
        val rows = request.rows.map { dto ->
            // Try to resolve instrument token
            val token = instrumentTokenResolver.resolve("NSE", dto.symbol)

            PhaseCWatchlistRow(
                symbol = dto.symbol,
                instrumentToken = token,
                addedOn = today,
                lastSeenOn = today,
                status = "chartinkFilter",
                stockName = dto.stockName,
                marketCapBucket = dto.marketCapBucket,
                closePrice = dto.closePrice,
                pctChange = dto.pctChange,
                volume = dto.volume,
                sector = dto.sector,
                industry = dto.industry,
                rocePct = dto.rocePct,
                ronwPct = dto.ronwPct,
                netProfitAfterTax = dto.netProfitAfterTax,
                debtEquityRatio = dto.debtEquityRatio,
                volDry200dMinCount = dto.volDry200dMinCount,
                volDry60dMinCount = dto.volDry60dMinCount,
                volDry200dMin105Count = dto.volDry200dMin105Count,
                volDry60dMin105Count = dto.volDry60dMin105Count,
                indianPromoterPct = dto.indianPromoterPct,
                foreignPromoterPct = dto.foreignPromoterPct,
                quarterlyGrossSales = dto.quarterlyGrossSales,
                high52w = dto.high52w,
                low52w = dto.low52w,
                dist200dHighPct = dto.dist200dHighPct,
                dist200dLowPct = dto.dist200dLowPct,
                atrLt2pctCount = dto.atrLt2pctCount
            )
        }

        if (rows.isEmpty()) {
            return PhaseCWatchlistUploadResponse(0, 0)
        }

        // Upsert the batch
        val results = jdbiHandler.transaction { _, writeDao ->
            writeDao.upsertBatch(rows)
        }

        val insertedOrUpdated = results.count { it > 0 }
        
        return PhaseCWatchlistUploadResponse(
            insertedCount = insertedOrUpdated, // Not splitting explicitly since ON CONFLICT doesn't always specify insert vs update cleanly
            updatedCount = 0
        )
    }

    suspend fun getAllWatchlist(): List<PhaseCWatchlistRow> {
        return jdbiHandler.transaction { readDao, _ ->
            readDao.findAll()
        }
    }
}
