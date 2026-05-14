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
             ${StockColumns.EXCHANGE}, ${StockColumns.NOTES}, ${StockColumns.PRIORITY}, ${StockColumns.TAGS},
             ${StockColumns.WATCHLIST_LIST})
        VALUES (:symbol, :instrumentToken, :companyName, :exchange, :notes, :priority, CAST(:tagsJson AS jsonb), :watchlistList)
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
        @Bind("watchlistList") watchlistList: String,
    ): Stock

    @SqlQuery(
        """
        UPDATE public.${Tables.STOCKS}
        SET
            ${StockColumns.NOTES}    = CASE WHEN :setNotes    THEN :notes                      ELSE ${StockColumns.NOTES}    END,
            ${StockColumns.PRIORITY} = CASE WHEN :setPriority THEN CAST(:priority AS integer)  ELSE ${StockColumns.PRIORITY} END,
            ${StockColumns.TAGS}     = CASE WHEN :setTags     THEN CAST(:tagsJson AS jsonb)    ELSE ${StockColumns.TAGS}     END,
            ${StockColumns.WATCHLIST_LIST} = CASE WHEN :setWatchlistList THEN :watchlistList ELSE ${StockColumns.WATCHLIST_LIST} END,
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
        @Bind("setWatchlistList") setWatchlistList: Boolean,
        @Bind("watchlistList") watchlistList: String?,
    ): Stock?

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.STOCKS}
        WHERE ${StockColumns.ID} = :id
        """
    )
    fun delete(@Bind("id") id: Long): Int

    @SqlUpdate(
        """
        UPDATE public.${Tables.STOCKS}
        SET ${StockColumns.NEEDS_REFRESH} = :needsRefresh, ${StockColumns.UPDATED_AT} = NOW()
        WHERE ${StockColumns.INSTRUMENT_TOKEN} = :instrumentToken
        """
    )
    fun setNeedsRefresh(
        @Bind("instrumentToken") instrumentToken: Long,
        @Bind("needsRefresh") needsRefresh: Boolean
    ): Int

    @SqlUpdate(
        """
        INSERT INTO public.${Tables.STOCKS}
            (${StockColumns.SYMBOL}, ${StockColumns.INSTRUMENT_TOKEN}, ${StockColumns.COMPANY_NAME},
             ${StockColumns.EXCHANGE}, ${StockColumns.TAGS})
        VALUES (:symbol, :instrumentToken, :companyName, :exchange, CAST(:growwTagJson AS jsonb))
        ON CONFLICT (${StockColumns.SYMBOL}, ${StockColumns.EXCHANGE}) DO UPDATE SET
            ${StockColumns.INSTRUMENT_TOKEN} = EXCLUDED.${StockColumns.INSTRUMENT_TOKEN},
            ${StockColumns.COMPANY_NAME} = EXCLUDED.${StockColumns.COMPANY_NAME},
            ${StockColumns.TAGS} = CASE
                WHEN EXISTS (
                    SELECT 1
                    FROM jsonb_array_elements(public.${Tables.STOCKS}.${StockColumns.TAGS}) AS elem
                    WHERE elem->>'name' = :growwTagName
                ) THEN public.${Tables.STOCKS}.${StockColumns.TAGS}
                ELSE public.${Tables.STOCKS}.${StockColumns.TAGS} || CAST(:growwTagJson AS jsonb)
            END,
            ${StockColumns.UPDATED_AT} = NOW()
        """
    )
    fun upsertFromGrowwWatchlist(
        @Bind("symbol") symbol: String,
        @Bind("instrumentToken") instrumentToken: Long,
        @Bind("companyName") companyName: String,
        @Bind("exchange") exchange: String,
        @Bind("growwTagName") growwTagName: String,
        @Bind("growwTagJson") growwTagJson: String,
    ): Int
}
