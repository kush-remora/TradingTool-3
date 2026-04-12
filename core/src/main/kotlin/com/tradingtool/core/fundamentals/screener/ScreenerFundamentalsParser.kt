package com.tradingtool.core.fundamentals.screener

object ScreenerFundamentalsParser {
    fun parse(symbol: String, html: String): ScreenerFundamentalsSnapshot {
        return ScreenerFundamentalsSnapshot(
            symbol = symbol,
            companyName = extractCompanyName(html),
            marketCapCr = extractTopRatioNumber(html, "Market Cap"),
            stockPe = extractTopRatioNumber(html, "Stock P/E"),
            rocePercent = extractTopRatioNumber(html, "ROCE"),
            roePercent = extractTopRatioNumber(html, "ROE"),
            promoterHoldingPercent = extractPromoterHolding(html),
            broadIndustry = extractIndustry(html, "Broad Industry"),
            industry = extractIndustry(html, "Industry"),
            debtToEquity = extractDetailedRatio(html, "Debt to equity"),
            pledgedPercent = extractDetailedRatio(html, "Pledged percentage"),
        )
    }

    private fun extractCompanyName(html: String): String {
        val match = H1_REGEX.find(html)
            ?: TITLE_REGEX.find(html)
            ?: error("Company name not found on Screener page.")
        return decodeHtml(match.groupValues[1].trim())
    }

    private fun extractTopRatioNumber(html: String, label: String): Double? {
        val pattern = Regex(
            """<li class="flex flex-space-between"[^>]*>\s*<span class="name">\s*$label\s*</span>.*?<span class="number">([^<]+)</span>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        return pattern.find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::parseNumber)
    }

    private fun extractPromoterHolding(html: String): Double? {
        val rowMatch = PROMOTER_ROW_REGEX.find(html) ?: return null
        val cellMatches = TD_PERCENT_REGEX.findAll(rowMatch.value).toList()
        return cellMatches.lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::parseNumber)
    }

    private fun extractIndustry(html: String, title: String): String? {
        val pattern = Regex(
            """title="$title">([^<]+)</a>""",
            setOf(RegexOption.IGNORE_CASE),
        )
        return pattern.find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let(::decodeHtml)
    }

    private fun extractDetailedRatio(html: String, label: String): Double? {
        val pattern = Regex(
            """<td[^>]*class="text"[^>]*>\s*$label\s*</td>\s*<td[^>]*>\s*([^<]+)\s*</td>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        return pattern.find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::parseNumber)
    }

    private fun parseNumber(raw: String): Double? {
        val normalized = raw
            .replace(",", "")
            .replace("%", "")
            .replace("₹", "")
            .replace("Cr.", "", ignoreCase = true)
            .trim()
        if (normalized.isBlank() || normalized == "-") {
            return null
        }
        return normalized.toDoubleOrNull()
    }

    private fun decodeHtml(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
    }

    private val H1_REGEX = Regex("""<h1 class="margin-0 show-from-tablet-landscape">([^<]+)</h1>""")
    private val TITLE_REGEX = Regex("""<title>([^<]+?) share price""")
    private val PROMOTER_ROW_REGEX = Regex(
        """<tr[^>]*>\s*<td class="text">\s*<button[^>]*classification=promoters[^>]*>.*?</tr>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val TD_PERCENT_REGEX = Regex("""<td>\s*([0-9.,]+)%\s*</td>""")
}
