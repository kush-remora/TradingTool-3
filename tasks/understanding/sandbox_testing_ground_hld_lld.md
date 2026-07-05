# Sandbox Testing Ground - HLD & LLD

## Overview
This document outlines the High-Level Design (HLD) and Low-Level Design (LLD) for the Wyckoff Sandbox Testing Ground. The sandbox is a standalone Kotlin script/module designed to ingest static CSVs (the BHEL Case Study) and validate core forensics math (clustering logic, shape regression, and Nitin Ranjan footprint math) before committing to the full system architecture.

## 1. High-Level Design (HLD)

### 1.1 Objective
Create an isolated, easily runnable Kotlin module that simulates the Backward Flow Pipeline and Forward Flow Pipeline using static CSV files. 

### 1.2 Core Components

1. **Data Ingestion Layer (CSV Parsers)**
   - **`EodDataParser`**: Reads `bhel_raw_eod.csv` to build a historical time-series of price, volume, and delivery data.
   - **`ChartinkHitParser`**: Reads `mock_chartink_hits.csv` to extract specific trigger dates (Phase B/C events, Momentum hits).

2. **The Forensics Engine (Core Logic)**
   - **`ClusteringEngine`**: Groups Chartink hit dates looking backward. If the gap between triggers is ≤ 15 trading days, they are chained into a single base to calculate Base Duration.
   - **`ShapeDetectionEngine`**: Runs Quadratic Regression on the clustered base to ensure it's flat or drifting downward (rejects Inverted U distribution).
   - **`FootprintCalculator`**: Implements the Nitin Ranjan filter to extract institutional action:
     - 🟢 Green (Vol > 50 SMA on Up-day)
     - 🔵 Blue (Pocket Pivots)
     - 🔴 Red (Vol > 50 SMA on Down-day)
   - **`DeliveryInverseCheck`**: Validates delivery spikes on volume "dry-up" days (down-days), proving institutional limit-order absorption.

3. **Execution & Validation Flow (The Sandbox Script)**
   - A standalone `main` function (or test suite) that orchestrates the flow:
     - Load CSVs -> Run Clustering -> Run Regression -> Run Footprint & Delivery Checks -> Output formatted results to console.

---

## 2. Low-Level Design (LLD)

### 2.1 Domain Models

```kotlin
data class EodCandle(
    val date: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val deliveryVolume: Long,
    val deliveryPercentage: Double,
    val sma50Volume: Long? = null // Populated after initial load
)

data class ChartinkHit(
    val date: LocalDate,
    val hitType: HitType // e.g., IGNITION, MOMENTUM, PHASE_C_DRY_UP
)

enum class HitType {
    IGNITION, MOMENTUM, PHASE_C_DRY_UP, LVQ, LV100, LVY
}

data class ClusteredBase(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val triggers: List<ChartinkHit>,
    val candles: List<EodCandle>,
    val baseDurationDays: Int
)

enum class FootprintType {
    GREEN_INSTITUTIONAL, // Vol > 50 SMA on Up-day
    BLUE_POCKET_PIVOT,   // Pocket Pivot
    RED_INSTITUTIONAL,   // Vol > 50 SMA on Down-day
    NEUTRAL
}
```

### 2.2 Component Interfaces & Signatures

#### 1. Data Ingestion
```kotlin
object CsvIngestor {
    fun parseEodData(filePath: String): List<EodCandle>
    fun parseChartinkHits(filePath: String): List<ChartinkHit>
}
```

#### 2. Clustering Engine
```kotlin
object ClusteringEngine {
    // Configurable gap limit (e.g., 15 trading days)
    fun buildClusters(
        hits: List<ChartinkHit>, 
        eodData: List<EodCandle>, 
        maxGapDays: Int = 15
    ): List<ClusteredBase>
}
```

#### 3. Mathematical & Shape Forensics
```kotlin
object ShapeDetectionEngine {
    // Returns true if the regression is flat or drifting downwards (no inverted U)
    fun isBaseShapeValid(base: ClusteredBase): Boolean
    
    // Internal implementation of Quadratic Regression over Close Prices
    private fun calculateQuadraticRegression(prices: List<Double>): RegressionResult
}

object DeliveryForensics {
    // Verifies delivery spikes on dry-up down-days
    fun hasInstitutionalAbsorption(base: ClusteredBase, hit: ChartinkHit): Boolean
}
```

#### 4. The Nitin Ranjan Footprint Evaluator
```kotlin
object FootprintCalculator {
    // Calculates the institutional footprint array for a given base
    fun calculateFootprints(base: ClusteredBase): Map<LocalDate, FootprintType>
}
```

### 2.3 `ta4j` Constraints & Technical Details
- **SMA 50 Volume:** Following the `technical-analysis-ta4j` guidelines, we will NOT use manual loops to calculate the 50-day SMA for volume.
- We will map the `List<EodCandle>` into a `BaseBarSeriesBuilder` from `ta4j-core`.
- Apply a `VolumeIndicator` followed by an `SMAIndicator(volumeIndicator, 50)`.
- **Warmup Data:** The `EodDataParser` must load at least 250 rows prior to the first Chartink hit to allow SMA 50 and any other indicators to converge mathematically.

### 2.4 Execution Output
The sandbox will output a console summary similar to:
```text
--- BHEL Sandbox Execution ---
Found 1 Cluster Base: [2023-01-10 -> 2023-03-25]
- Base Duration: 52 Trading Days
- Triggers inside Base: 4 (IGNITION, PHASE_C_DRY_UP, ...)
- Shape Regression: VALID (Drifting Downward)
- Footprint Analysis: 
    🟢 Green Days: 4
    🔴 Red Days: 1
    🔵 Pocket Pivots: 2
- Delivery Absorption on Dry-up: VERIFIED
------------------------------
STATUS: READY FOR PHASE D EXECUTION
```

## 3. Next Steps (Implementation Phase)
1. Set up the standalone Kotlin Sandbox Module (`sandbox/`).
2. Add `ta4j-core` dependency to the Sandbox module (or use the one in `core/pom.xml` if using maven/gradle).
3. Implement `CsvIngestor`.
4. Implement `ClusteringEngine` & `ta4j` integrations for Volume SMA.
5. Implement `ShapeDetectionEngine` (Quadratic Regression).
6. Execute the BHEL Case Study and assert output validity.
