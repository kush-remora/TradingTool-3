package com.tradingtool.core.constants

object DatabaseConstants {

    object Tables {
        const val KITE_TOKENS = "kite_tokens"
        const val TRADES = "trades"
        const val DAILY_CANDLES = "daily_candles"
        const val INTRADAY_CANDLES = "intraday_candles"
        const val STOCK_DELIVERY_DAILY = "stock_delivery_daily"
        const val INDEX_CONSTITUENTS = "index_constituents"
        const val GROWW_VOLUME_SHOCKER_DAILY = "groww_volume_shocker_daily"
        const val PHASE_C_WATCHLIST = "phase_c_watchlist"
    }

    object KiteTokenColumns {
        const val ID = "id"
        const val ACCESS_TOKEN = "access_token"
        const val CREATED_AT = "created_at"
    }

    object TradeColumns {
        const val ID = "id"
        const val INSTRUMENT_TOKEN = "instrument_token"
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
        const val STRATEGY = "strategy"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"

        const val ALL = "$ID, $INSTRUMENT_TOKEN, $NSE_SYMBOL, $QUANTITY, $AVG_BUY_PRICE, $TODAY_LOW, $STOP_LOSS_PERCENT, $STOP_LOSS_PRICE, $NOTES, $TRADE_DATE, $CLOSE_PRICE, $CLOSE_DATE, $STRATEGY, $CREATED_AT, $UPDATED_AT"
    }

    object StockDeliveryColumns {
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



    object IndexConstituentColumns {
        const val INDEX_KEY = "index_key"
        const val SYMBOL = "symbol"
        const val INSTRUMENT_TOKEN = "instrument_token"
        const val COMPANY_NAME = "company_name"
        const val INDUSTRY = "industry"
        const val SERIES = "series"
        const val ISIN_CODE = "isin_code"
        const val IS_ACTIVE = "is_active"
        const val SOURCE_URL = "source_url"
        const val LAST_SYNCED_AT = "last_synced_at"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
    }

    object GrowwVolumeShockerColumns {
        const val TRADE_DATE = "trade_date"
        const val SOURCE_RANK = "source_rank"
        const val EXCHANGE = "exchange"
        const val SYMBOL = "symbol"
        const val INSTRUMENT_TOKEN = "instrument_token"
        const val COMPANY_NAME = "company_name"
        const val LTP = "ltp"
        const val CLOSE = "close"
        const val MARKET_CAP_CRORE = "market_cap_crore"
        const val MARKET_CAP_CATEGORY = "market_cap_category"
        const val YEAR_LOW = "year_low"
        const val YEAR_HIGH = "year_high"
        const val VOLUME = "volume"
        const val WEEKLY_AVERAGE_VOLUME = "weekly_average_volume"
    }

    object PhaseCWatchlistColumns {
        const val SYMBOL = "symbol"
        const val INSTRUMENT_TOKEN = "instrument_token"
        const val ADDED_ON = "added_on"
        const val LAST_SEEN_ON = "last_seen_on"
        const val STATUS = "status"
        const val STOCK_NAME = "stock_name"
        const val MARKET_CAP_BUCKET = "market_cap_bucket"
        const val CLOSE_PRICE = "close_price"
        const val PCT_CHANGE = "pct_change"
        const val VOLUME = "volume"
        const val SECTOR = "sector"
        const val INDUSTRY = "industry"
        const val ROCE_PCT = "roce_pct"
        const val RONW_PCT = "ronw_pct"
        const val NET_PROFIT_AFTER_TAX = "net_profit_after_tax"
        const val DEBT_EQUITY_RATIO = "debt_equity_ratio"
        const val VOL_DRY_200D_MIN_COUNT = "vol_dry_200d_min_count"
        const val VOL_DRY_60D_MIN_COUNT = "vol_dry_60d_min_count"
        const val VOL_DRY_200D_MIN_105_COUNT = "vol_dry_200d_min_105_count"
        const val VOL_DRY_60D_MIN_105_COUNT = "vol_dry_60d_min_105_count"
        const val INDIAN_PROMOTER_PCT = "indian_promoter_pct"
        const val FOREIGN_PROMOTER_PCT = "foreign_promoter_pct"
        const val QUARTERLY_GROSS_SALES = "quarterly_gross_sales"
        const val HIGH_52W = "high_52w"
        const val LOW_52W = "low_52w"
        const val DIST_200D_HIGH_PCT = "dist_200d_high_pct"
        const val DIST_200D_LOW_PCT = "dist_200d_low_pct"
        const val ATR_LT_2PCT_COUNT = "atr_lt_2pct_count"
    }
}
