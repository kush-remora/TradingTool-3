package com.tradingtool.core.strategy.absolutedelivery

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.indexconstituents.IndexConstituentKeys
import java.time.LocalDate

@Singleton
class AbsoluteDeliveryBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val stockDeliveryHandler: StockDeliveryJdbiHandler,
    private val candleHandler: CandleJdbiHandler,
) {
    suspend fun listGroupingOptions(): List<AbsoluteDeliveryGroupingOption> =
        indexConstituentHandler.read { dao ->
            absoluteDeliveryGroupingOptions(dao.listUniqueIndices())
        }

    suspend fun runBacktest(groupingKey: String? = null): AbsoluteDeliveryBacktestResponse {
        val indexSummaries = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
        val resolvedGrouping = resolveAbsoluteDeliveryGrouping(
            requestedGrouping = groupingKey,
            summaries = indexSummaries,
            defaultGrouping = IndexConstituentKeys.GROWW_WATCHLIST,
        )
        val toDate = stockDeliveryHandler.read { dao -> dao.getLatestTradingDate() }
            ?: throw IllegalStateException("No stock delivery data available.")
        val fromDate = absoluteDeliveryBacktestFromDate(toDate)
        val members = indexConstituentHandler.read { dao ->
            dao.listActiveByIndex(resolvedGrouping)
        }.map { member ->
            AbsoluteDeliveryWatchlistMember(
                symbol = member.symbol.trim().uppercase(),
                companyName = member.companyName,
                instrumentToken = member.instrumentToken,
            )
        }.filter { member -> member.symbol.isNotEmpty() }

        val tradingDates = stockDeliveryHandler.read { dao ->
            dao.findTradingDatesBetween(fromDate = fromDate, toDate = toDate)
        }
        val deliveries = if (members.isEmpty()) {
            emptyList()
        } else {
            stockDeliveryHandler.read { dao ->
                dao.findByInstrumentTokensBetweenDates(
                    instrumentTokens = members.map { member -> member.instrumentToken }.distinct(),
                    fromDate = fromDate,
                    toDate = toDate,
                )
            }
        }
        val candles = if (members.isEmpty()) {
            emptyList()
        } else {
            candleHandler.read { dao ->
                dao.getDailyCandlesByTokens(
                    tokens = members.map { member -> member.instrumentToken }.distinct(),
                    from = fromDate.minusYears(1L),
                    to = toDate,
                )
            }
        }

        return AbsoluteDeliveryBacktestAnalyzer.buildResponse(
            AbsoluteDeliveryBacktestInput(
                universeKey = resolvedGrouping,
                fromDate = fromDate,
                toDate = toDate,
                members = members,
                tradingDates = tradingDates,
                deliveries = deliveries,
                candles = candles,
            ),
        )
    }
}

internal fun absoluteDeliveryBacktestFromDate(toDate: LocalDate): LocalDate =
    toDate.minusMonths(6L)
