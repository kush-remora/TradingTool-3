package com.tradingtool.core.watchlist.dao

import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.constants.DatabaseConstants.StockColumns
import com.tradingtool.core.constants.DatabaseConstants.WatchlistColumns
import com.tradingtool.core.constants.DatabaseConstants.TagColumns
import com.tradingtool.core.constants.DatabaseConstants.WatchlistStockColumns
import com.tradingtool.core.constants.DatabaseConstants.StockTagColumns
import com.tradingtool.core.constants.DatabaseConstants.WatchlistTagColumns
import com.tradingtool.core.constants.DatabaseConstants.StockNoteColumns
import com.tradingtool.core.constants.DatabaseConstants.UserLayoutColumns
import com.tradingtool.core.model.watchlist.*
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import java.time.OffsetDateTime
/**
 * Read-only DAO for all watchlist-related SELECT queries.
 * Handles stocks, watchlists, tags, and their relationships.
 */
@RegisterRowMapper(StockMapper::class)
@RegisterRowMapper(WatchlistMapper::class)
@RegisterRowMapper(TagMapper::class)
@RegisterRowMapper(WatchlistStockMapper::class)
@RegisterRowMapper(StockTagMapper::class)
@RegisterRowMapper(WatchlistTagMapper::class)
@RegisterRowMapper(StockNoteMapper::class)
@RegisterRowMapper(UserLayoutMapper::class)
interface WatchlistReadDao {

    // ==================== Stock Queries ====================

    @SqlQuery("SELECT ${StockColumns.ALL} FROM public.${Tables.STOCKS} WHERE ${StockColumns.ID} = :stockId LIMIT 1")
    fun getStockById(@Bind("stockId") stockId: Long): Stock?

    @SqlQuery("SELECT ${StockColumns.ALL} FROM public.${Tables.STOCKS} WHERE ${StockColumns.SYMBOL} = :symbol AND ${StockColumns.EXCHANGE} = :exchange LIMIT 1")
    fun getStockBySymbol(@Bind("symbol") symbol: String, @Bind("exchange") exchange: String): Stock?

    @SqlQuery("SELECT ${StockColumns.ALL} FROM public.${Tables.STOCKS} WHERE ${StockColumns.INSTRUMENT_TOKEN} = :instrumentToken LIMIT 1")
    fun getStockByInstrumentToken(@Bind("instrumentToken") instrumentToken: Long): Stock?

    @SqlQuery("SELECT ${StockColumns.ALL} FROM public.${Tables.STOCKS} ORDER BY ${StockColumns.CREATED_AT} DESC LIMIT :limit")
    fun listStocks(@Bind("limit") limit: Int): List<Stock>

    @SqlQuery(
        """
        SELECT s.${StockColumns.ID}, s.${StockColumns.SYMBOL}, s.${StockColumns.INSTRUMENT_TOKEN},
               s.${StockColumns.COMPANY_NAME}, s.${StockColumns.EXCHANGE}, s.${StockColumns.DESCRIPTION},
               s.${StockColumns.PRIORITY}, s.${StockColumns.CREATED_AT}, s.${StockColumns.UPDATED_AT}
        FROM public.${Tables.STOCKS} s
        JOIN public.${Tables.STOCK_TAGS} st ON s.${StockColumns.ID} = st.${StockTagColumns.STOCK_ID}
        JOIN public.${Tables.TAGS} t ON st.${StockTagColumns.TAG_ID} = t.${TagColumns.ID}
        WHERE t.${TagColumns.NAME} = :tagName
        ORDER BY s.${StockColumns.SYMBOL}
    """
    )
    fun getStocksByTagName(@Bind("tagName") tagName: String): List<Stock>

    // ==================== Watchlist Queries ====================

    @SqlQuery("SELECT ${WatchlistColumns.ALL} FROM public.${Tables.WATCHLISTS} WHERE ${WatchlistColumns.ID} = :watchlistId LIMIT 1")
    fun getWatchlistById(@Bind("watchlistId") watchlistId: Long): Watchlist?

    @SqlQuery("SELECT ${WatchlistColumns.ALL} FROM public.${Tables.WATCHLISTS} WHERE ${WatchlistColumns.NAME} = :name LIMIT 1")
    fun getWatchlistByName(@Bind("name") name: String): Watchlist?

    @SqlQuery("SELECT ${WatchlistColumns.ALL} FROM public.${Tables.WATCHLISTS} ORDER BY ${WatchlistColumns.CREATED_AT} DESC LIMIT :limit")
    fun listWatchlists(@Bind("limit") limit: Int): List<Watchlist>

