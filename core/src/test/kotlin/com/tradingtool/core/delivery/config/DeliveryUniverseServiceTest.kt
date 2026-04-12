package com.tradingtool.core.delivery.config

import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.model.stock.Stock
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DeliveryUniverseServiceTest {

    @Test
    fun `extractNseWatchlistSymbols keeps only NSE symbols`() {
        val tempDir = Files.createTempDirectory("delivery-universe-test")
        val previous = System.getProperty(DeliveryConfigService.CONFIG_FILE_PATH_PROPERTY)
        System.setProperty(
            DeliveryConfigService.CONFIG_FILE_PATH_PROPERTY,
            tempDir.resolve(DeliveryConfigService.CONFIG_FILE_NAME).toString(),
        )

        try {
            val service = DeliveryUniverseService(
                configService = DeliveryConfigService(),
                stockHandler = JdbiHandler(DatabaseConfig(""), StockReadDao::class.java, StockWriteDao::class.java),
            )

            val symbols = service.extractNseWatchlistSymbols(
                listOf(
                    stock(symbol = "RELIANCE", exchange = "NSE"),
                    stock(symbol = "SBIN", exchange = "BSE"),
                    stock(symbol = "INFY", exchange = "NSE"),
                ),
            )

            assertEquals(listOf("RELIANCE", "INFY"), symbols)
        } finally {
            if (previous == null) {
                System.clearProperty(DeliveryConfigService.CONFIG_FILE_PATH_PROPERTY)
            } else {
                System.setProperty(DeliveryConfigService.CONFIG_FILE_PATH_PROPERTY, previous)
            }
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun stock(symbol: String, exchange: String): Stock {
        return Stock(
            id = 1L,
            symbol = symbol,
            instrumentToken = 1001L,
            companyName = symbol,
            exchange = exchange,
            createdAt = "2026-04-12T00:00:00Z",
            updatedAt = "2026-04-12T00:00:00Z",
        )
    }
}
