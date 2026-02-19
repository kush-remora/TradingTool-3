package com.tradingtool.core.watchlist.dal.mapper

import com.tradingtool.core.model.watchlist.StockRecord
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import java.sql.Array as SqlArray
import java.sql.ResultSet
import java.time.OffsetDateTime

class StockRecordMapper : RowMapper<StockRecord> {
    override fun map(rs: ResultSet, ctx: StatementContext): StockRecord {
        return StockRecord(
            id = rs.getLong("id"),
            nseSymbol = rs.getString("nse_symbol"),
            companyName = rs.getString("company_name"),
            growwSymbol = rs.getString("groww_symbol"),
            kiteSymbol = rs.getString("kite_symbol"),
            description = rs.getString("description"),
            rating = rs.getObject("rating")?.let { value -> (value as Number).toInt() },
            tags = parseStringArray(rs.getArray("tags")),
            createdAt = toUtcString(rs.getObject("created_at", OffsetDateTime::class.java)),
            updatedAt = toUtcString(rs.getObject("updated_at", OffsetDateTime::class.java)),
        )
    }

    private fun parseStringArray(sqlArray: SqlArray?): List<String> {
        if (sqlArray == null) {
            return emptyList()
        }

        return try {
            val rawArray: Any? = sqlArray.array
            val values: Array<*> = rawArray as? Array<*> ?: return emptyList()
            values.mapNotNull { value -> value?.toString() }
        } finally {
            runCatching { sqlArray.free() }
        }
    }

    private fun toUtcString(value: OffsetDateTime?): String {
        return value?.toInstant()?.toString()
            ?: throw IllegalStateException("Unexpected null timestamp in database row")
    }
}