    // ==================== Tag Queries ====================

    @SqlQuery("SELECT ${TagColumns.ALL} FROM public.${Tables.TAGS} WHERE ${TagColumns.ID} = :tagId LIMIT 1")
    fun getTagById(@Bind("tagId") tagId: Long): Tag?

    @SqlQuery("SELECT ${TagColumns.ALL} FROM public.${Tables.TAGS} WHERE ${TagColumns.NAME} = :name LIMIT 1")
    fun getTagByName(@Bind("name") name: String): Tag?

    @SqlQuery("SELECT ${TagColumns.ALL} FROM public.${Tables.TAGS} ORDER BY ${TagColumns.NAME} LIMIT :limit")
    fun listTags(@Bind("limit") limit: Int): List<Tag>

    // ==================== Stock-Tag Relationship Queries ====================

    @SqlQuery("SELECT ${StockTagColumns.ALL} FROM public.${Tables.STOCK_TAGS} WHERE ${StockTagColumns.STOCK_ID} = :stockId AND ${StockTagColumns.TAG_ID} = :tagId LIMIT 1")
    fun getStockTag(@Bind("stockId") stockId: Long, @Bind("tagId") tagId: Long): StockTag?

    @SqlQuery(
        """
        SELECT t.${TagColumns.ID}, t.${TagColumns.NAME}, t.${TagColumns.CREATED_AT}, t.${TagColumns.UPDATED_AT}
        FROM public.${Tables.TAGS} t
        JOIN public.${Tables.STOCK_TAGS} st ON t.${TagColumns.ID} = st.${StockTagColumns.TAG_ID}
        WHERE st.${StockTagColumns.STOCK_ID} = :stockId
        ORDER BY t.${TagColumns.NAME}
    """
    )
    fun getTagsForStock(@Bind("stockId") stockId: Long): List<Tag>

    @SqlQuery("SELECT ${StockTagColumns.ALL} FROM public.${Tables.STOCK_TAGS} WHERE ${StockTagColumns.STOCK_ID} = :stockId")
    fun getStockTagsForStock(@Bind("stockId") stockId: Long): List<StockTag>

    @SqlQuery("SELECT ${StockTagColumns.ALL} FROM public.${Tables.STOCK_TAGS} ORDER BY ${StockTagColumns.STOCK_ID}")
    fun getAllStockTags(): List<StockTag>

    // ==================== Watchlist-Tag Relationship Queries ====================

    @SqlQuery("SELECT ${WatchlistTagColumns.ALL} FROM public.${Tables.WATCHLIST_TAGS} WHERE ${WatchlistTagColumns.WATCHLIST_ID} = :watchlistId AND ${WatchlistTagColumns.TAG_ID} = :tagId LIMIT 1")
    fun getWatchlistTag(@Bind("watchlistId") watchlistId: Long, @Bind("tagId") tagId: Long): WatchlistTag?

    @SqlQuery(
        """
        SELECT t.${TagColumns.ID}, t.${TagColumns.NAME}, t.${TagColumns.CREATED_AT}, t.${TagColumns.UPDATED_AT}
        FROM public.${Tables.TAGS} t
        JOIN public.${Tables.WATCHLIST_TAGS} wt ON t.${TagColumns.ID} = wt.${WatchlistTagColumns.TAG_ID}
        WHERE wt.${WatchlistTagColumns.WATCHLIST_ID} = :watchlistId
        ORDER BY t.${TagColumns.NAME}
    """
    )
    fun getTagsForWatchlist(@Bind("watchlistId") watchlistId: Long): List<Tag>

    @SqlQuery("SELECT ${WatchlistTagColumns.ALL} FROM public.${Tables.WATCHLIST_TAGS} WHERE ${WatchlistTagColumns.WATCHLIST_ID} = :watchlistId")
    fun getWatchlistTagsForWatchlist(@Bind("watchlistId") watchlistId: Long): List<WatchlistTag>

    @SqlQuery("SELECT ${WatchlistTagColumns.ALL} FROM public.${Tables.WATCHLIST_TAGS} ORDER BY ${WatchlistTagColumns.WATCHLIST_ID}")
    fun getAllWatchlistTags(): List<WatchlistTag>

    // ==================== Watchlist-Stock Relationship Queries ====================

