package com.tradingtool.core.watchlist.dal.sql

import com.tradingtool.core.model.watchlist.StockRecord
import com.tradingtool.core.watchlist.dal.mapper.StockRecordMapper
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

@RegisterRowMapper(StockRecordMapper::class)
interface StockSqlObject {
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
