package com.tradingtool.core.fundamentals.screener

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScreenerFundamentalsParserTest {
    @Test
    fun `parse extracts core fundamentals from company page html`() {
        val html = """
            <html>
              <head><title>Reliance Industries Ltd share price | About Reliance Industries | Key Insights - Screener</title></head>
              <body>
                <h1 class="margin-0 show-from-tablet-landscape">Reliance Industries Ltd</h1>
                <div class="company-ratios">
                  <ul id="top-ratios">
                    <li class="flex flex-space-between" data-source="default">
                      <span class="name">Market Cap</span>
                      <span class="nowrap value">₹ <span class="number">18,27,154</span> Cr.</span>
                    </li>
                    <li class="flex flex-space-between" data-source="default">
                      <span class="name">Stock P/E</span>
                      <span class="nowrap value"><span class="number">23.8</span></span>
                    </li>
                    <li class="flex flex-space-between" data-source="default">
                      <span class="name">ROCE</span>
                      <span class="nowrap value"><span class="number">9.69</span> %</span>
                    </li>
                    <li class="flex flex-space-between" data-source="default">
                      <span class="name">ROE</span>
                      <span class="nowrap value"><span class="number">8.40</span> %</span>
                    </li>
                  </ul>
                </div>
                <a href="/market/IN03/" title="Broad Industry">Petroleum Products</a>
                <a href="/market/IN03/IN0301/" title="Industry">Refineries &amp; Marketing</a>
                <section id="shareholding">
                  <table class="data-table">
                    <tbody>
                      <tr class="stripe">
                        <td class="text">
                          <button class="button-plain plausible-event-classification=promoters">Promoters&nbsp;<span>+</span></button>
                        </td>
                        <td>50.41%</td><td>50.39%</td><td>50.00%</td>
                      </tr>
                    </tbody>
                  </table>
                </section>
              </body>
            </html>
        """.trimIndent()

        val snapshot = ScreenerFundamentalsParser.parse("RELIANCE", html)

        assertEquals("Reliance Industries Ltd", snapshot.companyName)
        assertEquals(1827154.0, snapshot.marketCapCr)
        assertEquals(23.8, snapshot.stockPe)
        assertEquals(9.69, snapshot.rocePercent)
        assertEquals(8.40, snapshot.roePercent)
        assertEquals(50.0, snapshot.promoterHoldingPercent)
        assertEquals("Petroleum Products", snapshot.broadIndustry)
        assertEquals("Refineries & Marketing", snapshot.industry)
        assertNull(snapshot.debtToEquity)
        assertNull(snapshot.pledgedPercent)
    }
}