    @SqlQuery("SELECT ${WatchlistStockColumns.ALL} FROM public.${Tables.WATCHLIST_STOCKS} WHERE ${WatchlistStockColumns.WATCHLIST_ID} = :watchlistId AND ${WatchlistStockColumns.STOCK_ID} = :stockId LIMIT 1")
    fun getWatchlistStock(@Bind("watchlistId") watchlistId: Long, @Bind("stockId") stockId: Long): WatchlistStock?

    @SqlQuery(
        """
        SELECT s.${StockColumns.ID}, s.${StockColumns.SYMBOL}, s.${StockColumns.INSTRUMENT_TOKEN},
               s.${StockColumns.COMPANY_NAME}, s.${StockColumns.EXCHANGE}, s.${StockColumns.DESCRIPTION},
               s.${StockColumns.PRIORITY}, s.${StockColumns.CREATED_AT}, s.${StockColumns.UPDATED_AT}
        FROM public.${Tables.STOCKS} s
        JOIN public.${Tables.WATCHLIST_STOCKS} ws ON s.${StockColumns.ID} = ws.${WatchlistStockColumns.STOCK_ID}
        WHERE ws.${WatchlistStockColumns.WATCHLIST_ID} = :watchlistId
        ORDER BY ws.${WatchlistStockColumns.CREATED_AT} DESC
    """
    )
    fun getStocksInWatchlist(@Bind("watchlistId") watchlistId: Long): List<Stock>

    @SqlQuery("SELECT ${WatchlistStockColumns.ALL} FROM public.${Tables.WATCHLIST_STOCKS} WHERE ${WatchlistStockColumns.WATCHLIST_ID} = :watchlistId ORDER BY ${WatchlistStockColumns.CREATED_AT} DESC")
    fun getWatchlistStocksForWatchlist(@Bind("watchlistId") watchlistId: Long): List<WatchlistStock>

    // ==================== Stock Notes Queries ====================

    @SqlQuery("SELECT ${StockNoteColumns.ALL} FROM public.${Tables.STOCK_NOTES} WHERE ${StockNoteColumns.STOCK_ID} = :stockId ORDER BY ${StockNoteColumns.CREATED_AT} ASC")
    fun getNotesForStock(@Bind("stockId") stockId: Long): List<StockNote>

    // ==================== User Layout Queries ====================

    @SqlQuery("SELECT ${UserLayoutColumns.ID}, ${UserLayoutColumns.LAYOUT_DATA}::text AS ${UserLayoutColumns.LAYOUT_DATA}, ${UserLayoutColumns.UPDATED_AT} FROM public.${Tables.USER_LAYOUT} WHERE ${UserLayoutColumns.ID} = 1 LIMIT 1")
    fun getLayout(): UserLayout?
}

// ==================== Row Mappers ====================

class StockMapper : RowMapper<Stock> {
    override fun map(rs: ResultSet, ctx: StatementContext): Stock {
        return Stock(
            id = rs.getLong(StockColumns.ID),
            symbol = rs.getString(StockColumns.SYMBOL),
            instrumentToken = rs.getLong(StockColumns.INSTRUMENT_TOKEN),
            companyName = rs.getString(StockColumns.COMPANY_NAME),
            exchange = rs.getString(StockColumns.EXCHANGE),
            description = rs.getString(StockColumns.DESCRIPTION),
            priority = rs.getObject(StockColumns.PRIORITY, Int::class.javaObjectType),
            createdAt = toUtcString(rs.getObject(StockColumns.CREATED_AT, OffsetDateTime::class.java)),
            updatedAt = toUtcString(rs.getObject(StockColumns.UPDATED_AT, OffsetDateTime::class.java)),
        )
    }

    private fun toUtcString(value: OffsetDateTime?): String {
        return value?.toInstant()?.toString()
            ?: throw IllegalStateException("Unexpected null timestamp in database row")
    }
}

class WatchlistMapper : RowMapper<Watchlist> {
    override fun map(rs: ResultSet, ctx: StatementContext): Watchlist {
        return Watchlist(
            id = rs.getLong(WatchlistColumns.ID),
            name = rs.getString(WatchlistColumns.NAME),
            description = rs.getString(WatchlistColumns.DESCRIPTION),
            createdAt = toUtcString(rs.getObject(WatchlistColumns.CREATED_AT, OffsetDateTime::class.java)),
            updatedAt = toUtcString(rs.getObject(WatchlistColumns.UPDATED_AT, OffsetDateTime::class.java)),
        )
    }

