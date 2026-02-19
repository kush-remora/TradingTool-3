package com.tradingtool.core.watchlist.dao

import com.tradingtool.core.model.watchlist.WatchlistStockRecord
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.sql.ResultSet
import java.time.OffsetDateTime

@RegisterRowMapper(WatchlistStockRecordMapper::class)
interface WatchlistTextQueryDao {
    @SqlQuery(
        """
        INSERT INTO $WATCHLIST_STOCKS_TABLE (
            watchlist_id,
            stock_id,
            notes
        ) VALUES (
            :watchlistId,
            :stockId,
            :notes
        )
        RETURNING
            $WATCHLIST_STOCKS_SELECT_COLUMNS
        """,
    )
    fun createWatchlistStock(
        @Bind("watchlistId") watchlistId: Long,
        @Bind("stockId") stockId: Long,
        @Bind("notes") notes: String?,
    ): WatchlistStockRecord

    @SqlQuery(
        """
        SELECT
            $WATCHLIST_STOCKS_SELECT_COLUMNS
        FROM $WATCHLIST_STOCKS_TABLE
        WHERE watchlist_id = :watchlistId
          AND stock_id = :stockId
        LIMIT 1
        """,
    )
    fun getWatchlistStock(
        @Bind("watchlistId") watchlistId: Long,
        @Bind("stockId") stockId: Long,
    ): WatchlistStockRecord?

    @SqlQuery(
        """
        SELECT
            $WATCHLIST_STOCKS_SELECT_COLUMNS
        FROM $WATCHLIST_STOCKS_TABLE
        WHERE watchlist_id = :watchlistId
        ORDER BY created_at DESC
        """,
    )
    fun listStocksForWatchlist(@Bind("watchlistId") watchlistId: Long): List<WatchlistStockRecord>

    @SqlQuery(
        """
        UPDATE $WATCHLIST_STOCKS_TABLE
        SET
            notes = CASE WHEN :setNotes THEN CAST(:notes AS text) ELSE notes END
        WHERE watchlist_id = :watchlistId
          AND stock_id = :stockId
        RETURNING
            $WATCHLIST_STOCKS_SELECT_COLUMNS
        """,
    )
    fun updateWatchlistStock(
        @Bind("watchlistId") watchlistId: Long,
        @Bind("stockId") stockId: Long,
        @Bind("setNotes") setNotes: Boolean,
        @Bind("notes") notes: String?,
    ): WatchlistStockRecord?

    @SqlUpdate(
        """
        DELETE FROM $WATCHLIST_STOCKS_TABLE
        WHERE watchlist_id = :watchlistId
          AND stock_id = :stockId
        """,
    )
    fun deleteWatchlistStock(
        @Bind("watchlistId") watchlistId: Long,
        @Bind("stockId") stockId: Long,
    ): Int

    private companion object {
        const val WATCHLIST_STOCKS_TABLE: String = "watchlist_stocks"
        const val WATCHLIST_STOCKS_SELECT_COLUMNS: String = """
            watchlist_id,
            stock_id,
            notes,
            created_at
        """
    }
}

class WatchlistStockRecordMapper : RowMapper<WatchlistStockRecord> {
    override fun map(rs: ResultSet, ctx: StatementContext): WatchlistStockRecord {
        return WatchlistStockRecord(
            watchlistId = rs.getLong("watchlist_id"),
            stockId = rs.getLong("stock_id"),
            notes = rs.getString("notes"),
            createdAt = toUtcString(rs.getObject("created_at", OffsetDateTime::class.java)),
        )
    }

    private fun toUtcString(value: OffsetDateTime?): String {
        return value?.toInstant()?.toString()
            ?: throw IllegalStateException("Unexpected null timestamp in database row")
    }
}
