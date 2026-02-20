package com.tradingtool.core.watchlist.dao

import com.tradingtool.core.model.watchlist.*
import com.tradingtool.core.constants.DatabaseConstants.StockColumns
import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.constants.DatabaseConstants.TagColumns
import com.tradingtool.core.constants.DatabaseConstants.WatchlistColumns
import com.tradingtool.core.constants.DatabaseConstants.WatchlistStockColumns
import com.tradingtool.core.constants.DatabaseConstants.StockTagColumns
import com.tradingtool.core.constants.DatabaseConstants.WatchlistTagColumns
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

/**
 * Write-only DAO for all watchlist-related INSERT/UPDATE/DELETE operations.
 * Handles stocks, watchlists, tags, and their relationships.
 */
@RegisterRowMapper(StockMapper::class)
@RegisterRowMapper(WatchlistMapper::class)
@RegisterRowMapper(TagMapper::class)
@RegisterRowMapper(WatchlistStockMapper::class)
@RegisterRowMapper(StockTagMapper::class)
@RegisterRowMapper(WatchlistTagMapper::class)
interface WatchlistWriteDao {

    // ==================== Stock Write Operations ====================

    @SqlQuery(
        """
        INSERT INTO public.${Tables.STOCKS} (${StockColumns.SYMBOL}, ${StockColumns.INSTRUMENT_TOKEN}, ${StockColumns.COMPANY_NAME}, ${StockColumns.EXCHANGE})
        VALUES (:symbol, :instrumentToken, :companyName, :exchange)
        RETURNING ${StockColumns.ALL}
        """
    )
    fun createStock(
        @Bind("symbol") symbol: String,
        @Bind("instrumentToken") instrumentToken: Long,
        @Bind("companyName") companyName: String,
        @Bind("exchange") exchange: String
    ): Stock

