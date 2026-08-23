package com.tradingtool.resources

import com.tradingtool.core.strategy.emacontinuation.EmaContinuationStudyRequest
import com.tradingtool.core.strategy.emacontinuation.EmaContinuationWatchlistStudyRequest
import java.util.Locale

internal fun validateEmaContinuationStudyRequest(request: EmaContinuationStudyRequest): EmaContinuationStudyRequest {
    val watchlistKey = request.watchlistKey?.trim().orEmpty()
    val symbol = request.symbol?.trim()?.uppercase(Locale.ROOT).orEmpty()
    val instrumentToken = request.instrumentToken
    require(watchlistKey.isNotBlank()) { "watchlistKey is required." }
    require(symbol.isNotBlank()) { "symbol is required." }
    require(instrumentToken != null && instrumentToken > 0) { "instrumentToken must be positive." }
    return request.copy(watchlistKey = watchlistKey, symbol = symbol)
}

internal fun validateEmaContinuationWatchlistStudyRequest(
    request: EmaContinuationWatchlistStudyRequest,
): EmaContinuationWatchlistStudyRequest {
    val watchlistKey = request.watchlistKey?.trim().orEmpty()
    require(watchlistKey.isNotBlank()) { "watchlistKey is required." }
    return request.copy(watchlistKey = watchlistKey)
}
