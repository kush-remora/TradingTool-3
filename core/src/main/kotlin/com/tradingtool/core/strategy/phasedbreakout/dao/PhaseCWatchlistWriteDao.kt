package com.tradingtool.core.strategy.phasedbreakout.dao

import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.constants.DatabaseConstants.PhaseCWatchlistColumns as Cols
import com.tradingtool.core.strategy.phasedbreakout.PhaseCFreshFieldUpdate
import com.tradingtool.core.strategy.phasedbreakout.Phase2DeliveryUpdate
import com.tradingtool.core.strategy.phasedbreakout.PhaseCWatchlistRow
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlBatch

interface PhaseCWatchlistWriteDao {

    @SqlBatch(
        """
        INSERT INTO public.${Tables.PHASE_C_WATCHLIST} (
            ${Cols.SYMBOL},
            ${Cols.INSTRUMENT_TOKEN},
            ${Cols.ADDED_ON},
            ${Cols.LAST_SEEN_ON},
            ${Cols.STATUS},
            ${Cols.STOCK_NAME},
            ${Cols.MARKET_CAP_BUCKET},
            ${Cols.CLOSE_PRICE},
            ${Cols.PCT_CHANGE},
            ${Cols.VOLUME},
            ${Cols.SECTOR},
            ${Cols.INDUSTRY},
            ${Cols.ROCE_PCT},
            ${Cols.RONW_PCT},
            ${Cols.NET_PROFIT_AFTER_TAX},
            ${Cols.DEBT_EQUITY_RATIO},
            ${Cols.VOL_DRY_200D_MIN_COUNT},
            ${Cols.VOL_DRY_60D_MIN_COUNT},
            ${Cols.VOL_DRY_200D_MIN_105_COUNT},
            ${Cols.VOL_DRY_60D_MIN_105_COUNT},
            ${Cols.INDIAN_PROMOTER_PCT},
            ${Cols.FOREIGN_PROMOTER_PCT},
            ${Cols.QUARTERLY_GROSS_SALES},
            ${Cols.HIGH_52W},
            ${Cols.LOW_52W},
            ${Cols.DIST_200D_HIGH_PCT},
            ${Cols.DIST_200D_LOW_PCT},
            ${Cols.ATR_LT_2PCT_COUNT},
            ${Cols.MARKET_FIELDS_UPDATED_ON},
            ${Cols.PHASE_2_DELIVERY_STATUS},
            ${Cols.PHASE_2_REASON},
            ${Cols.PHASE_2_EVALUATED_ON},
            ${Cols.DELIVERY_QUANTITY_TODAY},
            ${Cols.DELIVERY_PCT_TODAY},
            ${Cols.WHOLESALE_BASE_DQ},
            ${Cols.DELIVERY_SPIKE_RATIO},
            ${Cols.CONVICTION_DAYS_10D},
            ${Cols.CONVICTION_DAYS_20D}
        ) VALUES (
            :symbol,
            :instrumentToken,
            :addedOn,
            :lastSeenOn,
            :status,
            :stockName,
            :marketCapBucket,
            :closePrice,
            :pctChange,
            :volume,
            :sector,
            :industry,
            :rocePct,
            :ronwPct,
            :netProfitAfterTax,
            :debtEquityRatio,
            :volDry200dMinCount,
            :volDry60dMinCount,
            :volDry200dMin105Count,
            :volDry60dMin105Count,
            :indianPromoterPct,
            :foreignPromoterPct,
            :quarterlyGrossSales,
            :high52w,
            :low52w,
            :dist200dHighPct,
            :dist200dLowPct,
            :atrLt2pctCount,
            :marketFieldsUpdatedOn,
            :phase2DeliveryStatus,
            :phase2Reason,
            :phase2EvaluatedOn,
            :deliveryQuantityToday,
            :deliveryPctToday,
            :wholesaleBaseDq,
            :deliverySpikeRatio,
            :convictionDays10d,
            :convictionDays20d
        )
        ON CONFLICT(${Cols.SYMBOL}) DO UPDATE SET
            ${Cols.INSTRUMENT_TOKEN} = EXCLUDED.${Cols.INSTRUMENT_TOKEN},
            ${Cols.LAST_SEEN_ON} = EXCLUDED.${Cols.LAST_SEEN_ON},
            ${Cols.STATUS} = 'chartinkFilter',
            ${Cols.STOCK_NAME} = EXCLUDED.${Cols.STOCK_NAME},
            ${Cols.MARKET_CAP_BUCKET} = EXCLUDED.${Cols.MARKET_CAP_BUCKET},
            ${Cols.CLOSE_PRICE} = EXCLUDED.${Cols.CLOSE_PRICE},
            ${Cols.PCT_CHANGE} = EXCLUDED.${Cols.PCT_CHANGE},
            ${Cols.VOLUME} = EXCLUDED.${Cols.VOLUME},
            ${Cols.SECTOR} = EXCLUDED.${Cols.SECTOR},
            ${Cols.INDUSTRY} = EXCLUDED.${Cols.INDUSTRY},
            ${Cols.ROCE_PCT} = EXCLUDED.${Cols.ROCE_PCT},
            ${Cols.RONW_PCT} = EXCLUDED.${Cols.RONW_PCT},
            ${Cols.NET_PROFIT_AFTER_TAX} = EXCLUDED.${Cols.NET_PROFIT_AFTER_TAX},
            ${Cols.DEBT_EQUITY_RATIO} = EXCLUDED.${Cols.DEBT_EQUITY_RATIO},
            ${Cols.VOL_DRY_200D_MIN_COUNT} = EXCLUDED.${Cols.VOL_DRY_200D_MIN_COUNT},
            ${Cols.VOL_DRY_60D_MIN_COUNT} = EXCLUDED.${Cols.VOL_DRY_60D_MIN_COUNT},
            ${Cols.VOL_DRY_200D_MIN_105_COUNT} = EXCLUDED.${Cols.VOL_DRY_200D_MIN_105_COUNT},
            ${Cols.VOL_DRY_60D_MIN_105_COUNT} = EXCLUDED.${Cols.VOL_DRY_60D_MIN_105_COUNT},
            ${Cols.INDIAN_PROMOTER_PCT} = EXCLUDED.${Cols.INDIAN_PROMOTER_PCT},
            ${Cols.FOREIGN_PROMOTER_PCT} = EXCLUDED.${Cols.FOREIGN_PROMOTER_PCT},
            ${Cols.QUARTERLY_GROSS_SALES} = EXCLUDED.${Cols.QUARTERLY_GROSS_SALES},
            ${Cols.HIGH_52W} = EXCLUDED.${Cols.HIGH_52W},
            ${Cols.LOW_52W} = EXCLUDED.${Cols.LOW_52W},
            ${Cols.DIST_200D_HIGH_PCT} = EXCLUDED.${Cols.DIST_200D_HIGH_PCT},
            ${Cols.DIST_200D_LOW_PCT} = EXCLUDED.${Cols.DIST_200D_LOW_PCT},
            ${Cols.ATR_LT_2PCT_COUNT} = EXCLUDED.${Cols.ATR_LT_2PCT_COUNT},
            ${Cols.MARKET_FIELDS_UPDATED_ON} = NULL,
            ${Cols.PHASE_2_DELIVERY_STATUS} = 'NOT_RUN',
            ${Cols.PHASE_2_REASON} = 'awaiting_delivery_validation',
            ${Cols.PHASE_2_EVALUATED_ON} = NULL,
            ${Cols.DELIVERY_QUANTITY_TODAY} = NULL,
            ${Cols.DELIVERY_PCT_TODAY} = NULL,
            ${Cols.WHOLESALE_BASE_DQ} = NULL,
            ${Cols.DELIVERY_SPIKE_RATIO} = NULL,
            ${Cols.CONVICTION_DAYS_10D} = NULL,
            ${Cols.CONVICTION_DAYS_20D} = NULL
        """
    )
    fun upsertBatch(@BindBean rows: List<PhaseCWatchlistRow>): IntArray

