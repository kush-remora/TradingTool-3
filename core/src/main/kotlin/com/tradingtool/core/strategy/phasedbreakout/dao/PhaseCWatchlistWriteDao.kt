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
            ${Cols.MARKETCAPNAME},
            ${Cols.CLOSE_PRICE},
            ${Cols.PCT_CHANGE},
            ${Cols.VOLUME},
            ${Cols.SECTOR},
            ${Cols.INDUSTRY},
            ${Cols.ROCE},
            ${Cols.RONW},
            ${Cols.NET_PROFIT_3Q_AGO},
            ${Cols.DEBT_EQUITY},
            ${Cols.VOL_DRY_200_MIN},
            ${Cols.VOL_DRY_60_MIN},
            ${Cols.VOL_DRY_200_MIN_1_05},
            ${Cols.VOL_DRY_60_MIN_1_05},
            ${Cols.ATR_COUNT}
        ) VALUES (
            :symbol,
            :instrumentToken,
            :addedOn,
            :lastSeenOn,
            :status,
            :stockName,
            :marketcapname,
            :closePrice,
            :pctChange,
            :volume,
            :sector,
            :industry,
            :roce,
            :ronw,
            :netProfit3qAgo,
            :debtEquity,
            :volDry200Min,
            :volDry60Min,
            :volDry200Min105,
            :volDry60Min105,
            :atrCount
        )
        ON CONFLICT(${Cols.SYMBOL}) DO UPDATE SET
            ${Cols.INSTRUMENT_TOKEN} = EXCLUDED.${Cols.INSTRUMENT_TOKEN},
            ${Cols.LAST_SEEN_ON} = EXCLUDED.${Cols.LAST_SEEN_ON},
            ${Cols.STATUS} = 'chartinkFilter',
            ${Cols.STOCK_NAME} = EXCLUDED.${Cols.STOCK_NAME},
            ${Cols.MARKETCAPNAME} = EXCLUDED.${Cols.MARKETCAPNAME},
            ${Cols.CLOSE_PRICE} = EXCLUDED.${Cols.CLOSE_PRICE},
            ${Cols.PCT_CHANGE} = EXCLUDED.${Cols.PCT_CHANGE},
            ${Cols.VOLUME} = EXCLUDED.${Cols.VOLUME},
            ${Cols.SECTOR} = EXCLUDED.${Cols.SECTOR},
            ${Cols.INDUSTRY} = EXCLUDED.${Cols.INDUSTRY},
            ${Cols.ROCE} = EXCLUDED.${Cols.ROCE},
            ${Cols.RONW} = EXCLUDED.${Cols.RONW},
            ${Cols.NET_PROFIT_3Q_AGO} = EXCLUDED.${Cols.NET_PROFIT_3Q_AGO},
            ${Cols.DEBT_EQUITY} = EXCLUDED.${Cols.DEBT_EQUITY},
            ${Cols.VOL_DRY_200_MIN} = EXCLUDED.${Cols.VOL_DRY_200_MIN},
            ${Cols.VOL_DRY_60_MIN} = EXCLUDED.${Cols.VOL_DRY_60_MIN},
            ${Cols.VOL_DRY_200_MIN_1_05} = EXCLUDED.${Cols.VOL_DRY_200_MIN_1_05},
            ${Cols.VOL_DRY_60_MIN_1_05} = EXCLUDED.${Cols.VOL_DRY_60_MIN_1_05},
            ${Cols.ATR_COUNT} = EXCLUDED.${Cols.ATR_COUNT}
        """
    )
    fun upsertBatch(@BindBean rows: List<PhaseCWatchlistRow>): IntArray
}
