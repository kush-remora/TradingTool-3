package com.tradingtool.core.strategy.phasedbreakout.dao

import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.constants.DatabaseConstants.PhaseCWatchlistColumns as Cols
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
            ${Cols.ATR_LT_2PCT_COUNT}
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
            :atrLt2pctCount
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
            ${Cols.ATR_LT_2PCT_COUNT} = EXCLUDED.${Cols.ATR_LT_2PCT_COUNT}
        """
    )
    fun upsertBatch(@BindBean rows: List<PhaseCWatchlistRow>): IntArray
}
