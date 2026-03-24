package com.tradingtool.core.constants

object DatabaseConstants {

    object Tables {
        const val STOCKS = "stocks"
        const val KITE_TOKENS = "kite_tokens"
        const val TRADES = "trades"
    }

    object KiteTokenColumns {
        const val ID = "id"
        const val ACCESS_TOKEN = "access_token"
        const val CREATED_AT = "created_at"
    }

    object StockColumns {
        const val ID = "id"
        const val SYMBOL = "symbol"
        const val INSTRUMENT_TOKEN = "instrument_token"
        const val COMPANY_NAME = "company_name"
        const val EXCHANGE = "exchange"
        const val NOTES = "notes"
        const val PRIORITY = "priority"
        const val TAGS = "tags"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"

        // tags cast to text so the JDBC driver returns a plain String (not PGobject)
        const val ALL_WITH_TAGS =
            "$ID, $SYMBOL, $INSTRUMENT_TOKEN, $COMPANY_NAME, $EXCHANGE, $NOTES, $PRIORITY, $TAGS::text AS $TAGS, $CREATED_AT, $UPDATED_AT"
    }

    object TradeColumns {
        const val ID = "id"
        const val STOCK_ID = "stock_id"
        const val NSE_SYMBOL = "nse_symbol"
        const val QUANTITY = "quantity"
        const val AVG_BUY_PRICE = "avg_buy_price"
        const val TODAY_LOW = "today_low"
        const val STOP_LOSS_PERCENT = "stop_loss_percent"
        const val STOP_LOSS_PRICE = "stop_loss_price"
        const val NOTES = "notes"
        const val TRADE_DATE = "trade_date"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"

        const val ALL = "$ID, $STOCK_ID, $NSE_SYMBOL, $QUANTITY, $AVG_BUY_PRICE, $TODAY_LOW, $STOP_LOSS_PERCENT, $STOP_LOSS_PRICE, $NOTES, $TRADE_DATE, $CREATED_AT, $UPDATED_AT"
    }
}
