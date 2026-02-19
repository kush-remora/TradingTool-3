package com.tradingtool.core.watchlist.dao

import com.tradingtool.core.model.watchlist.StockRecord
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.sql.Array as SqlArray
import java.sql.ResultSet
import java.time.OffsetDateTime

@RegisterRowMapper(StockRecordMapper::class)
interface StockQueryDao {
    @SqlQuery(
        """
        INSERT INTO $STOCK_TABLE (
            nse_symbol,
            company_name,
            groww_symbol,
            kite_symbol,
            description,
            rating,
            tags
        ) VALUES (
            :nseSymbol,
            :companyName,
            :growwSymbol,
            :kiteSymbol,
            :description,
            CAST(:rating AS smallint),
            CAST(:tags AS text[])
        )
        RETURNING
            $STOCK_SELECT_COLUMNS
        """,
    )
    fun createStock(
        @Bind("nseSymbol") nseSymbol: String,
        @Bind("companyName") companyName: String,
        @Bind("growwSymbol") growwSymbol: String?,
        @Bind("kiteSymbol") kiteSymbol: String?,
        @Bind("description") description: String?,
        @Bind("rating") rating: Int?,
        @Bind("tags") tags: Array<String>,
    ): StockRecord

    @SqlQuery(
        """
        SELECT
            $STOCK_SELECT_COLUMNS
        FROM $STOCK_TABLE
        WHERE id = :stockId
        LIMIT 1
        """,
    )
    fun getStockById(@Bind("stockId") stockId: Long): StockRecord?

    @SqlQuery(
        """
        SELECT
            $STOCK_SELECT_COLUMNS
        FROM $STOCK_TABLE
        WHERE nse_symbol = :nseSymbol
        LIMIT 1
        """,
    )
    fun getStockByNseSymbol(@Bind("nseSymbol") nseSymbol: String): StockRecord?

    @SqlQuery(
        """
        SELECT
            $STOCK_SELECT_COLUMNS
        FROM $STOCK_TABLE
        ORDER BY id
        LIMIT :limit
        """,
    )
    fun listStocks(@Bind("limit") limit: Int): List<StockRecord>

    @SqlQuery(
        """
        UPDATE $STOCK_TABLE
        SET
            company_name = CASE WHEN :setCompanyName THEN CAST(:companyName AS text) ELSE company_name END,
            groww_symbol = CASE WHEN :setGrowwSymbol THEN CAST(:growwSymbol AS text) ELSE groww_symbol END,
            kite_symbol = CASE WHEN :setKiteSymbol THEN CAST(:kiteSymbol AS text) ELSE kite_symbol END,
            description = CASE WHEN :setDescription THEN CAST(:description AS text) ELSE description END,
            rating = CASE WHEN :setRating THEN CAST(:rating AS smallint) ELSE rating END,
            tags = CASE WHEN :setTags THEN CAST(:tags AS text[]) ELSE tags END
        WHERE id = :stockId
        RETURNING
            $STOCK_SELECT_COLUMNS
        """,
    )
    fun updateStock(
        @Bind("stockId") stockId: Long,
        @Bind("setCompanyName") setCompanyName: Boolean,
        @Bind("companyName") companyName: String?,
        @Bind("setGrowwSymbol") setGrowwSymbol: Boolean,
        @Bind("growwSymbol") growwSymbol: String?,
        @Bind("setKiteSymbol") setKiteSymbol: Boolean,
        @Bind("kiteSymbol") kiteSymbol: String?,
        @Bind("setDescription") setDescription: Boolean,
        @Bind("description") description: String?,
        @Bind("setRating") setRating: Boolean,
        @Bind("rating") rating: Int?,
        @Bind("setTags") setTags: Boolean,
        @Bind("tags") tags: Array<String>?,
    ): StockRecord?

    @SqlUpdate(
        """
        DELETE FROM $STOCK_TABLE
        WHERE id = :stockId
        """,
    )
    fun deleteStock(@Bind("stockId") stockId: Long): Int

    private companion object {
        const val STOCK_TABLE: String = "stocks"
        const val STOCK_SELECT_COLUMNS: String = """
            id,
            nse_symbol,
            company_name,
            groww_symbol,
            kite_symbol,
            description,
            rating,
            tags,
            created_at,
            updated_at
        """
    }
}

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
