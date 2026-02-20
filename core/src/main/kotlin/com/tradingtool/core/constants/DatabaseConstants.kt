package com.tradingtool.core.constants

/**
 * Database table and column name constants.
 * Used by ReadDao and WriteDao to avoid hardcoding SQL identifiers.
 */
object DatabaseConstants {

    // ==================== Table Names ====================
    object Tables {
        const val STOCKS = "stocks"
        const val WATCHLISTS = "watchlists"
        const val TAGS = "tags"
        const val STOCK_TAGS = "stock_tags"
        const val WATCHLIST_TAGS = "watchlist_tags"
        const val WATCHLIST_STOCKS = "watchlist_stocks"
    }

    // ==================== Stock Columns ====================
    object StockColumns {
        const val ID = "id"
        const val SYMBOL = "symbol"
        const val INSTRUMENT_TOKEN = "instrument_token"
        const val COMPANY_NAME = "company_name"
        const val EXCHANGE = "exchange"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"

        const val ALL = "$ID, $SYMBOL, $INSTRUMENT_TOKEN, $COMPANY_NAME, $EXCHANGE, $CREATED_AT, $UPDATED_AT"
    }

    // ==================== Watchlist Columns ====================
    object WatchlistColumns {
        const val ID = "id"
        const val NAME = "name"
        const val DESCRIPTION = "description"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"

        const val ALL = "$ID, $NAME, $DESCRIPTION, $CREATED_AT, $UPDATED_AT"
    }

    // ==================== Tag Columns ====================
    object TagColumns {
        const val ID = "id"
        const val NAME = "name"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"

        const val ALL = "$ID, $NAME, $CREATED_AT, $UPDATED_AT"
    }

    // ==================== WatchlistStock Columns ====================
    object WatchlistStockColumns {
        const val ID = "id"
        const val WATCHLIST_ID = "watchlist_id"
        const val STOCK_ID = "stock_id"
        const val CREATED_AT = "created_at"

        const val ALL = "$ID, $WATCHLIST_ID, $STOCK_ID, $CREATED_AT"
    }

    // ==================== StockTag Columns ====================
    object StockTagColumns {
        const val ID = "id"
        const val STOCK_ID = "stock_id"
        const val TAG_ID = "tag_id"
        const val CREATED_AT = "created_at"

        const val ALL = "$ID, $STOCK_ID, $TAG_ID, $CREATED_AT"
    }

    // ==================== WatchlistTag Columns ====================
    object WatchlistTagColumns {
        const val ID = "id"
        const val WATCHLIST_ID = "watchlist_id"
        const val TAG_ID = "tag_id"
        const val CREATED_AT = "created_at"

        const val ALL = "$ID, $WATCHLIST_ID, $TAG_ID, $CREATED_AT"
    }
}