    private fun toUtcString(value: OffsetDateTime?): String {
        return value?.toInstant()?.toString()
            ?: throw IllegalStateException("Unexpected null timestamp in database row")
    }
}

class TagMapper : RowMapper<Tag> {
    override fun map(rs: ResultSet, ctx: StatementContext): Tag {
        return Tag(
            id = rs.getLong(TagColumns.ID),
            name = rs.getString(TagColumns.NAME),
            createdAt = toUtcString(rs.getObject(TagColumns.CREATED_AT, OffsetDateTime::class.java)),
            updatedAt = toUtcString(rs.getObject(TagColumns.UPDATED_AT, OffsetDateTime::class.java)),
        )
    }

    private fun toUtcString(value: OffsetDateTime?): String {
        return value?.toInstant()?.toString()
            ?: throw IllegalStateException("Unexpected null timestamp in database row")
    }
}

class WatchlistStockMapper : RowMapper<WatchlistStock> {
    override fun map(rs: ResultSet, ctx: StatementContext): WatchlistStock {
        return WatchlistStock(
            id = rs.getLong(WatchlistStockColumns.ID),
            watchlistId = rs.getLong(WatchlistStockColumns.WATCHLIST_ID),
            stockId = rs.getLong(WatchlistStockColumns.STOCK_ID),
            createdAt = toUtcString(rs.getObject(WatchlistStockColumns.CREATED_AT, OffsetDateTime::class.java)),
        )
    }

    private fun toUtcString(value: OffsetDateTime?): String {
        return value?.toInstant()?.toString()
            ?: throw IllegalStateException("Unexpected null timestamp in database row")
    }
}

class StockTagMapper : RowMapper<StockTag> {
    override fun map(rs: ResultSet, ctx: StatementContext): StockTag {
        return StockTag(
            id = rs.getLong(StockTagColumns.ID),
            stockId = rs.getLong(StockTagColumns.STOCK_ID),
            tagId = rs.getLong(StockTagColumns.TAG_ID),
            createdAt = toUtcString(rs.getObject(StockTagColumns.CREATED_AT, OffsetDateTime::class.java)),
        )
    }

    private fun toUtcString(value: OffsetDateTime?): String {
        return value?.toInstant()?.toString()
            ?: throw IllegalStateException("Unexpected null timestamp in database row")
    }
}

class WatchlistTagMapper : RowMapper<WatchlistTag> {
    override fun map(rs: ResultSet, ctx: StatementContext): WatchlistTag {
        return WatchlistTag(
            id = rs.getLong(WatchlistTagColumns.ID),
            watchlistId = rs.getLong(WatchlistTagColumns.WATCHLIST_ID),
            tagId = rs.getLong(WatchlistTagColumns.TAG_ID),
            createdAt = toUtcString(rs.getObject(WatchlistTagColumns.CREATED_AT, OffsetDateTime::class.java)),
        )
    }

    private fun toUtcString(value: OffsetDateTime?): String {
        return value?.toInstant()?.toString()
            ?: throw IllegalStateException("Unexpected null timestamp in database row")
    }
}

class StockNoteMapper : RowMapper<StockNote> {
    override fun map(rs: ResultSet, ctx: StatementContext): StockNote {
        return StockNote(
            id = rs.getLong(StockNoteColumns.ID),
            stockId = rs.getLong(StockNoteColumns.STOCK_ID),
            content = rs.getString(StockNoteColumns.CONTENT),
            createdAt = toUtcString(rs.getObject(StockNoteColumns.CREATED_AT, OffsetDateTime::class.java)),
        )
    }

    private fun toUtcString(value: OffsetDateTime?): String {
        return value?.toInstant()?.toString()
            ?: throw IllegalStateException("Unexpected null timestamp in database row")
    }
}

class UserLayoutMapper : RowMapper<UserLayout> {
    override fun map(rs: ResultSet, ctx: StatementContext): UserLayout {
        return UserLayout(
            id = rs.getInt(UserLayoutColumns.ID),
            layoutData = rs.getString(UserLayoutColumns.LAYOUT_DATA) ?: "{}",
            updatedAt = toUtcString(rs.getObject(UserLayoutColumns.UPDATED_AT, OffsetDateTime::class.java)),
        )
    }

    private fun toUtcString(value: OffsetDateTime?): String {
        return value?.toInstant()?.toString()
            ?: throw IllegalStateException("Unexpected null timestamp in database row")
    }
}