    @SqlBatch(
        """
        UPDATE public.${Tables.PHASE_C_WATCHLIST}
        SET ${Cols.CLOSE_PRICE} = :closePrice,
            ${Cols.PCT_CHANGE} = :pctChange,
            ${Cols.VOLUME} = :volume,
            ${Cols.HIGH_52W} = :high52w,
            ${Cols.LOW_52W} = :low52w,
            ${Cols.DIST_200D_HIGH_PCT} = :dist200dHighPct,
            ${Cols.DIST_200D_LOW_PCT} = :dist200dLowPct,
            ${Cols.MARKET_FIELDS_UPDATED_ON} = :marketFieldsUpdatedOn
        WHERE ${Cols.SYMBOL} = :symbol
        """
    )
    fun updateFreshFields(@BindBean rows: List<PhaseCFreshFieldUpdate>): IntArray

    @SqlBatch(
        """
        UPDATE public.${Tables.PHASE_C_WATCHLIST}
        SET ${Cols.PHASE_2_DELIVERY_STATUS} = :phase2DeliveryStatus,
            ${Cols.PHASE_2_REASON} = :phase2Reason,
            ${Cols.PHASE_2_EVALUATED_ON} = :phase2EvaluatedOn,
            ${Cols.DELIVERY_QUANTITY_TODAY} = :deliveryQuantityToday,
            ${Cols.DELIVERY_PCT_TODAY} = :deliveryPctToday,
            ${Cols.WHOLESALE_BASE_DQ} = :wholesaleBaseDq,
            ${Cols.DELIVERY_SPIKE_RATIO} = :deliverySpikeRatio,
            ${Cols.CONVICTION_DAYS_10D} = :convictionDays10d,
            ${Cols.CONVICTION_DAYS_20D} = :convictionDays20d
        WHERE ${Cols.SYMBOL} = :symbol
        """
    )
    fun updatePhase2Metrics(@BindBean rows: List<Phase2DeliveryUpdate>): IntArray
}
