package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.strategy.csvbacktest.dao.BacktestTradeReviewReadDao
import com.tradingtool.core.strategy.csvbacktest.dao.BacktestTradeReviewWriteDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class BacktestTradeReviewService(
    private val reviewHandler: JdbiHandler<BacktestTradeReviewReadDao, BacktestTradeReviewWriteDao>
) {
    suspend fun getAllReviews(): BacktestTradeReviewApiResponse = withContext(Dispatchers.IO) {
        val reviews = reviewHandler.read { it.getAllReviews() }
        BacktestTradeReviewApiResponse(reviews)
    }

    suspend fun upsertReview(request: BacktestTradeReviewApiRequest) = withContext(Dispatchers.IO) {
        val review = BacktestTradeReview(
            symbol = request.symbol,
            signalDate = LocalDate.parse(request.signalDate),
            marketCap = request.marketCap,
            sector = request.sector,
            entryDate = request.entryDate?.let { LocalDate.parse(it) },
            entryPrice = request.entryPrice,
            exitDate = request.exitDate?.let { LocalDate.parse(it) },
            exitPrice = request.exitPrice,
            pnlPct = request.pnlPct,
            daysHeld = request.daysHeld,
            slHit = request.slHit,
            isPass = request.isPass,
            reasonTags = request.reasonTags,
            notes = request.notes
        )
        reviewHandler.write { it.upsertReview(review) }
    }
}
