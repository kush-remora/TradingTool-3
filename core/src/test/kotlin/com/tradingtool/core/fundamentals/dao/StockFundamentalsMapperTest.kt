package com.tradingtool.core.fundamentals.dao

import com.tradingtool.core.constants.DatabaseConstants.StockFundamentalsColumns as Cols
import com.tradingtool.core.delivery.model.DeliveryUniverse
import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.statement.StatementContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Date
import java.sql.Types
import javax.sql.rowset.RowSetMetaDataImpl
import javax.sql.rowset.RowSetProvider

class StockFundamentalsMapperTest {

    @Test
    fun `map reads fundamentals row with nullable stock id`() {
        val rowSet = RowSetProvider.newFactory().createCachedRowSet()
        val metadata = RowSetMetaDataImpl()
        metadata.columnCount = 17
        metadata.setColumnName(1, Cols.STOCK_ID)
        metadata.setColumnType(1, Types.BIGINT)
        metadata.setColumnName(2, Cols.INSTRUMENT_TOKEN)
        metadata.setColumnType(2, Types.BIGINT)
        metadata.setColumnName(3, Cols.SYMBOL)
        metadata.setColumnType(3, Types.VARCHAR)
        metadata.setColumnName(4, Cols.EXCHANGE)
        metadata.setColumnType(4, Types.VARCHAR)
        metadata.setColumnName(5, Cols.UNIVERSE)
        metadata.setColumnType(5, Types.VARCHAR)
        metadata.setColumnName(6, Cols.SNAPSHOT_DATE)
        metadata.setColumnType(6, Types.DATE)
        metadata.setColumnName(7, Cols.COMPANY_NAME)
        metadata.setColumnType(7, Types.VARCHAR)
        metadata.setColumnName(8, Cols.MARKET_CAP_CR)
        metadata.setColumnType(8, Types.DOUBLE)
        metadata.setColumnName(9, Cols.STOCK_PE)
        metadata.setColumnType(9, Types.DOUBLE)
        metadata.setColumnName(10, Cols.ROCE_PERCENT)
        metadata.setColumnType(10, Types.DOUBLE)
        metadata.setColumnName(11, Cols.ROE_PERCENT)
        metadata.setColumnType(11, Types.DOUBLE)
        metadata.setColumnName(12, Cols.PROMOTER_HOLDING_PERCENT)
        metadata.setColumnType(12, Types.DOUBLE)
        metadata.setColumnName(13, Cols.BROAD_INDUSTRY)
        metadata.setColumnType(13, Types.VARCHAR)
        metadata.setColumnName(14, Cols.INDUSTRY)
        metadata.setColumnType(14, Types.VARCHAR)
        metadata.setColumnName(15, Cols.SOURCE_NAME)
        metadata.setColumnType(15, Types.VARCHAR)
        metadata.setColumnName(16, Cols.SOURCE_URL)
        metadata.setColumnType(16, Types.VARCHAR)
        metadata.setColumnName(17, Cols.FETCHED_AT)
        metadata.setColumnType(17, Types.TIMESTAMP)
        rowSet.setMetaData(metadata)

        rowSet.moveToInsertRow()
        rowSet.updateNull(Cols.STOCK_ID)
        rowSet.updateLong(Cols.INSTRUMENT_TOKEN, 738561L)
        rowSet.updateString(Cols.SYMBOL, "RELIANCE")
        rowSet.updateString(Cols.EXCHANGE, "NSE")
        rowSet.updateString(Cols.UNIVERSE, DeliveryUniverse.LARGEMIDCAP_250.storageValue)
        rowSet.updateDate(Cols.SNAPSHOT_DATE, Date.valueOf("2026-04-14"))
        rowSet.updateString(Cols.COMPANY_NAME, "Reliance Industries Ltd")
        rowSet.updateDouble(Cols.MARKET_CAP_CR, 2_450_123.45)
        rowSet.updateDouble(Cols.STOCK_PE, 24.8)
        rowSet.updateDouble(Cols.ROCE_PERCENT, 14.2)
        rowSet.updateDouble(Cols.ROE_PERCENT, 11.6)
        rowSet.updateDouble(Cols.PROMOTER_HOLDING_PERCENT, 50.1)
        rowSet.updateString(Cols.BROAD_INDUSTRY, "Energy")
        rowSet.updateString(Cols.INDUSTRY, "Oil & Gas")
        rowSet.updateString(Cols.SOURCE_NAME, "nse-corporate-filings")
        rowSet.updateString(Cols.SOURCE_URL, "https://www.nseindia.com/api/corporates-financial-results?index=equities&symbol=RELIANCE&period=Quarterly")
        rowSet.updateNull(Cols.FETCHED_AT)
        rowSet.insertRow()
        rowSet.moveToCurrentRow()
        rowSet.beforeFirst()
        rowSet.next()

        val mapped = StockFundamentalsMapper().map(rowSet, statementContext())

        assertNull(mapped.stockId)
        assertEquals(738561L, mapped.instrumentToken)
        assertEquals("RELIANCE", mapped.symbol)
        assertEquals(DeliveryUniverse.LARGEMIDCAP_250, mapped.universe)
        assertEquals(24.8, mapped.stockPe)
        assertEquals("Energy", mapped.broadIndustry)
        assertEquals("nse-corporate-filings", mapped.sourceName)
    }

    private fun statementContext(): StatementContext {
        val factory = StatementContext::class.java.getDeclaredMethod(
            "create",
            ConfigRegistry::class.java,
            Class.forName("org.jdbi.v3.core.extension.ExtensionMethod"),
            java.lang.reflect.Type::class.java,
        )
        factory.isAccessible = true
        return factory.invoke(null, ConfigRegistry(), null, null) as StatementContext
    }
}
