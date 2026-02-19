package com.tradingtool.core.watchlist.dal.sql

import com.tradingtool.core.model.watchlist.WatchlistRecord
import com.tradingtool.core.watchlist.dal.mapper.WatchlistRecordMapper
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

@RegisterRowMapper(WatchlistRecordMapper::class)
interface WatchlistSqlObject {
    @SqlQuery(
        """
        INSERT INTO $WATCHLIST_TABLE (
            name,
            description
        ) VALUES (
            :name,
            :description
        )
        RETURNING
            $WATCHLIST_SELECT_COLUMNS
        """,
    )
    fun createWatchlist(
        @Bind("name") name: String,
        @Bind("description") description: String?,
    ): WatchlistRecord

    @SqlQuery(
        """
        SELECT
            $WATCHLIST_SELECT_COLUMNS
        FROM $WATCHLIST_TABLE
        WHERE id = :watchlistId
        LIMIT 1
        """,
    )
    fun getWatchlistById(@Bind("watchlistId") watchlistId: Long): WatchlistRecord?

    @SqlQuery(
        """
        SELECT
            $WATCHLIST_SELECT_COLUMNS
        FROM $WATCHLIST_TABLE
        WHERE name = :name
        LIMIT 1
        """,
    )
    fun getWatchlistByName(@Bind("name") name: String): WatchlistRecord?

    @SqlQuery(
        """
        SELECT
            $WATCHLIST_SELECT_COLUMNS
        FROM $WATCHLIST_TABLE
        ORDER BY id
        LIMIT :limit
        """,
    )
    fun listWatchlists(@Bind("limit") limit: Int): List<WatchlistRecord>

    @SqlQuery(
        """
        UPDATE $WATCHLIST_TABLE
        SET
            name = CASE WHEN :setName THEN CAST(:name AS text) ELSE name END,
            description = CASE WHEN :setDescription THEN CAST(:description AS text) ELSE description END
        WHERE id = :watchlistId
        RETURNING
            $WATCHLIST_SELECT_COLUMNS
        """,
    )
    fun updateWatchlist(
        @Bind("watchlistId") watchlistId: Long,
        @Bind("setName") setName: Boolean,
        @Bind("name") name: String?,
        @Bind("setDescription") setDescription: Boolean,
        @Bind("description") description: String?,
    ): WatchlistRecord?

    @SqlUpdate(
        """
        DELETE FROM $WATCHLIST_TABLE
        WHERE id = :watchlistId
        """,
    )
    fun deleteWatchlist(@Bind("watchlistId") watchlistId: Long): Int

    private companion object {
        const val WATCHLIST_TABLE: String = "watchlists"
        const val WATCHLIST_SELECT_COLUMNS: String = """
            id,
            name,
            description,
            created_at,
            updated_at
        """
    }
}
