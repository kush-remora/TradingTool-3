#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import datetime as dt
import os
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Sequence, Tuple
from urllib.parse import parse_qs, urlparse


@dataclass(frozen=True)
class Candle:
    date: dt.date
    close: float


@dataclass(frozen=True)
class RsiPoint:
    date: dt.date
    close: float
    rsi14: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify 1Y RSI-14 low from daily_candles.")
    parser.add_argument("--symbol", default="JBCHEPHARM", help="NSE symbol (default: JBCHEPHARM)")
    parser.add_argument("--from-date", default="2025-04-21", help="From date YYYY-MM-DD")
    parser.add_argument("--to-date", default="2026-04-21", help="To date YYYY-MM-DD")
    return parser.parse_args()


def load_jdbc_url() -> str:
    env_url = os.getenv("SUPABASE_DB_URL", "").strip()
    if env_url:
        return env_url

    local_config = Path("service/src/main/resources/localconfig.yaml")
    if not local_config.exists():
        raise RuntimeError("SUPABASE_DB_URL is not set and localconfig.yaml was not found.")

    content = local_config.read_text(encoding="utf-8")
    match = re.search(r'^\s*dbUrl:\s*"([^"]+)"\s*$', content, flags=re.MULTILINE)
    if not match:
        raise RuntimeError("Could not find supabase.dbUrl in localconfig.yaml")
    return match.group(1)


def jdbc_to_postgres_uri(jdbc_url: str) -> str:
    if not jdbc_url.startswith("jdbc:postgresql://"):
        raise RuntimeError("Unsupported JDBC URL format for PostgreSQL.")

    parsed = urlparse(jdbc_url.replace("jdbc:", "", 1))
    params = parse_qs(parsed.query)

    user = params.get("user", [""])[0]
    password = params.get("password", [""])[0]
    sslmode = params.get("sslmode", ["require"])[0]

    if not user or not password:
        raise RuntimeError("JDBC URL does not contain user/password query params.")

    host = parsed.hostname or ""
    port = parsed.port or 5432
    database = parsed.path.lstrip("/")
    if not host or not database:
        raise RuntimeError("JDBC URL is missing host or database.")

    return f"postgresql://{user}:{password}@{host}:{port}/{database}?sslmode={sslmode}"


def normalize_symbol(symbol: str) -> str:
    cleaned = symbol.strip().upper()
    if not re.fullmatch(r"[A-Z0-9&\-.]+", cleaned):
        raise RuntimeError(f"Unsupported symbol format: {symbol}")
    return cleaned


def fetch_candles(
    postgres_uri: str,
    symbol: str,
    from_date: dt.date,
    to_date: dt.date,
) -> List[Candle]:
    query = (
        "SELECT candle_date, close "
        "FROM public.daily_candles "
        f"WHERE symbol = '{symbol}' "
        f"AND candle_date BETWEEN '{from_date.isoformat()}' AND '{to_date.isoformat()}' "
        "ORDER BY candle_date ASC;"
    )

    cmd: Sequence[str] = [
        "psql",
        postgres_uri,
        "-X",
        "-A",
        "-F,",
        "-q",
        "-t",
        "-c",
        query,
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise RuntimeError(f"psql query failed: {result.stderr.strip()}")

    rows = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    candles: List[Candle] = []
    for row in csv.reader(rows):
        if len(row) != 2:
            continue
        candle_date = dt.date.fromisoformat(row[0])
        close = float(row[1])
        candles.append(Candle(date=candle_date, close=close))
    return candles


def compute_rsi14_wilder(candles: Sequence[Candle], period: int = 14) -> List[Optional[float]]:
    n = len(candles)
    if n == 0:
        return []

    rsi: List[Optional[float]] = [None] * n
    if n <= period:
        return rsi

    deltas: List[float] = [candles[i].close - candles[i - 1].close for i in range(1, n)]
    first_window = deltas[:period]
    avg_gain = sum(max(delta, 0.0) for delta in first_window) / period
    avg_loss = sum(max(-delta, 0.0) for delta in first_window) / period

    rsi[period] = 100.0 if avg_loss == 0.0 else 100.0 - (100.0 / (1.0 + (avg_gain / avg_loss)))

    for idx in range(period + 1, n):
        delta = deltas[idx - 1]
        gain = max(delta, 0.0)
        loss = max(-delta, 0.0)
        avg_gain = ((avg_gain * (period - 1)) + gain) / period
        avg_loss = ((avg_loss * (period - 1)) + loss) / period
        rsi[idx] = 100.0 if avg_loss == 0.0 else 100.0 - (100.0 / (1.0 + (avg_gain / avg_loss)))

    return rsi


def find_lowest_rsi_point(candles: Sequence[Candle], rsi_values: Sequence[Optional[float]]) -> Optional[RsiPoint]:
    points: List[RsiPoint] = []
    for candle, rsi in zip(candles, rsi_values):
        if rsi is None:
            continue
        points.append(RsiPoint(date=candle.date, close=candle.close, rsi14=rsi))
    if not points:
        return None
    return min(points, key=lambda point: point.rsi14)


def main() -> int:
    args = parse_args()
    symbol = normalize_symbol(args.symbol)
    from_date = dt.date.fromisoformat(args.from_date)
    to_date = dt.date.fromisoformat(args.to_date)
    if from_date > to_date:
        raise RuntimeError("--from-date must be <= --to-date")

    jdbc_url = load_jdbc_url()
    postgres_uri = jdbc_to_postgres_uri(jdbc_url)
    candles = fetch_candles(postgres_uri=postgres_uri, symbol=symbol, from_date=from_date, to_date=to_date)
    rsi_values = compute_rsi14_wilder(candles, period=14)
    lowest = find_lowest_rsi_point(candles, rsi_values)

    print(f"Symbol: {symbol}")
    print(f"Period: {from_date.isoformat()} -> {to_date.isoformat()}")
    print(f"Candles loaded: {len(candles)}")

    if lowest is None:
        print("No RSI points available (insufficient candle count).")
        return 0

    print(
        "Lowest RSI-14:"
        f" date={lowest.date.isoformat()},"
        f" close={lowest.close:.2f},"
        f" rsi14={lowest.rsi14:.6f}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
