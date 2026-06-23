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
                marketcapname = dto.marketcapname,
                closePrice = dto.closePrice,
                pctChange = dto.pctChange,
                volume = dto.volume,
                sector = dto.sector,
                industry = dto.industry,
                roce = dto.roce,
                ronw = dto.ronw,
                netProfit3qAgo = dto.netProfit3qAgo,
                debtEquity = dto.debtEquity,
                volDry200Min = dto.volDry200Min,
                volDry60Min = dto.volDry60Min,
                volDry200Min105 = dto.volDry200Min105,
                volDry60Min105 = dto.volDry60Min105,
                atrCount = dto.atrCount
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
