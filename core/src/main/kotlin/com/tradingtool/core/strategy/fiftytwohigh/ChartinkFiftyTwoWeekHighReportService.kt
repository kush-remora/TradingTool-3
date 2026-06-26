package com.tradingtool.core.strategy.fiftytwohigh

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

class ChartinkFiftyTwoWeekHighReportService(
    private val objectMapper: ObjectMapper,
    private val reportPath: Path = DEFAULT_REPORT_PATH,
) {

    fun loadLatestReport(): ChartinkFiftyTwoWeekHighBacktestReport {
        require(Files.exists(reportPath)) {
            "Backtest report not found at $reportPath. Run the Chartink 52-week-high backtest job first."
        }

        return objectMapper.readValue(reportPath.toFile(), ChartinkFiftyTwoWeekHighBacktestReport::class.java)
    }

    companion object {
        val DEFAULT_REPORT_PATH: Path = Path.of(
            "build",
            "reports",
            "chartink-fiftytwo-week-high-backtest",
            "latest.json",
        )
    }
}
