package com.tradingtool.cron

import com.tradingtool.core.model.telegram.TelegramSendTextRequest
import com.tradingtool.core.telegram.TelegramSender
import com.tradingtool.core.screener.BollingerSqueezeService
import com.tradingtool.core.screener.SqueezePositionInput
import com.tradingtool.core.trade.service.TradeService
import com.tradingtool.di.ServiceModule
import com.tradingtool.config.DropwizardConfig
import com.tradingtool.core.config.ConfigLoader
import com.google.inject.Guice
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess
import com.tradingtool.core.screener.BollingerSqueezeScanResult
import com.tradingtool.core.model.trade.TradeWithTargets

private val log = LoggerFactory.getLogger("BollingerSqueezeAlertJob")

/**
 * Daily job to send Bollinger Squeeze updates to Telegram.
 * 1. Scans the watchlist for new breakouts/setups.
 * 2. Scans open trades for SL updates.
 */
class BollingerSqueezeAlertJob {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // DropwizardConfig doesn't have a static load(), but we can mimic DropwizardApplication's logic
            // or just use ServiceModule with a dummy config if only certain parts are needed.
            // However, ServiceModule requires a full AppConfig.
            
            val appConfig = DropwizardConfig().toAppConfig() // Uses defaults + some env vars
            val injector = Guice.createInjector(ServiceModule(appConfig))
            
            val squeezeService = injector.getInstance(BollingerSqueezeService::class.java)
            val tradeService = injector.getInstance(TradeService::class.java)
            val telegramSender = injector.getInstance(TelegramSender::class.java)

            runBlocking {
                try {
                    log.info("Starting Bollinger Squeeze Alert Job...")
                    
                    // 1. Scan for new setups (Watchlist)
                    val scanResults: List<BollingerSqueezeScanResult> = squeezeService.analyze(listOf("WATCHLIST"))
                    val highPriorityAlerts = scanResults.filter { it.alertStatus == "DAY_1_ALERT" || it.alertStatus == "TRIGGERED_TODAY" }
                    
                    if (highPriorityAlerts.isNotEmpty()) {
                        val setupMsg = StringBuilder("🔔 *Bollinger Squeeze Alerts*\n\n")
                        highPriorityAlerts.forEach { res ->
                            val emoji = if (res.alertStatus == "TRIGGERED_TODAY") "🚀" else "⏳"
                            val statusText = res.alertStatus.replace("_", " ")
                            setupMsg.append("$emoji *${res.symbol}*: $statusText\n")
                            setupMsg.append("Price: ₹${res.ltp} | RSI: ${res.currentRsi}\n\n")
                        }
                        telegramSender.sendText(TelegramSendTextRequest(setupMsg.toString()))
                    }

                    // 2. Scan open trades for SL updates
                    val allTrades: List<TradeWithTargets> = tradeService.getTradesWithTargets()
                    val openTrades = allTrades.filter { it.trade.closePrice == null }
                    if (openTrades.isNotEmpty()) {
                        val positions = openTrades.map { t ->
                            SqueezePositionInput(t.trade.nseSymbol, t.trade.tradeDate, t.trade.avgBuyPrice.toDouble())
                        }
                        val trackResponse = squeezeService.track(positions)
                        
                        val choreMsg = StringBuilder("📋 *Daily GTT Update Required*\n\n")
                        trackResponse.results.forEach { res ->
                            choreMsg.append("*${res.symbol}* (Phase: ${res.currentPhase})\n")
                            choreMsg.append("➡️ Move GTT to: *₹${res.requiredSl}*\n")
                            choreMsg.append("Profit: ${res.profitPct}% | LTP: ₹${res.ltp}\n\n")
                        }
                        telegramSender.sendText(TelegramSendTextRequest(choreMsg.toString()))
                    }

                    log.info("Bollinger Squeeze Alert Job completed successfully.")
                    exitProcess(0)
                } catch (e: Exception) {
                    log.error("Job failed: {}", e.message, e)
                    exitProcess(1)
                }
            }
        }
    }
}
