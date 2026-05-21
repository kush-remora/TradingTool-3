package com.tradingtool.core.screener

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.fundamentals.config.NseIndexConstituentsService
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.stock.Stock
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumConfigService
import com.tradingtool.core.technical.calculateRsiValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

@Singleton
class RsiFloorScannerService @Inject constructor(
    private val redis: RedisHandler,
    private val candleHandler: CandleJdbiHandler,
    private val stockHandler: StockJdbiHandler,
    private val instrumentCache: InstrumentCache,
    private val kiteClient: KiteConnectClient,
    private val rsiMomentumConfigService: RsiMomentumConfigService,
    private val nseIndexConstituentsService: NseIndexConstituentsService,
) {
    private val log = LoggerFactory.getLogger(RsiFloorScannerService::class.java)
    private val ist: ZoneId = ZoneId.of("Asia/Kolkata")
    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
    private val candleType = object : TypeReference<List<DailyCandle>>() {}
    private val resultType = object : TypeReference<RsiFloorScannerResult>() {}

    suspend fun scan(request: RsiFloorScannerRequest): RsiFloorScannerResult {
        val normalized = normalizeRequest(request)
        ensureInstrumentCacheLoaded()

        val universeSymbols = resolveUniverseSymbols(normalized.universe)
        val stocksBySymbol = stockHandler.read { dao ->
            dao.listAll()
                .asSequence()
                .filter { stock -> stock.exchange.equals("NSE", ignoreCase = true) }
                .associateBy { stock -> stock.symbol.trim().uppercase() }
        }
        val cacheKey = buildResultCacheKey(normalized)

        if (!normalized.freshScan) {
            val cached = loadCachedResult(cacheKey)
            if (cached != null) {
                return cached.copy(source = RsiFloorScanSource.CACHE)
            }
        }

        if (normalized.freshScan) {
            clearUniverseCaches(universeSymbols, stocksBySymbol, cacheKey, normalized.yearWindowDays)
        }

        val requestedSymbols = universeSymbols.size
        val rows = mutableListOf<RsiFloorScannerRow>()
        var scannedSymbols = 0
        var skippedInsufficientHistory = 0
        val minimumCandlesRequired = maxOf(normalized.rsiPeriod + 1, normalized.lookbackMatchDays)

        val resolvedTokens = resolveTokens(universeSymbols, stocksBySymbol)
        val today = LocalDate.now(ist)
        val fromDate = today.minusDays(computeCalendarLookbackDays(normalized))

        for (symbol in universeSymbols) {
            val token = resolvedTokens[symbol] ?: continue
            val companyName = resolveCompanyName(symbol, token, stocksBySymbol)
            val candles = loadYearlyCandlesWithFallback(
                symbol = symbol,
                token = token,
                from = fromDate,
                to = today,
                yearWindowDays = normalized.yearWindowDays,
                minimumCandlesRequired = minimumCandlesRequired,
            )
            if (candles.size < minimumCandlesRequired) {
                skippedInsufficientHistory++
                continue
            }

            val matched = findLatestMatch(candles, normalized)
            if (matched == null) {
                scannedSymbols++
                continue
            }

            scannedSymbols++
            val capBucket = classifyMarketCapBucket(null)
            rows += RsiFloorScannerRow(
                symbol = symbol,
                companyName = companyName,
                exchange = "NSE",
                instrumentToken = token,
                currentRsi = matched.currentRsi,
                yearLowRsiAtMatchedDay = matched.yearLowRsi,
                matchedByYearLow = matched.matchedByYearLow,
                matchedByHardLimit = matched.matchedByHardLimit,
                matchedDate = matched.matchedDate,
                ltp = matched.ltp,
                drawdownPct = matched.drawdownPct,
                high52w = matched.high52w,
                low52w = matched.low52w,
                marketCapCr = null,
                capBucket = capBucket,
                historyType = matched.historyType,
            )
        }

        val result = RsiFloorScannerResult(
            universe = normalized.universe,
            requestedSymbols = requestedSymbols,
            scannedSymbols = scannedSymbols,
            skippedInsufficientHistory = skippedInsufficientHistory,
            matchedCount = rows.size,
            lookbackMatchDays = normalized.lookbackMatchDays,
            rsiPeriod = normalized.rsiPeriod,
            yearWindowDays = normalized.yearWindowDays,
            hardRsiLimit = normalized.hardRsiLimit,
            source = RsiFloorScanSource.FRESH,
            rows = rows.sortedBy { row -> row.currentRsi },
        )
        saveResultCache(cacheKey, result)
        return result
    }

    private fun normalizeRequest(request: RsiFloorScannerRequest): RsiFloorScannerRequest {
        return request.copy(
            universe = request.universe.trim().uppercase().ifBlank { "ALL_NSE" },
            lookbackMatchDays = request.lookbackMatchDays.coerceIn(1, 60),
            rsiPeriod = request.rsiPeriod.coerceIn(2, 50),
            yearWindowDays = request.yearWindowDays.coerceIn(120, 500),
            hardRsiLimit = request.hardRsiLimit.coerceIn(1.0, 40.0),
        )
    }

    private suspend fun loadCachedResult(cacheKey: String): RsiFloorScannerResult? {
        val raw = runCatching { redis.get(cacheKey) }.getOrNull() ?: return null
        return runCatching { objectMapper.readValue(raw, resultType) }
            .onFailure { error ->
                log.warn("Failed to parse cached scanner result for key={}: {}", cacheKey, error.message)
            }
            .getOrNull()
    }

    private suspend fun saveResultCache(cacheKey: String, result: RsiFloorScannerResult) {
        runCatching {
            redis.set(cacheKey, objectMapper.writeValueAsString(result), RESULT_CACHE_TTL_SECONDS)
        }.onFailure { error ->
            log.warn("Failed to cache scanner result for key={}: {}", cacheKey, error.message)
        }
    }

    private suspend fun clearUniverseCaches(
        symbols: List<String>,
        stocksBySymbol: Map<String, Stock>,
        resultCacheKey: String,
        yearWindowDays: Int,
    ) {
        runCatching { redis.delete(resultCacheKey) }
        val tokens = resolveTokens(symbols, stocksBySymbol).values.toSet()
        tokens.forEach { token ->
            runCatching { redis.delete(buildYearlyCandleCacheKey(token, yearWindowDays)) }
        }
    }

    private suspend fun resolveUniverseSymbols(universe: String): List<String> {
        val watchlistSymbols = stockHandler.read { dao ->
            dao.listAll()
                .asSequence()
                .filter { stock -> stock.exchange.equals("NSE", ignoreCase = true) }
                .map { stock -> stock.symbol.trim().uppercase() }
                .filter { symbol -> symbol.isNotEmpty() }
                .toList()
        }

        val symbols = when (universe) {
            "ALL_NSE" -> instrumentCache.all()
                .asSequence()
                .filter { instrument ->
                    instrument.exchange.equals("NSE", ignoreCase = true) &&
                        instrument.instrument_type.equals("EQ", ignoreCase = true)
                }
                .map { instrument -> instrument.tradingsymbol.trim().uppercase() }
                .filter { symbol -> symbol.isNotEmpty() }
                .toList()
            "ALL_CUSTOM_UNIVERSE" -> {
                nseIndexConstituentsService.fetchSymbols("NIFTY LARGEMIDCAP 250") +
                    nseIndexConstituentsService.fetchSymbols("NIFTY SMALLCAP 250") +
                    GROWW_WATCHLIST_SYMBOLS
            }
            "NIFTY_100" -> nseIndexConstituentsService.fetchSymbols("NIFTY 100")
            "NIFTY_MIDCAP_250" -> nseIndexConstituentsService.fetchSymbols("NIFTY MIDCAP 250")
            "NIFTY_LARGEMIDCAP_250" -> rsiMomentumConfigService.loadBaseUniverseSymbols("NIFTY_LARGEMIDCAP_250")
            "NIFTY_SMALLCAP_250" -> rsiMomentumConfigService.loadBaseUniverseSymbols("NIFTY_SMALLCAP_250")
            "NIFTY_50" -> rsiMomentumConfigService.loadBaseUniverseSymbols("NIFTY_50")
            "NIFTY_500" -> loadSymbolsFromCsv("strategy-universes/nifty_500.csv")
            "GROWW_WATCHLIST" -> watchlistSymbols
            "WATCHLIST" -> watchlistSymbols
            else -> emptyList()
        }

        val cleaned = symbols
            .asSequence()
            .map { symbol -> symbol.trim().uppercase() }
            .filter { symbol -> symbol.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()
        return if (cleaned.isEmpty() && universe != "ALL_NSE") {
            resolveUniverseSymbols("ALL_NSE")
        } else {
            cleaned
        }
    }

    private fun loadSymbolsFromCsv(resourceName: String): List<String> {
        val stream = javaClass.classLoader.getResourceAsStream(resourceName) ?: return emptyList()
        return stream.bufferedReader().useLines { lines ->
            lines.map { line -> line.trim() }
                .filter { line -> line.isNotEmpty() && !line.startsWith("#") && line.lowercase() != "symbol" }
                .map { line -> line.uppercase() }
                .toList()
        }
    }

    private fun resolveTokens(
        symbols: List<String>,
        stocksBySymbol: Map<String, Stock>,
    ): Map<String, Long> {
        val resolved = linkedMapOf<String, Long>()
        symbols.forEach { symbol ->
            val cacheToken = instrumentCache.token("NSE", symbol)
            if (cacheToken != null && cacheToken > 0) {
                resolved[symbol] = cacheToken
                return@forEach
            }
            val stockToken = stocksBySymbol[symbol]?.instrumentToken
            if (stockToken != null && stockToken > 0) {
                resolved[symbol] = stockToken
            }
        }
        return resolved
    }

    private fun resolveCompanyName(
        symbol: String,
        token: Long,
        stocksBySymbol: Map<String, Stock>,
    ): String {
        return instrumentCache.find(token)?.name?.takeIf { name -> name.isNotBlank() }
            ?: stocksBySymbol[symbol]?.companyName
            ?: symbol
    }

    private suspend fun loadYearlyCandlesWithFallback(
        symbol: String,
        token: Long,
        from: LocalDate,
        to: LocalDate,
        yearWindowDays: Int,
        minimumCandlesRequired: Int,
    ): List<DailyCandle> {
        val desiredTradingDays = yearWindowDays + RSI_WARMUP_DAYS
        val cacheKey = buildYearlyCandleCacheKey(token, yearWindowDays)
        val cached = runCatching { redis.get(cacheKey) }.getOrNull()
        if (!cached.isNullOrBlank()) {
            val parsed = runCatching { objectMapper.readValue(cached, candleType) }.getOrNull()
            val sortedParsed = parsed.orEmpty().sortedBy { candle -> candle.candleDate }
            if (!shouldFetchFromKite(sortedParsed, from, to, desiredTradingDays)) {
                return sortedParsed
            }
        }

        val dbCandles = candleHandler.read { dao ->
            dao.getDailyCandles(token, from, to)
        }.sortedBy { candle -> candle.candleDate }
        if (!shouldFetchFromKite(dbCandles, from, to, desiredTradingDays)) {
            runCatching {
                redis.set(cacheKey, objectMapper.writeValueAsString(dbCandles), YEARLY_CANDLE_CACHE_TTL_SECONDS)
            }
            return dbCandles
        }

        val fetched = fetchFromKite(symbol, token, from, to)
        if (fetched.isNotEmpty()) {
            candleHandler.write { dao -> dao.upsertDailyCandles(fetched) }
        }

        val merged = mergeCandles(dbCandles, fetched)
        if (merged.isNotEmpty() && isUsableCoverage(merged, to, minimumCandlesRequired)) {
            runCatching {
                redis.set(cacheKey, objectMapper.writeValueAsString(merged), YEARLY_CANDLE_CACHE_TTL_SECONDS)
            }
            return merged
        }

        log.warn(
            "Insufficient candle coverage for {} (token={}) after fallback. dbCount={}, kiteCount={}, mergedCount={}",
            symbol,
            token,
            dbCandles.size,
            fetched.size,
            merged.size,
        )
        if (merged.isNotEmpty()) {
            // Allow partial-history symbols to be evaluated as long as scan-level minimum candles are present.
            if (merged.size >= minimumCandlesRequired) {
                runCatching {
                    redis.set(cacheKey, objectMapper.writeValueAsString(merged), YEARLY_CANDLE_CACHE_TTL_SECONDS)
                }
                return merged
            }
            runCatching {
                redis.set(cacheKey, objectMapper.writeValueAsString(merged), YEARLY_CANDLE_CACHE_TTL_SECONDS)
            }
        }

        val ohlcvFallbackCandles = loadFromOhlcvRedisFallback(symbol, token, from, to)
        if (ohlcvFallbackCandles.size >= minimumCandlesRequired) {
            runCatching {
                redis.set(cacheKey, objectMapper.writeValueAsString(ohlcvFallbackCandles), YEARLY_CANDLE_CACHE_TTL_SECONDS)
            }
            return ohlcvFallbackCandles
        }
        return emptyList()
    }

    private suspend fun loadFromOhlcvRedisFallback(
        symbol: String,
        token: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<DailyCandle> {
        val raw = runCatching { redis.get("stock:$token:ohlcv") }.getOrNull() ?: return emptyList()
        val values = runCatching { objectMapper.readTree(raw) }.getOrNull() ?: return emptyList()
        if (!values.isArray) return emptyList()

        return values.mapNotNull { node ->
            val timestamp = node.get("timeStamp")?.asText()?.takeIf { it.length >= 19 } ?: return@mapNotNull null
            val candleDate = runCatching { LocalDateTime.parse(timestamp.substring(0, 19)).toLocalDate() }.getOrNull()
                ?: return@mapNotNull null
            if (candleDate.isBefore(from) || candleDate.isAfter(to)) return@mapNotNull null

            val open = node.get("open")?.asDouble() ?: return@mapNotNull null
            val high = node.get("high")?.asDouble() ?: return@mapNotNull null
            val low = node.get("low")?.asDouble() ?: return@mapNotNull null
            val close = node.get("close")?.asDouble() ?: return@mapNotNull null
            val volume = node.get("volume")?.asLong() ?: 0L

            DailyCandle(
                instrumentToken = token,
                symbol = symbol,
                candleDate = candleDate,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = volume,
            )
        }.sortedBy { candle -> candle.candleDate }
    }

    private suspend fun fetchFromKite(
        symbol: String,
        token: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<DailyCandle> {
        return runCatching {
            withContext(Dispatchers.IO) {
                kiteClient.client().getHistoricalData(
                    from.toJavaDate(ist),
                    to.toJavaDate(ist),
                    token.toString(),
                    "day",
                    false,
                    false,
                )
            }
        }.fold(
            onSuccess = { response ->
                response.dataArrayList.mapNotNull { bar ->
                    runCatching {
                        val historical = bar ?: return@mapNotNull null
                        val candleDate = LocalDateTime.parse(historical.timeStamp.substring(0, 19)).toLocalDate()
                        DailyCandle(
                            instrumentToken = token,
                            symbol = symbol,
                            candleDate = candleDate,
                            open = historical.open,
                            high = historical.high,
                            low = historical.low,
                            close = historical.close,
                            volume = historical.volume.toLong(),
                        )
                    }.getOrNull()
                }
            },
            onFailure = { error ->
                log.warn("Failed to fetch candles from Kite for {} ({}): {}", symbol, token, error.message)
                emptyList()
            },
        )
    }

    private fun findLatestMatch(
        candles: List<DailyCandle>,
        request: RsiFloorScannerRequest,
    ): MatchedObservation? {
        if (candles.isEmpty()) return null
        val rsiValues = candles.calculateRsiValues(period = request.rsiPeriod, fallback = 50.0)
        if (rsiValues.isEmpty()) return null

        val lastIndex = candles.lastIndex
        val startEval = (lastIndex - request.lookbackMatchDays + 1).coerceAtLeast(0)
        var best: MatchedObservation? = null

        for (index in startEval..lastIndex) {
            val windowStart = resolveWindowStartIndex(
                candles = candles,
                index = index,
                yearWindowDays = request.yearWindowDays,
            )
            val windowSize = index - windowStart + 1

            val currentRsi = rsiValues[index]
            val validRsiStart = maxOf(windowStart, request.rsiPeriod)
            if (index < validRsiStart) continue
            val yearLowRsi = rsiValues.subList(validRsiStart, index + 1).minOrNull() ?: continue
            val matchedByYearLow = currentRsi <= yearLowRsi
            val matchedByHardLimit = currentRsi <= request.hardRsiLimit
            val matchedByRsiFloor = matchesRsiFloorCondition(currentRsi, yearLowRsi, request.hardRsiLimit)
            val matchedByNearLow = isNearLowAtIndex(
                candles = candles,
                index = index,
                windowStart = windowStart,
            )
            if (!matchedByRsiFloor && !matchedByNearLow) continue

            val windowCandles = candles.subList(windowStart, index + 1)
            val high52w = windowCandles.maxOfOrNull { candle -> candle.high }
            val low52w = windowCandles.minOfOrNull { candle -> candle.low }
            val ltp = candles[index].close
            val drawdownPct = if (high52w != null && high52w > 0.0) {
                ((ltp - high52w) / high52w) * 100.0
            } else {
                null
            }
            val historyType = if (windowSize >= request.yearWindowDays) {
                RsiHistoryType.FULL_1Y
            } else {
                RsiHistoryType.PARTIAL_IPO
            }
            best = MatchedObservation(
                matchedDate = candles[index].candleDate,
                currentRsi = currentRsi,
                yearLowRsi = yearLowRsi,
                matchedByYearLow = matchedByYearLow,
                matchedByHardLimit = matchedByHardLimit,
                ltp = ltp,
                drawdownPct = drawdownPct,
                high52w = high52w,
                low52w = low52w,
                historyType = historyType,
            )
        }
        return best
    }

    private fun isNearLowAtIndex(
        candles: List<DailyCandle>,
        index: Int,
        windowStart: Int,
    ): Boolean {
        val windowLow = candles.subList(windowStart, index + 1).minOfOrNull { candle -> candle.low } ?: return false
        val thresholdLow = windowLow * (1.0 + NEAR_LOW_TOLERANCE_PCT / 100.0)
        return candles[index].low <= thresholdLow
    }

    private fun resolveWindowStartIndex(
        candles: List<DailyCandle>,
        index: Int,
        yearWindowDays: Int,
    ): Int {
        if (candles.isEmpty()) return 0
        val cutoffDate = candles[index].candleDate.minusDays((yearWindowDays - 1).toLong())
        var start = index
        while (start > 0 && !candles[start - 1].candleDate.isBefore(cutoffDate)) {
            start--
        }
        return start
    }

    private suspend fun ensureInstrumentCacheLoaded() {
        if (!instrumentCache.isEmpty()) return
        val instruments = withContext(Dispatchers.IO) {
            kiteClient.client().getInstruments("NSE")
        }
        instrumentCache.refresh(instruments)
    }

    private fun buildResultCacheKey(request: RsiFloorScannerRequest): String {
        return "scanner:remora:rsi-floor:v2:" +
            "u=${request.universe}:" +
            "lmd=${request.lookbackMatchDays}:" +
            "rsi=${request.rsiPeriod}:" +
            "yw=${request.yearWindowDays}:" +
            "hard=${request.hardRsiLimit}"
    }

    private fun buildYearlyCandleCacheKey(token: Long, yearWindowDays: Int): String {
        return "scanner:remora:rsi-floor:candles:$token:$yearWindowDays"
    }

    private fun computeCalendarLookbackDays(request: RsiFloorScannerRequest): Long {
        val tradingDays = request.yearWindowDays + request.lookbackMatchDays + request.rsiPeriod + 30
        val calendarDays = (tradingDays * 2.2).toLong()
        return calendarDays.coerceAtLeast(730L)
    }

    private fun isUsableCoverage(
        candles: List<DailyCandle>,
        to: LocalDate,
        minimumTradingDays: Int,
    ): Boolean {
        if (candles.size < minimumTradingDays) return false
        val lastDate = candles.lastOrNull()?.candleDate ?: return false
        if (lastDate.isBefore(to.minusDays(MAX_END_LAG_DAYS))) return false
        return true
    }

    private fun shouldFetchFromKite(
        candles: List<DailyCandle>,
        from: LocalDate,
        to: LocalDate,
        desiredTradingDays: Int,
    ): Boolean {
        if (candles.isEmpty()) return true
        if (!isUsableCoverage(candles, to, MIN_HISTORY_DAYS)) return true

        val firstDate = candles.firstOrNull()?.candleDate ?: return true
        val hasDesiredSpan = candles.size >= desiredTradingDays && !firstDate.isAfter(from.plusDays(MAX_START_LAG_DAYS))
        if (hasDesiredSpan) return false

        val looksLikeRecentListing = firstDate.isAfter(from.plusDays(IPO_LISTING_GAP_DAYS))
        if (looksLikeRecentListing && candles.size >= MIN_HISTORY_DAYS) return false

        return true
    }

    private fun mergeCandles(
        dbCandles: List<DailyCandle>,
        fetchedCandles: List<DailyCandle>,
    ): List<DailyCandle> {
        if (dbCandles.isEmpty()) return fetchedCandles.sortedBy { candle -> candle.candleDate }
        if (fetchedCandles.isEmpty()) return dbCandles.sortedBy { candle -> candle.candleDate }

        return (dbCandles + fetchedCandles)
            .associateBy { candle -> candle.candleDate }
            .values
            .sortedBy { candle -> candle.candleDate }
    }

    private data class MatchedObservation(
        val matchedDate: LocalDate,
        val currentRsi: Double,
        val yearLowRsi: Double,
        val matchedByYearLow: Boolean,
        val matchedByHardLimit: Boolean,
        val ltp: Double?,
        val drawdownPct: Double?,
        val high52w: Double?,
        val low52w: Double?,
        val historyType: RsiHistoryType,
    )

    private companion object {
        const val RESULT_CACHE_TTL_SECONDS: Long = 600
        const val YEARLY_CANDLE_CACHE_TTL_SECONDS: Long = 3600
        const val MIN_HISTORY_DAYS: Int = 80
        const val RSI_WARMUP_DAYS: Int = 14
        const val MAX_START_LAG_DAYS: Long = 45
        const val MAX_END_LAG_DAYS: Long = 10
        const val IPO_LISTING_GAP_DAYS: Long = 180
        const val NEAR_LOW_TOLERANCE_PCT: Double = 3.0
        val GROWW_WATCHLIST_SYMBOLS: List<String> = """
            TCS, ANANDRATHI, AQYLON, ICICIAMC, JUSTDIAL, INNOVISION, ICICIPRULI, DEN, ICICIGI, HDBFS, ELECON, GTPL, TEJASNET, HDFCAMC, ANGELONE, HDFCLIFE, CRISIL, WIPRO, VSTIND, SGFIN, LLOYDS, WAAREERTL, ALOKINDS, RAJINDLTD, MASTEK, HATHWAY, INFOMEDIA, BAJAJCON, BIRLAMONEY, JIOFIN, ICICIBANK, HDFCBANK, YESBANK, BHARATCOAL, NETWORK18, SMLMAH, PNBHOUSING, PNBGILTS, WAAREEINDO, AXITA, NAVKARCORP, GROWW, UGROCAP, INDBANK, NELCO, MAHABANK, E2E, NESTLEIND, HCLTECH, PERSISTENT, 360ONE, CYIENTDLM, CMPDI, TATAELXSI, TATAINVEST, TARIL, SUNTECK, RAJRATAN, POWERICA, MAHEPC, DBSTOCKBRO, TECHM, MAHSCOOTER, HAVELLS, PRIZOR, LTTS, SBILIFE, TATACOMM, OFSS, DELTACORP, SANGAMIND, TRENT, SARLAPOLY, INFY, UTIAMC, TATACAP, CIEINDIA, MAHLOG, ADANIENSOL, LTM, CYIENT, HINDCOMPOS, AURUM, ABSLAMC, HSCL, IEX, SWSOLAR, BLUESTONE, CHOICEIN, TTML, ATUL, ADANIGREEN, INDUSINDBK, VINYLINDIA, SHAIVAL, TANLA, CHENNPETRO, LTF, SPLPETRO, SHRIRAMFIN, WENDT, ATISH, RPEL, ZENSARTECH, TNPL, DCBBANK, RELIANCE, M&MFIN, CANFINHOME, JAYNECOIND, MRPL, INDIACEM, IDFCFIRSTB, SBFC, SILKFLEX, AXISBANK, AVANTEL, ULTRACEMCO, NAM-INDIA, CRAMC, MHRIL, ATGL, BAJAJHFL, TMB, GRSE, RALLIS, EMBASSY, COALINDIA, AIMTRON, SUPREMEIND, SHEKHAWATI, HUHTAMAKI, BDR, CUB, ASTEC, MAHLIFE, STARHEALTH, CEATLTD, CASTROLIND, MARUTI, BANDHANBNK, DHANBANK, GODIGIT, ORIENTCEM, DALBHARAT, INFOBEAN, IFCI, PPLPHARMA, MPHASIS, KFINTECH, NAVINFLUOR, HEG, CAPITALSFB, BAJFINANCE, NDTV, FEDERALBNK, CEMPRO, BENARES, SHREDIGCEM, ACCELYA, SCHAEFFLER, GRANULES, SYNGENE, LGBBROSLTD, LALPATHLAB, HINDUNILVR, CHOLAFIN, BAJAJFINSV, GODREJAGRO, PSPPROJECT, KSOLVES, NSDL, LAURUSLABS, ADANIPORTS, ACC, EQUITASBNK, KSB, PANKAJPOLYMERS, GHCLTEXTIL, SIS, KRMAYURVED, NITTAGELA, KOTAKBANK, LATENTVIEW, EXIDEIND, CAMS, CSBBANK, TATATECH, AMBUJACEM, TSIL, M&M, COFORGE, POONAWALLA, UBL, GHCL, ALKYLAMINE, MARICO, KANSAINER, SHREECEM, BLUESTARCO, RML, GODREJCP, APTUS, MUTHOOTMF, BAJAJ-AUTO, RADICO, HEXT, EMUDHRA, DABUR, GLOBUSSPR, BAJAJHLDNG, NOCIL, PIDILITIND, RSSOFTWARE, ESCORTS, THERMAX, RAIN, ABB, MUTHOOTCAP, CHOLAHLDNG, KALYANKJIL, DLINKINDIA, ORIENTPPR, WEP, AURIONPRO, ANANTRAJ, DRREDDY, BBL, MFSL, BERGEPAINT, NOVARTIND, FOSECOIND, SESHAPAPER, HIGHENERGY, CIPLA, ALLCARGO, ENDURANCE, JSWSTEEL, JTEKTINDIA, MUKANDLTD, TIMKEN, WINSOMETEX, TVTODAY, RANEHOLDIN, AAKAAR, GANGOTRI, GICHSGFIN, MINDTECK, BASF, BOSCH-HCIL, BOSCHLTD
        """.split(",")
            .map { symbol -> symbol.trim().uppercase() }
            .filter { symbol -> symbol.isNotEmpty() }
            .distinct()
    }
}

private fun LocalDate.toJavaDate(zoneId: ZoneId): Date = Date.from(atStartOfDay(zoneId).toInstant())
