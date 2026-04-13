package com.tradingtool.core.fundamentals.dao

import com.tradingtool.core.constants.DatabaseConstants.StockFundamentalsColumns as Cols
import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.time.LocalDate

interface StockFundamentalsWriteDao {

    @SqlUpdate(
        """
        INSERT INTO ${Tables.STOCK_FUNDAMENTALS_DAILY} (
            ${Cols.STOCK_ID}, ${Cols.INSTRUMENT_TOKEN}, ${Cols.SYMBOL}, ${Cols.EXCHANGE}, ${Cols.UNIVERSE},
            ${Cols.SNAPSHOT_DATE}, ${Cols.COMPANY_NAME}, ${Cols.MARKET_CAP_CR}, ${Cols.STOCK_PE},
            ${Cols.ROCE_PERCENT}, ${Cols.ROE_PERCENT}, ${Cols.PROMOTER_HOLDING_PERCENT},
            ${Cols.BROAD_INDUSTRY}, ${Cols.INDUSTRY}, ${Cols.SOURCE_NAME}, ${Cols.SOURCE_URL}
        ) VALUES (
            :stockId, :instrumentToken, :symbol, :exchange, :universe,
            :snapshotDate, :companyName, :marketCapCr, :stockPe,
            :rocePercent, :roePercent, :promoterHoldingPercent,
            :broadIndustry, :industry, :sourceName, :sourceUrl
        ) ON CONFLICT (${Cols.INSTRUMENT_TOKEN}, ${Cols.SNAPSHOT_DATE}) DO UPDATE SET
            ${Cols.STOCK_ID} = EXCLUDED.${Cols.STOCK_ID},
            ${Cols.SYMBOL} = EXCLUDED.${Cols.SYMBOL},
            ${Cols.EXCHANGE} = EXCLUDED.${Cols.EXCHANGE},
            ${Cols.UNIVERSE} = EXCLUDED.${Cols.UNIVERSE},
            ${Cols.COMPANY_NAME} = EXCLUDED.${Cols.COMPANY_NAME},
            ${Cols.MARKET_CAP_CR} = EXCLUDED.${Cols.MARKET_CAP_CR},
            ${Cols.STOCK_PE} = EXCLUDED.${Cols.STOCK_PE},
            ${Cols.ROCE_PERCENT} = EXCLUDED.${Cols.ROCE_PERCENT},
            ${Cols.ROE_PERCENT} = EXCLUDED.${Cols.ROE_PERCENT},
            ${Cols.PROMOTER_HOLDING_PERCENT} = EXCLUDED.${Cols.PROMOTER_HOLDING_PERCENT},
            ${Cols.BROAD_INDUSTRY} = EXCLUDED.${Cols.BROAD_INDUSTRY},
            ${Cols.INDUSTRY} = EXCLUDED.${Cols.INDUSTRY},
            ${Cols.SOURCE_NAME} = EXCLUDED.${Cols.SOURCE_NAME},
            ${Cols.SOURCE_URL} = EXCLUDED.${Cols.SOURCE_URL},
            ${Cols.FETCHED_AT} = CURRENT_TIMESTAMP
        """
    )
    fun upsert(
        @Bind("stockId") stockId: Long?,
        @Bind("instrumentToken") instrumentToken: Long,
        @Bind("symbol") symbol: String,
        @Bind("exchange") exchange: String,
        @Bind("universe") universe: String,
        @Bind("snapshotDate") snapshotDate: LocalDate,
        @Bind("companyName") companyName: String,
        @Bind("marketCapCr") marketCapCr: Double?,
        @Bind("stockPe") stockPe: Double?,
        @Bind("rocePercent") rocePercent: Double?,
        @Bind("roePercent") roePercent: Double?,
        @Bind("promoterHoldingPercent") promoterHoldingPercent: Double?,
        @Bind("broadIndustry") broadIndustry: String?,
        @Bind("industry") industry: String?,
        @Bind("sourceName") sourceName: String,
        @Bind("sourceUrl") sourceUrl: String,
    ): Int
}
