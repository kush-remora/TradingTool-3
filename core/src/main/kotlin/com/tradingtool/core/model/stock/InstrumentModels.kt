package com.tradingtool.core.model.stock

import com.fasterxml.jackson.annotation.JsonProperty

data class InstrumentSearchResult(
    @get:JsonProperty("instrument_token")
    val instrumentToken: Long,
    @get:JsonProperty("trading_symbol")
    val tradingSymbol: String,
    @get:JsonProperty("company_name")
    val companyName: String,
    val exchange: String,
    @get:JsonProperty("instrument_type")
    val instrumentType: String,
)

data class StockQuoteSnapshot(
    val symbol: String,
    val ltp: Double? = null,
    @get:JsonProperty("average_price")
    val averagePrice: Double? = null,
    @get:JsonProperty("change_percent")
    val changePercent: Double? = null,
    @get:JsonProperty("day_open")
    val dayOpen: Double? = null,
    @get:JsonProperty("day_high")
    val dayHigh: Double? = null,
    @get:JsonProperty("day_low")
    val dayLow: Double? = null,
    val volume: Long? = null,
    @get:JsonProperty("previous_day_volume")
    val previousDayVolume: Long? = null,
    @get:JsonProperty("updated_at")
    val updatedAt: String,
)
