package com.tradingtool.core.watchlist.dal.mapper

import com.tradingtool.core.model.watchlist.WatchlistRecord
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import java.sql.ResultSet
import java.time.OffsetDateTime

class WatchlistRecordMapper : RowMapper<WatchlistRecord> {
    override fun map(rs: ResultSet, ctx: StatementContext): WatchlistRecord {
        return WatchlistRecord(
            id = rs.getLong("id"),
            name = rs.getString("name"),
            description = rs.getString("description"),
            createdAt = toUtcString(rs.getObject("created_at", OffsetDateTime::class.java)),
            updatedAt = toUtcString(rs.getObject("updated_at", OffsetDateTime::class.java)),
        )
    }

    private fun toUtcString(value: OffsetDateTime?): String {
        return value?.toInstant()?.toString()
            ?: throw IllegalStateException("Unexpected null timestamp in database row")
    }
}
