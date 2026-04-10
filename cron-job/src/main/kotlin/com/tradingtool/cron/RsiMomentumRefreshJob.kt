package com.tradingtool.cron

import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.http.HttpClientConfig
import com.tradingtool.core.http.JdkHttpClientImpl
import com.tradingtool.core.http.Result
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.http.HttpClient as JdkHttpClient
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("RsiMomentumRefreshJob")

private data class StrategyRefreshConfig(
    val renderUrl: String,
)

private object StrategyRefreshConstants {
    const val HEALTH_PATH = "/health"
    const val REFRESH_PATH = "/api/strategy/rsi-momentum/refresh"
}

fun main() {
    val config = StrategyRefreshConfig(
        renderUrl = ConfigLoader.get("RENDER_SERVICE_URL", "deployment.renderExternalUrl"),
    )
    val httpClient = JdkHttpClientImpl(JdkHttpClient.newBuilder().build(), HttpClientConfig())

    runBlocking {
        wakeService(config.renderUrl, httpClient)

        when (val response = httpClient.post(config.renderUrl.toRefreshUrl())) {
            is Result.Success -> {
                log.info("RSI momentum refresh completed successfully: {}", response.data)
                exitProcess(0)
            }
            is Result.Failure -> {
                log.error("RSI momentum refresh failed: {}", response.error.describe())
                exitProcess(1)
            }
        }
    }
}

private suspend fun wakeService(
    renderUrl: String,
    httpClient: JdkHttpClientImpl,
) {
    when (val response = httpClient.get(renderUrl.toHealthUrl())) {
        is Result.Success -> log.info("Wake check succeeded")
        is Result.Failure -> log.warn("Wake check failed: {}", response.error.describe())
    }
}

private fun String.toHealthUrl(): String = this.trimEnd('/') + StrategyRefreshConstants.HEALTH_PATH

private fun String.toRefreshUrl(): String = this.trimEnd('/') + StrategyRefreshConstants.REFRESH_PATH