    @SqlQuery(
        """
        UPDATE public.${Tables.STOCKS}
        SET
            ${StockColumns.COMPANY_NAME} = CASE WHEN :setCompanyName THEN CAST(:companyName AS text) ELSE ${StockColumns.COMPANY_NAME} END,
            ${StockColumns.EXCHANGE} = CASE WHEN :setExchange THEN CAST(:exchange AS text) ELSE ${StockColumns.EXCHANGE} END,
            ${StockColumns.UPDATED_AT} = NOW()
        WHERE ${StockColumns.ID} = :stockId
        RETURNING ${StockColumns.ALL}
        """
    )
    fun updateStock(
        @Bind("stockId") stockId: Long,
        @Bind("setCompanyName") setCompanyName: Boolean,
        @Bind("companyName") companyName: String?,
        @Bind("setExchange") setExchange: Boolean,
        @Bind("exchange") exchange: String?
    ): Stock?

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.STOCKS}
        WHERE ${StockColumns.ID} = :stockId
        """
    )
    fun deleteStock(@Bind("stockId") stockId: Long): Int

    // ==================== Watchlist Write Operations ====================

    @SqlQuery(
        """
        INSERT INTO public.${Tables.WATCHLISTS} (${WatchlistColumns.NAME}, ${WatchlistColumns.DESCRIPTION})
        VALUES (:name, :description)
        RETURNING ${WatchlistColumns.ALL}
        """
    )
    fun createWatchlist(
        @Bind("name") name: String,
        @Bind("description") description: String?
    ): Watchlist

    @SqlQuery(
        """
        UPDATE public.${Tables.WATCHLISTS}
        SET
            ${WatchlistColumns.NAME} = CASE WHEN :setName THEN CAST(:name AS text) ELSE ${WatchlistColumns.NAME} END,
            ${WatchlistColumns.DESCRIPTION} = CASE WHEN :setDescription THEN CAST(:description AS text) ELSE ${WatchlistColumns.DESCRIPTION} END,
            ${WatchlistColumns.UPDATED_AT} = NOW()
        WHERE ${WatchlistColumns.ID} = :watchlistId
        RETURNING ${WatchlistColumns.ALL}
        """
    )
    fun updateWatchlist(
        @Bind("watchlistId") watchlistId: Long,
        @Bind("setName") setName: Boolean,
        @Bind("name") name: String?,
        @Bind("setDescription") setDescription: Boolean,
        @Bind("description") description: String?
    ): Watchlist?

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.WATCHLISTS}
        WHERE ${WatchlistColumns.ID} = :watchlistId
        """
    )
    fun deleteWatchlist(@Bind("watchlistId") watchlistId: Long): Int

    // ==================== Tag Write Operations ====================

    @SqlQuery(
        """
        INSERT INTO public.${Tables.TAGS} (${TagColumns.NAME})
        VALUES (:name)
        RETURNING ${TagColumns.ALL}
        """
    )
    fun createTag(@Bind("name") name: String): Tag

    @SqlQuery(
        """
        INSERT INTO public.${Tables.TAGS} (${TagColumns.NAME})
        VALUES (:name)
        ON CONFLICT (${TagColumns.NAME}) DO UPDATE SET ${TagColumns.NAME} = EXCLUDED.${TagColumns.NAME}
        RETURNING ${TagColumns.ALL}
        """
    )
    fun getOrCreateTag(@Bind("name") name: String): Tag

    @SqlQuery(
        """
        UPDATE public.${Tables.TAGS}
        SET
            ${TagColumns.NAME} = CASE WHEN :setName THEN CAST(:name AS text) ELSE ${TagColumns.NAME} END,
            ${TagColumns.UPDATED_AT} = NOW()
        WHERE ${TagColumns.ID} = :tagId
        RETURNING ${TagColumns.ALL}
        """
    )
    fun updateTag(
        @Bind("tagId") tagId: Long,
        @Bind("setName") setName: Boolean,
        @Bind("name") name: String?
    ): Tag?

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.TAGS}
        WHERE ${TagColumns.ID} = :tagId
        """
    )
    fun deleteTag(@Bind("tagId") tagId: Long): Int

    // ==================== Stock-Tag Relationship Write Operations ====================

    @SqlQuery(
        """
        INSERT INTO public.${Tables.STOCK_TAGS} (${StockTagColumns.STOCK_ID}, ${StockTagColumns.TAG_ID})
        VALUES (:stockId, :tagId)
        RETURNING ${StockTagColumns.ALL}
        """
    )
    fun createStockTag(
        @Bind("stockId") stockId: Long,
        @Bind("tagId") tagId: Long
    ): StockTag

    @SqlQuery(
        """
        INSERT INTO public.${Tables.STOCK_TAGS} (${StockTagColumns.STOCK_ID}, ${StockTagColumns.TAG_ID})
        VALUES (:stockId, :tagId)
        ON CONFLICT (${StockTagColumns.STOCK_ID}, ${StockTagColumns.TAG_ID}) DO NOTHING
        RETURNING ${StockTagColumns.ALL}
        """
    )
    fun getOrCreateStockTag(
        @Bind("stockId") stockId: Long,
        @Bind("tagId") tagId: Long
    ): StockTag?

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.STOCK_TAGS}
        WHERE ${StockTagColumns.STOCK_ID} = :stockId AND ${StockTagColumns.TAG_ID} = :tagId
        """
    )
    fun deleteStockTag(
        @Bind("stockId") stockId: Long,
        @Bind("tagId") tagId: Long
    ): Int

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.STOCK_TAGS}
        WHERE ${StockTagColumns.STOCK_ID} = :stockId
        """
    )
    fun deleteAllStockTags(@Bind("stockId") stockId: Long): Int

    // ==================== Watchlist-Tag Relationship Write Operations ====================

    @SqlQuery(
        """
        INSERT INTO public.${Tables.WATCHLIST_TAGS} (${WatchlistTagColumns.WATCHLIST_ID}, ${WatchlistTagColumns.TAG_ID})
        VALUES (:watchlistId, :tagId)
        RETURNING ${WatchlistTagColumns.ALL}
        """
    )
    fun createWatchlistTag(
        @Bind("watchlistId") watchlistId: Long,
        @Bind("tagId") tagId: Long
    ): WatchlistTag

    @SqlQuery(
        """
        INSERT INTO public.${Tables.WATCHLIST_TAGS} (${WatchlistTagColumns.WATCHLIST_ID}, ${WatchlistTagColumns.TAG_ID})
        VALUES (:watchlistId, :tagId)
        ON CONFLICT (${WatchlistTagColumns.WATCHLIST_ID}, ${WatchlistTagColumns.TAG_ID}) DO NOTHING
        RETURNING ${WatchlistTagColumns.ALL}
        """
    )
    fun getOrCreateWatchlistTag(
        @Bind("watchlistId") watchlistId: Long,
        @Bind("tagId") tagId: Long
    ): WatchlistTag?

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.WATCHLIST_TAGS}
        WHERE ${WatchlistTagColumns.WATCHLIST_ID} = :watchlistId AND ${WatchlistTagColumns.TAG_ID} = :tagId
        """
    )
    fun deleteWatchlistTag(
        @Bind("watchlistId") watchlistId: Long,
        @Bind("tagId") tagId: Long
    ): Int

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.WATCHLIST_TAGS}
        WHERE ${WatchlistTagColumns.WATCHLIST_ID} = :watchlistId
        """
    )
    fun deleteAllWatchlistTags(@Bind("watchlistId") watchlistId: Long): Int

    // ==================== Watchlist-Stock Relationship Write Operations ====================

    @SqlQuery(
        """
        INSERT INTO public.${Tables.WATCHLIST_STOCKS} (${WatchlistStockColumns.WATCHLIST_ID}, ${WatchlistStockColumns.STOCK_ID})
        VALUES (:watchlistId, :stockId)
        RETURNING ${WatchlistStockColumns.ALL}
        """
    )
    fun createWatchlistStock(
        @Bind("watchlistId") watchlistId: Long,
        @Bind("stockId") stockId: Long
    ): WatchlistStock

    @SqlQuery(
        """
        INSERT INTO public.${Tables.WATCHLIST_STOCKS} (${WatchlistStockColumns.WATCHLIST_ID}, ${WatchlistStockColumns.STOCK_ID})
        VALUES (:watchlistId, :stockId)
        ON CONFLICT (${WatchlistStockColumns.WATCHLIST_ID}, ${WatchlistStockColumns.STOCK_ID}) DO NOTHING
        RETURNING ${WatchlistStockColumns.ALL}
        """
    )
    fun getOrCreateWatchlistStock(
        @Bind("watchlistId") watchlistId: Long,
        @Bind("stockId") stockId: Long
    ): WatchlistStock?

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.WATCHLIST_STOCKS}
        WHERE ${WatchlistStockColumns.WATCHLIST_ID} = :watchlistId AND ${WatchlistStockColumns.STOCK_ID} = :stockId
        """
    )
    fun deleteWatchlistStock(
        @Bind("watchlistId") watchlistId: Long,
        @Bind("stockId") stockId: Long
    ): Int

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.WATCHLIST_STOCKS}
        WHERE ${WatchlistStockColumns.WATCHLIST_ID} = :watchlistId
        """
    )
    fun deleteAllWatchlistStocks(@Bind("watchlistId") watchlistId: Long): Int
}
