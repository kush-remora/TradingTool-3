package com.tradingtool.core.watchlist

import com.tradingtool.core.model.watchlist.ComputedIndicators
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ComputedIndicatorsSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test serialization and deserialization of ComputedIndicators`() {
        val original = ComputedIndicators(
            instrumentToken = 123456,
            sma50 = 100.0,
            sma200 = 105.5,
            rsi14 = 45.0,
            computedAt = System.currentTimeMillis()
        )

        val serialized = json.encodeToString(original)
        println("Serialized: $serialized")

        val deserialized = json.decodeFromString<ComputedIndicators>(serialized)
        assertEquals(original.instrumentToken, deserialized.instrumentToken)
        assertEquals(original.sma50, deserialized.sma50)
        assertEquals(original.sma200, deserialized.sma200)
        assertEquals(original.rsi14, deserialized.rsi14)
        assertEquals(original.computedAt, deserialized.computedAt)
    }

    @Test
    fun `test serialization and deserialization of List of ComputedIndicators`() {
        val list = listOf(
            ComputedIndicators(instrumentToken = 1, computedAt = 1000),
            ComputedIndicators(instrumentToken = 2, computedAt = 2000)
        )

        val serialized = json.encodeToString(list)
        println("List Serialized: $serialized")

        val deserialized = json.decodeFromString<List<ComputedIndicators>>(serialized)
        assertEquals(2, deserialized.size)
        assertEquals(1, deserialized[0].instrumentToken)
        assertEquals(2, deserialized[1].instrumentToken)
    }
}
