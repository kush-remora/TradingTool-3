package com.tradingtool.core.screener

import com.tradingtool.core.model.watchlist.WatchlistRow

data class DrawdownScannerResult(
    val universe: String,
    val count: Int,
    val results: List<WatchlistRow>,
    val computedAt: Long = System.currentTimeMillis()
)
