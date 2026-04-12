package com.tradingtool.core.constants

object DatabaseConstants {

    object Tables {
        const val STOCKS = "stocks"
        const val KITE_TOKENS = "kite_tokens"
        const val TRADES = "trades"
        const val STOCK_INDICATORS_SNAPSHOT = "stock_indicators_snapshot"
        const val REMORA_SIGNALS = "remora_signals"
        const val DAILY_CANDLES = "daily_candles"
        const val INTRADAY_CANDLES = "intraday_candles"
        const val STOCK_DELIVERY_DAILY = "stock_delivery_daily"
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
        const val NEEDS_REFRESH = "needs_refresh"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"

        // tags cast to text so the JDBC driver returns a plain String (not PGobject)
        const val ALL_WITH_TAGS =
            "$ID, $SYMBOL, $INSTRUMENT_TOKEN, $COMPANY_NAME, $EXCHANGE, $NOTES, $PRIORITY, $TAGS::text AS $TAGS, $CREATED_AT, $UPDATED_AT"
    }

    object StockIndicatorColumns {
        const val INSTRUMENT_TOKEN = "instrument_token"
        const val INDICATORS_PAYLOAD = "indicators_payload"
        const val COMPUTED_AT = "computed_at"
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
        const val CLOSE_PRICE = "close_price"
        const val CLOSE_DATE = "close_date"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"

        const val ALL = "$ID, $STOCK_ID, $NSE_SYMBOL, $QUANTITY, $AVG_BUY_PRICE, $TODAY_LOW, $STOP_LOSS_PERCENT, $STOP_LOSS_PRICE, $NOTES, $TRADE_DATE, $CLOSE_PRICE, $CLOSE_DATE, $CREATED_AT, $UPDATED_AT"
    }

    object StockDeliveryColumns {
        const val STOCK_ID = "stock_id"
        const val INSTRUMENT_TOKEN = "instrument_token"
        const val SYMBOL = "symbol"
        const val EXCHANGE = "exchange"
        const val UNIVERSE = "universe"
        const val TRADING_DATE = "trading_date"
        const val RECONCILIATION_STATUS = "reconciliation_status"
        const val SERIES = "series"
        const val TTL_TRD_QNTY = "ttl_trd_qnty"
        const val DELIV_QTY = "deliv_qty"
        const val DELIV_PER = "deliv_per"
        const val SOURCE_FILE_NAME = "source_file_name"
        const val SOURCE_URL = "source_url"
        const val FETCHED_AT = "fetched_at"
    }
}
