package com.tradingtool.core.indexconstituents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexConstituentCsvSourceTest {
    private val source = IndexConstituentCsvSource()

    @Test
    fun `parseRows extracts csv columns`() {
        val csv = """
            Company Name,Industry,Symbol,Series,ISIN Code
            A Ltd,Services,abc,EQ,INE1
            B Ltd,Tech, XYZ ,EQ,INE2
        """.trimIndent()

        val rows = source.parseRows(csv)
        assertEquals(2, rows.size)
        assertEquals("ABC", rows[0].symbol)
        assertEquals("A Ltd", rows[0].companyName)
        assertEquals("Services", rows[0].industry)
        assertEquals("EQ", rows[0].series)
        assertEquals("INE1", rows[0].isinCode)
    }

    @Test
    fun `parseRows rejects html body`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Error 404</title></head>
            <body>Not found</body>
            </html>
        """.trimIndent()

        val rows = source.parseRows(html)
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `describeParseFailure explains html response`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>Blocked</body>
            </html>
        """.trimIndent()

        assertEquals(
            "received HTML instead of CSV from source URL",
            source.describeParseFailure(html),
        )
    }

    @Test
    fun `describeParseFailure includes first line when symbol header is missing`() {
        val csv = """
            Company Name,Industry,Ticker,Series,ISIN Code
            A Ltd,Services,ABC,EQ,INE1
        """.trimIndent()

        assertEquals(
            "Symbol column missing or empty. first line=`Company Name,Industry,Ticker,Series,ISIN Code`",
            source.describeParseFailure(csv),
        )
    }
}
