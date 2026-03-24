package com.tradingtool.core.stock.dao

import com.tradingtool.core.constants.DatabaseConstants.StockIndicatorColumns
import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery

interface StockIndicatorsReadDao {

    @SqlQuery(
        "SELECT ${StockIndicatorColumns.INDICATORS_PAYLOAD}::TEXT " +
            "FROM public.${Tables.STOCK_INDICATORS_SNAPSHOT} " +
            "WHERE ${StockIndicatorColumns.INSTRUMENT_TOKEN} = :instrumentToken " +
            "LIMIT 1"
    )
    fun getIndicatorsJson(@Bind("instrumentToken") instrumentToken: Long): String?
}
