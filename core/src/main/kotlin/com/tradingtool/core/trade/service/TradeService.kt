package com.tradingtool.core.trade.service

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.model.trade.*
import com.tradingtool.core.trade.dao.TradeReadDao
import com.tradingtool.core.trade.dao.TradeWriteDao
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Service layer for trade journal operations.
 * Handles GTT target calculation and consolidation logic.
 * All database operations are routed through JdbiHandler.
 */
@Singleton
class TradeService @Inject constructor(
    private val db: JdbiHandler<TradeReadDao, TradeWriteDao>,
) {

    companion object {
        // Standard GTT target percentages (% above base price)
        private val GTT_TARGET_PERCENTS = listOf(2.0, 3.0, 5.0, 7.0, 10.0)
    }

    // ==================== Read Operations ====================

    /**
     * Fetch all trades with calculated GTT targets.
     */
    suspend fun getTradesWithTargets(): List<TradeWithTargets> {
        val trades = runRead { dao -> dao.getAllTrades() }
        return trades.map { trade ->
            val targets = calculateGttTargets(trade)
            val totalInvested = calculateTotalInvested(trade)
            TradeWithTargets(
                trade = trade,
                gttTargets = targets,
                totalInvested = totalInvested
            )
        }
    }

    /**
     * Fetch trade by stock ID (at most one — unique constraint on stock_id).
     */
    suspend fun getTradeByStockId(stockId: Long): com.tradingtool.core.model.trade.Trade? {
        return runRead { dao -> dao.getTradeByStockId(stockId) }
    }

    /**
     * Fetch single trade with GTT targets.
     */
    suspend fun getTradeWithTargets(tradeId: Long): TradeWithTargets? {
        val trade = runRead { dao -> dao.getTradeById(tradeId) } ?: return null
        val targets = calculateGttTargets(trade)
        val totalInvested = calculateTotalInvested(trade)
        return TradeWithTargets(
            trade = trade,
            gttTargets = targets,
            totalInvested = totalInvested
        )
    }

    // ==================== Write Operations ====================

    /**
     * Create or consolidate trade.
     * If stock already has a trade, this will:
     * 1. Merge quantities
     * 2. Calculate weighted average price
     * 3. Recalculate stop loss price
     *
     * All consolidation is handled at the DB level via UPSERT.
     */
    suspend fun createOrConsolidateTrade(input: CreateTradeInput): TradeWithTargets {
        // Calculate stop loss price from avg price and stop loss %
        val stopLossPrice = calculateStopLossPrice(
            avgBuyPrice = input.avgBuyPrice,
            stopLossPercent = input.stopLossPercent
        )

        // Upsert to DB (handles consolidation automatically)
        val trade = runWrite { dao ->
            dao.upsertTrade(
                stockId = input.stockId,
                nseSymbol = input.nseSymbol,
                quantity = input.quantity,
                avgBuyPrice = input.avgBuyPrice,
                todayLow = input.todayLow,
                stopLossPercent = input.stopLossPercent,
                stopLossPrice = stopLossPrice,
                notes = input.notes,
                tradeDate = input.tradeDate
            )
        }

        // Return enriched response with targets
        val targets = calculateGttTargets(trade)
        val totalInvested = calculateTotalInvested(trade)
        return TradeWithTargets(
            trade = trade,
            gttTargets = targets,
            totalInvested = totalInvested
        )
    }

    /**
     * Delete trade by ID.
     */
    suspend fun deleteTrade(tradeId: Long): Boolean {
        return runWrite { dao -> dao.deleteTrade(tradeId) } > 0
    }

    // ==================== Calculation Helpers ====================

    /**
     * Calculate GTT targets from a base price.
     * Base = today's low (if provided), else avg buy price.
     * Targets are: base * (1 + percent / 100)
     * Yield = ((target - avgBuyPrice) / avgBuyPrice) * 100
     */
    private fun calculateGttTargets(trade: Trade): List<GttTarget> {
        val basePrice = if (trade.todayLow != null) {
            BigDecimal(trade.todayLow)
        } else {
            BigDecimal(trade.avgBuyPrice)
        }

        val avgPrice = BigDecimal(trade.avgBuyPrice)

        return GTT_TARGET_PERCENTS.map { percent ->
            val targetPrice = basePrice * BigDecimal(1 + percent / 100)
            val yield = ((targetPrice - avgPrice) / avgPrice) * BigDecimal(100)

            GttTarget(
                percent = percent,
                price = targetPrice.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                yieldPercent = yield.setScale(2, RoundingMode.HALF_UP).toPlainString()
            )
        }
    }

    /**
     * Calculate stop loss price from avg price and stop loss %.
     * Formula: avgBuyPrice * (1 - stopLossPercent / 100)
     */
    private fun calculateStopLossPrice(
        avgBuyPrice: String,
        stopLossPercent: String
    ): String {
        val avgPrice = BigDecimal(avgBuyPrice)
        val slPercent = BigDecimal(stopLossPercent)
        val slPrice = avgPrice * (BigDecimal.ONE - slPercent / BigDecimal(100))
        return slPrice.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }

    /**
     * Calculate total amount invested in this trade.
     * Formula: quantity * avgBuyPrice
     */
    private fun calculateTotalInvested(trade: Trade): String {
        val qty = BigDecimal(trade.quantity)
        val avg = BigDecimal(trade.avgBuyPrice)
        val total = qty * avg
        return total.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }

    // ==================== Helper Methods for DB Access ====================

    private suspend fun <T> runRead(operation: (TradeReadDao) -> T): T {
        return db.read(operation)
    }

    private suspend fun <T> runWrite(operation: (TradeWriteDao) -> T): T {
        return db.write(operation)
    }
}
