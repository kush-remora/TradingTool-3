package com.tradingtool.core.candle.dao

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.candle.IntradayCandle
import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlBatch

interface CandleWriteDao {

    @SqlBatch(
        """
        INSERT INTO public.${Tables.DAILY_CANDLES}
            (instrument_token, symbol, candle_date, open, high, low, close, volume)
        VALUES
            (:instrumentToken, :symbol, :candleDate, :open, :high, :low, :close, :volume)
        ON CONFLICT (instrument_token, candle_date) DO UPDATE SET
            open   = EXCLUDED.open,
            high   = EXCLUDED.high,
            low    = EXCLUDED.low,
            close  = EXCLUDED.close,
            volume = EXCLUDED.volume
        """
    )
    fun upsertDailyCandles(@BindBean candles: List<DailyCandle>): IntArray

    @SqlBatch(
        """
        INSERT INTO public.${Tables.INTRADAY_CANDLES}
            (instrument_token, symbol, interval, candle_timestamp, open, high, low, close, volume)
        VALUES
            (:instrumentToken, :symbol, :interval, :candleTimestamp, :open, :high, :low, :close, :volume)
        ON CONFLICT (instrument_token, interval, candle_timestamp) DO UPDATE SET
            open   = EXCLUDED.open,
            high   = EXCLUDED.high,
            low    = EXCLUDED.low,
            close  = EXCLUDED.close,
            volume = EXCLUDED.volume
        """
    )
    fun upsertIntradayCandles(@BindBean candles: List<IntradayCandle>): IntArray
}
