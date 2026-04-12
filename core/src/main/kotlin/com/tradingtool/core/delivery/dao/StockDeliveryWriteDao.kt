package com.tradingtool.core.delivery.dao

import com.tradingtool.core.constants.DatabaseConstants.StockDeliveryColumns as Cols
import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.time.LocalDate

interface StockDeliveryWriteDao {

    @SqlUpdate(
        """
        INSERT INTO ${Tables.STOCK_DELIVERY_DAILY} (
            ${Cols.STOCK_ID}, ${Cols.INSTRUMENT_TOKEN}, ${Cols.SYMBOL}, ${Cols.EXCHANGE}, ${Cols.TRADING_DATE},
            ${Cols.RECONCILIATION_STATUS},
            ${Cols.SERIES}, ${Cols.TTL_TRD_QNTY}, ${Cols.DELIV_QTY}, ${Cols.DELIV_PER}, 
            ${Cols.SOURCE_FILE_NAME}, ${Cols.SOURCE_URL}
        ) VALUES (
            :stockId, :instrumentToken, :symbol, :exchange, :tradingDate,
            :reconciliationStatus,
            :series, :ttlTrdQnty, :delivQty, :delivPer, 
            :sourceFileName, :sourceUrl
        ) ON CONFLICT (${Cols.INSTRUMENT_TOKEN}, ${Cols.TRADING_DATE}) DO UPDATE SET
            ${Cols.STOCK_ID} = EXCLUDED.${Cols.STOCK_ID},
            ${Cols.SYMBOL} = EXCLUDED.${Cols.SYMBOL},
            ${Cols.EXCHANGE} = EXCLUDED.${Cols.EXCHANGE},
            ${Cols.RECONCILIATION_STATUS} = EXCLUDED.${Cols.RECONCILIATION_STATUS},
            ${Cols.SERIES} = EXCLUDED.${Cols.SERIES},
            ${Cols.TTL_TRD_QNTY} = EXCLUDED.${Cols.TTL_TRD_QNTY},
            ${Cols.DELIV_QTY} = EXCLUDED.${Cols.DELIV_QTY},
            ${Cols.DELIV_PER} = EXCLUDED.${Cols.DELIV_PER},
            ${Cols.SOURCE_FILE_NAME} = EXCLUDED.${Cols.SOURCE_FILE_NAME},
            ${Cols.SOURCE_URL} = EXCLUDED.${Cols.SOURCE_URL},
            ${Cols.FETCHED_AT} = CURRENT_TIMESTAMP
        """
    )
    fun upsert(
        @Bind("stockId") stockId: Long?,
        @Bind("instrumentToken") instrumentToken: Long,
        @Bind("symbol") symbol: String,
        @Bind("exchange") exchange: String,
        @Bind("tradingDate") tradingDate: LocalDate,
        @Bind("reconciliationStatus") reconciliationStatus: String,
        @Bind("series") series: String?,
        @Bind("ttlTrdQnty") ttlTrdQnty: Long?,
        @Bind("delivQty") delivQty: Long?,
        @Bind("delivPer") delivPer: Double?,
        @Bind("sourceFileName") sourceFileName: String?,
        @Bind("sourceUrl") sourceUrl: String?
    ): Int
}
