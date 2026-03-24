package com.tradingtool.core.model.trade

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

// ==================== Core Entity Models ====================

data class Trade(
    val id: Long,
    @get:JsonProperty("stock_id")
    val stockId: Long,
    @get:JsonProperty("nse_symbol")
    val nseSymbol: String,
    val quantity: Int,
    @get:JsonProperty("avg_buy_price")
    val avgBuyPrice: String, // decimal as string for precision
    @get:JsonProperty("today_low")
    val todayLow: String? = null,
    @get:JsonProperty("stop_loss_percent")
    val stopLossPercent: String,
    @get:JsonProperty("stop_loss_price")
    val stopLossPrice: String,
    val notes: String? = null,
    @get:JsonProperty("trade_date")
    val tradeDate: String, // YYYY-MM-DD format
    @get:JsonProperty("created_at")
    val createdAt: String,
    @get:JsonProperty("updated_at")
    val updatedAt: String,
)

// ==================== Input Models for Create/Consolidate Operations ====================

data class CreateTradeInput(
    @JsonProperty("stock_id")
    val stockId: Long,
    @JsonProperty("nse_symbol")
    val nseSymbol: String,
    val quantity: Int,
    @JsonProperty("avg_buy_price")
    val avgBuyPrice: String, // e.g., "3100.50"
    @JsonProperty("today_low")
    val todayLow: String? = null, // e.g., "3050.00"
    @JsonProperty("stop_loss_percent")
    val stopLossPercent: String, // e.g., "5.5"
    val notes: String? = null,
    @JsonProperty("trade_date")
    val tradeDate: String = java.time.LocalDate.now().toString(), // YYYY-MM-DD
)

// ==================== Response Models with Calculated GTT Targets ====================

data class GttTarget(
    val percent: Double, // 2.0, 3.0, 5.0, 7.0, 10.0
    val price: String, // decimal as string
    @get:JsonProperty("yield_percent")
    val yieldPercent: String, // yield % against avg buy price
)

data class TradeWithTargets(
    val trade: Trade,
    @get:JsonProperty("gtt_targets")
    val gttTargets: List<GttTarget>,
    @get:JsonProperty("total_invested")
    val totalInvested: String, // quantity × avg_buy_price
)
