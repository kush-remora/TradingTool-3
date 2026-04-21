package com.tradingtool.core.strategy.rsimomentum

import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SimpleMomentumBacktestPrepServiceTest {

    private val service = SimpleMomentumBacktestPrepService(
        configService = RsiMomentumConfigService(),
        candleDataService = allocateWithoutConstructor(),
        backfillService = allocateWithoutConstructor(),
        kiteClient = allocateWithoutConstructor(),
    )

    @Test
    fun `prepare rejects reversed date range before downstream work`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.prepare(
                    SimpleMomentumBacktestPrepareRequest(
                        profileId = "largemidcap250",
                        fromDate = "2026-01-10",
                        toDate = "2026-01-01",
                    ),
                )
            }
        }
    }

    @Test
    fun `prepare rejects unknown profile`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.prepare(
                    SimpleMomentumBacktestPrepareRequest(
                        profileId = "unknown-profile",
                        fromDate = "2026-01-01",
                        toDate = "2026-01-10",
                    ),
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> allocateWithoutConstructor(): T {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(T::class.java) as T
    }
}
