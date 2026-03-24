package com.tradingtool.core.stock.dao

import com.tradingtool.core.constants.DatabaseConstants.StockColumns
import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.model.stock.Stock
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

@RegisterRowMapper(StockMapper::class)
interface StockWriteDao {

    @SqlQuery(
        """
        INSERT INTO public.${Tables.STOCKS}
            (${StockColumns.SYMBOL}, ${StockColumns.INSTRUMENT_TOKEN}, ${StockColumns.COMPANY_NAME},
             ${StockColumns.EXCHANGE}, ${StockColumns.NOTES}, ${StockColumns.PRIORITY}, ${StockColumns.TAGS})
        VALUES (:symbol, :instrumentToken, :companyName, :exchange, :notes, :priority, CAST(:tagsJson AS jsonb))
        RETURNING ${StockColumns.ALL_WITH_TAGS}
        """
    )
    fun create(
        @Bind("symbol") symbol: String,
        @Bind("instrumentToken") instrumentToken: Long,
        @Bind("companyName") companyName: String,
        @Bind("exchange") exchange: String,
        @Bind("notes") notes: String?,
        @Bind("priority") priority: Int?,
        @Bind("tagsJson") tagsJson: String,
    ): Stock

    @SqlQuery(
        """
        UPDATE public.${Tables.STOCKS}
        SET
            ${StockColumns.NOTES}    = CASE WHEN :setNotes    THEN :notes                      ELSE ${StockColumns.NOTES}    END,
            ${StockColumns.PRIORITY} = CASE WHEN :setPriority THEN CAST(:priority AS integer)  ELSE ${StockColumns.PRIORITY} END,
            ${StockColumns.TAGS}     = CASE WHEN :setTags     THEN CAST(:tagsJson AS jsonb)    ELSE ${StockColumns.TAGS}     END,
            ${StockColumns.UPDATED_AT} = NOW()
        WHERE ${StockColumns.ID} = :id
        RETURNING ${StockColumns.ALL_WITH_TAGS}
        """
    )
    fun update(
        @Bind("id") id: Long,
        @Bind("setNotes") setNotes: Boolean,
        @Bind("notes") notes: String?,
        @Bind("setPriority") setPriority: Boolean,
        @Bind("priority") priority: Int?,
        @Bind("setTags") setTags: Boolean,
        @Bind("tagsJson") tagsJson: String?,
    ): Stock?

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.STOCKS}
        WHERE ${StockColumns.ID} = :id
        """
    )
    fun delete(@Bind("id") id: Long): Int
}
