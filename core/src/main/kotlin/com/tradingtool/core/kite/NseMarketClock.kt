package com.tradingtool.core.kite

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

object NseMarketClock {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    fun isMarketOpen(now: ZonedDateTime = ZonedDateTime.now(zone)): Boolean {
        val marketNow = now.withZoneSameInstant(zone)
        if (marketNow.dayOfWeek == DayOfWeek.SATURDAY || marketNow.dayOfWeek == DayOfWeek.SUNDAY) {
            return false
        }

        val open = marketNow.withHour(9).withMinute(14).withSecond(0).withNano(0)
        val close = marketNow.withHour(15).withMinute(31).withSecond(0).withNano(0)
        return marketNow.isAfter(open) && marketNow.isBefore(close)
    }
}
