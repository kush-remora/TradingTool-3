package com.tradingtool.core.stock.dao

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.tradingtool.core.constants.DatabaseConstants.StockColumns
import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.model.stock.Stock
import com.tradingtool.core.model.stock.StockTag
import com.tradingtool.core.model.stock.TableAccessStatus
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import java.time.OffsetDateTime

@RegisterRowMapper(StockMapper::class)
@RegisterRowMapper(StockTagMapper::class)
interface StockReadDao {

    @SqlQuery("SELECT ${StockColumns.ALL_WITH_TAGS} FROM public.${Tables.STOCKS} WHERE ${StockColumns.ID} = :id LIMIT 1")
    fun getById(@Bind("id") id: Long): Stock?

    @SqlQuery("SELECT ${StockColumns.ALL_WITH_TAGS} FROM public.${Tables.STOCKS} WHERE ${StockColumns.SYMBOL} = :symbol AND ${StockColumns.EXCHANGE} = :exchange LIMIT 1")
    fun getBySymbol(@Bind("symbol") symbol: String, @Bind("exchange") exchange: String): Stock?

    @SqlQuery("SELECT ${StockColumns.ALL_WITH_TAGS} FROM public.${Tables.STOCKS} ORDER BY ${StockColumns.CREATED_AT} DESC")
    fun listAll(): List<Stock>

    @SqlQuery(
        """
        SELECT ${StockColumns.ALL_WITH_TAGS}
        FROM public.${Tables.STOCKS}
        WHERE EXISTS (
            SELECT 1 FROM jsonb_array_elements(${StockColumns.TAGS}) AS elem
            WHERE elem->>'name' = :tagName
        )
        ORDER BY ${StockColumns.CREATED_AT} DESC
        """
    )
    fun listByTagName(@Bind("tagName") tagName: String): List<Stock>

    @SqlQuery(
        """
        SELECT DISTINCT elem->>'name' AS name, elem->>'color' AS color
        FROM public.${Tables.STOCKS}, jsonb_array_elements(${StockColumns.TAGS}) AS elem
        ORDER BY name
        """
    )
    fun listAllTags(): List<StockTag>
}

// ==================== Row Mappers ====================

private val jacksonMapper = ObjectMapper()
private val tagListType = object : TypeReference<List<StockTag>>() {}

class StockMapper : RowMapper<Stock> {
    override fun map(rs: ResultSet, ctx: StatementContext): Stock {
        val tagsJson = rs.getString(StockColumns.TAGS) ?: "[]"
        val tags: List<StockTag> = try {
            jacksonMapper.readValue(tagsJson, tagListType)
        } catch (_: Exception) {
            emptyList()
        }
        return Stock(
            id = rs.getLong(StockColumns.ID),
            symbol = rs.getString(StockColumns.SYMBOL),
            instrumentToken = rs.getLong(StockColumns.INSTRUMENT_TOKEN),
            companyName = rs.getString(StockColumns.COMPANY_NAME),
            exchange = rs.getString(StockColumns.EXCHANGE),
            notes = rs.getString(StockColumns.NOTES),
            priority = rs.getObject(StockColumns.PRIORITY, Int::class.javaObjectType),
            tags = tags,
            createdAt = toUtcString(rs.getObject(StockColumns.CREATED_AT, OffsetDateTime::class.java)),
            updatedAt = toUtcString(rs.getObject(StockColumns.UPDATED_AT, OffsetDateTime::class.java)),
        )
    }

    private fun toUtcString(value: OffsetDateTime?): String =
        value?.toInstant()?.toString()
            ?: throw IllegalStateException("Unexpected null timestamp in database row")
}

class StockTagMapper : RowMapper<StockTag> {
    override fun map(rs: ResultSet, ctx: StatementContext): StockTag {
        return StockTag(
            name = rs.getString("name"),
            color = rs.getString("color"),
        )
    }
}
