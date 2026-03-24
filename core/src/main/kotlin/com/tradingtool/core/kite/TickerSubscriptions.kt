package com.tradingtool.core.kite

/**
 * Allows resources to add/remove instrument tokens from the live Kite ticker
 * without depending on the event-service module.
 *
 * Implemented by KiteTickerService in event-service.
 * Bound in ServiceModule: bind(TickerSubscriptions).to(KiteTickerService).
 */
interface TickerSubscriptions {
    fun addInstrument(token: Long)
    fun removeInstrument(token: Long)
}
