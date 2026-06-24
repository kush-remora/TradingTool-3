package com.tradingtool.core.strategy.phasedbreakout

import org.jdbi.v3.core.mapper.reflect.ColumnName
import java.time.LocalDate

data class PhaseCWatchlistRow(
    @ColumnName("symbol") val symbol: String,
    @ColumnName("instrument_token") val instrumentToken: Long?,
    @ColumnName("added_on") val addedOn: LocalDate,
    @ColumnName("last_seen_on") val lastSeenOn: LocalDate,
    @ColumnName("status") val status: String, // 'chartinkFilter', 'BREAKOUT_TRIGGERED', 'EXPIRED'
    @ColumnName("stock_name") val stockName: String?,
    @ColumnName("market_cap_bucket") val marketCapBucket: String?,
    @ColumnName("close_price") val closePrice: Double?,
    @ColumnName("pct_change") val pctChange: String?,
    @ColumnName("volume") val volume: Long?,
    @ColumnName("sector") val sector: String?,
    @ColumnName("industry") val industry: String?,
    @ColumnName("roce_pct") val rocePct: Double?,
    @ColumnName("ronw_pct") val ronwPct: Double?,
    @ColumnName("net_profit_after_tax") val netProfitAfterTax: Double?,
    @ColumnName("debt_equity_ratio") val debtEquityRatio: Double?,
    @ColumnName("vol_dry_200d_min_count") val volDry200dMinCount: Int?,
    @ColumnName("vol_dry_60d_min_count") val volDry60dMinCount: Int?,
    @ColumnName("vol_dry_200d_min_105_count") val volDry200dMin105Count: Int?,
    @ColumnName("vol_dry_60d_min_105_count") val volDry60dMin105Count: Int?,
    @ColumnName("indian_promoter_pct") val indianPromoterPct: Double?,
    @ColumnName("foreign_promoter_pct") val foreignPromoterPct: Double?,
    @ColumnName("quarterly_gross_sales") val quarterlyGrossSales: Double?,
    @ColumnName("high_52w") val high52w: Double?,
    @ColumnName("low_52w") val low52w: Double?,
    @ColumnName("dist_200d_high_pct") val dist200dHighPct: Double?,
    @ColumnName("dist_200d_low_pct") val dist200dLowPct: Double?,
    @ColumnName("atr_lt_2pct_count") val atrLt2pctCount: Int?
)
