package com.tradingtool.core.watchlist.dal.mapper

import com.tradingtool.core.model.watchlist.WatchlistStockRecord
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import java.sql.ResultSet
import java.time.OffsetDateTime

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
