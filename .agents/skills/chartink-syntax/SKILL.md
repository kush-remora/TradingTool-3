---
name: chartink-syntax
description: Understanding and writing Chartink screener syntax for technical analysis.
---

# Chartink Screener Syntax Guide

This skill helps you interpret and write Chartink screener rules. Chartink is a popular technical screener in India.

## Basic Structure
- Rules are typically written as comparisons between two indicators or price points.
- **Timeframes**: `Latest` means the current candle. `1 day ago`, `2 days ago`, etc., refer to previous candles. You can also specify weekly or monthly timeframes (e.g., `Latest Weekly Close`).
- **Logical Operators**: 
  - `All of the following` (AND logic)
  - `Any of the following` (OR logic)

## Common Price and Volume Variables
- `Close`, `Open`, `High`, `Low`, `Volume`

## Common Indicators
- `SMA(close, 20)`: Simple Moving Average of closing prices over 20 periods.
- `RSI(14)`: Relative Strength Index.
- `MACD(12, 26, 9)`: MACD indicator.

## Advanced Functions
- **Count(condition, periods)**: Counts how many times a condition was true over the last X periods. Example: `Count(Close > Open, 10)` counts how many green candles in the last 10 days.
- **Countstreak(condition)**: Counts the number of *consecutive* periods a condition has been true ending at the current period.
- **Greatest(periods, variable)** or `Max`: Finds the highest value of a variable over X periods. Example: `Max(High, 30)` for the 30-day high.
- **Least(periods, variable)** or `Min`: Finds the lowest value over X periods.
- **Abs(value)**: Absolute value.

## Reading Chartink Code
Chartink code reads almost like English. 
Example: `Latest Close > 1 day ago High` means today's closing price is greater than yesterday's highest price.
