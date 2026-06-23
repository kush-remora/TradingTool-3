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
        const val MARKETCAPNAME = "marketcapname"
        const val CLOSE_PRICE = "close_price"
        const val PCT_CHANGE = "pct_change"
        const val VOLUME = "volume"
        const val SECTOR = "sector"
        const val INDUSTRY = "industry"
        const val ROCE = "roce"
        const val RONW = "ronw"
        const val NET_PROFIT_3Q_AGO = "net_profit_3q_ago"
        const val DEBT_EQUITY = "debt_equity"
        const val VOL_DRY_200_MIN = "vol_dry_200_min"
        const val VOL_DRY_60_MIN = "vol_dry_60_min"
        const val VOL_DRY_200_MIN_1_05 = "vol_dry_200_min_1_05"
        const val VOL_DRY_60_MIN_1_05 = "vol_dry_60_min_1_05"
        const val PROMOTER_HOLDING = "promoter_holding"
        const val FOREIGN_PROMOTER_HOLDING = "foreign_promoter_holding"
        const val GROSS_SALES = "gross_sales"
        const val HIGH_252D = "high_252d"
        const val MIN_20D_HIGH = "min_20d_high"
        const val DIST_200D_HIGH = "dist_200d_high"
        const val BRACKETS2 = "brackets2"
        const val ATR_COUNT = "atr_count"
    }
}
