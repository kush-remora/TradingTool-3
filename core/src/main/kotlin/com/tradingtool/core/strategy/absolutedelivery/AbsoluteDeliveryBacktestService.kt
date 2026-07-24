package com.tradingtool.core.strategy.absolutedelivery

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.indexconstituents.IndexConstituentKeys
import java.time.LocalDate

@Singleton
class AbsoluteDeliveryBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val stockDeliveryHandler: StockDeliveryJdbiHandler,
) {
    suspend fun runBacktest(): AbsoluteDeliveryBacktestResponse {
        val toDate = stockDeliveryHandler.read { dao -> dao.getLatestTradingDate() }
            ?: throw IllegalStateException("No stock delivery data available.")
        val fromDate = absoluteDeliveryBacktestFromDate(toDate)
        val members = indexConstituentHandler.read { dao ->
            dao.listActiveByIndex(IndexConstituentKeys.GROWW_WATCHLIST)
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

        return AbsoluteDeliveryBacktestAnalyzer.buildResponse(
            AbsoluteDeliveryBacktestInput(
                universeKey = IndexConstituentKeys.GROWW_WATCHLIST,
                fromDate = fromDate,
                toDate = toDate,
                members = members,
                tradingDates = tradingDates,
                deliveries = deliveries,
            ),
        )
    }
}

internal fun absoluteDeliveryBacktestFromDate(toDate: LocalDate): LocalDate =
    toDate.minusMonths(6L)
