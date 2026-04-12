package com.tradingtool.core.strategy.s4

import com.tradingtool.core.strategy.rsimomentum.UniverseMember
import com.tradingtool.core.strategy.volume.VolumeAnalysisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class S4RankerTest {
    private val profile = S4ProfileConfig(
        id = "largemidcap250",
        label = "LargeMidcap250",
        baseUniversePreset = S4ProfileConfig.DEFAULT_BASE_UNIVERSE_PRESET,
    )

    @Test
    fun `qualify returns fresh spike when today volume leads`() {
        val qualified = S4Ranker.qualify(candidateInput(analysis = analysis(todayVolumeRatio = 2.5, recent3dAvgVolumeRatio = 1.4, recent5dMaxVolumeRatio = 2.5, spikePersistenceDays5d = 1, recent10dAvgVolumeRatio = 1.35, elevatedVolumeDays10d = 2, todayPriceChangePct = 2.0, priceReturn3dPct = 2.5, breakout = false)))
        assertNotNull(qualified)
        assertEquals("FRESH_SPIKE", qualified?.classification)
    }

    @Test
    fun `qualify returns buildup breakout when recent regime qualifies`() {
        val qualified = S4Ranker.qualify(candidateInput(analysis = analysis(todayVolumeRatio = 1.4, recent3dAvgVolumeRatio = 2.0, recent5dMaxVolumeRatio = 2.1, spikePersistenceDays5d = 3, recent10dAvgVolumeRatio = 1.9, elevatedVolumeDays10d = 5, todayPriceChangePct = 1.6, priceReturn3dPct = 3.5, breakout = false)))
        assertNotNull(qualified)
        assertEquals("BUILDUP_BREAKOUT", qualified?.classification)
    }

    @Test
    fun `qualify returns extended spike when persistence is very high`() {
        val qualified = S4Ranker.qualify(candidateInput(analysis = analysis(todayVolumeRatio = 2.4, recent3dAvgVolumeRatio = 2.1, recent5dMaxVolumeRatio = 2.8, spikePersistenceDays5d = 5, recent10dAvgVolumeRatio = 2.2, elevatedVolumeDays10d = 8, todayPriceChangePct = 2.2, priceReturn3dPct = 4.1, breakout = true)))
        assertNotNull(qualified)
        assertEquals("EXTENDED_SPIKE", qualified?.classification)
    }

    @Test
    fun `qualify blocks raw volume noise without price confirmation`() {
        val qualified = S4Ranker.qualify(candidateInput(analysis = analysis(todayVolumeRatio = 3.0, recent3dAvgVolumeRatio = 2.0, recent5dMaxVolumeRatio = 3.0, spikePersistenceDays5d = 3, recent10dAvgVolumeRatio = 2.0, elevatedVolumeDays10d = 6, todayPriceChangePct = 0.4, priceReturn3dPct = 1.0, breakout = false)))
        assertNull(qualified)
    }

    @Test
    fun `rank prefers stronger recent regime over marginal candidate`() {
        val strongest = S4Ranker.qualify(candidateInput(symbol = "AAA", analysis = analysis(todayVolumeRatio = 2.2, recent3dAvgVolumeRatio = 2.4, recent5dMaxVolumeRatio = 2.4, spikePersistenceDays5d = 3, recent10dAvgVolumeRatio = 1.9, elevatedVolumeDays10d = 5, todayPriceChangePct = 2.0, priceReturn3dPct = 3.5, breakout = true)))!!
        val weaker = S4Ranker.qualify(candidateInput(symbol = "BBB", analysis = analysis(todayVolumeRatio = 2.0, recent3dAvgVolumeRatio = 1.8, recent5dMaxVolumeRatio = 2.0, spikePersistenceDays5d = 2, recent10dAvgVolumeRatio = 1.5, elevatedVolumeDays10d = 3, todayPriceChangePct = 1.5, priceReturn3dPct = 3.0, breakout = false)))!!
        val orderedUniverseSymbols = (1..250).map { index ->
            when (index) {
                120 -> "AAA"
                240 -> "BBB"
                else -> "SYM$index"
            }
        }

        val ranked = S4Ranker.rank(
            profileId = profile.id,
            baseUniversePreset = profile.baseUniversePreset,
            candidates = listOf(weaker, strongest),
            candidateCount = 10,
            orderedUniverseSymbols = orderedUniverseSymbols,
        )

        assertEquals(listOf("AAA", "BBB"), ranked.map { it.symbol })
        assertEquals("BUILDUP_BREAKOUT", ranked.first().classification)
        assertEquals(120, ranked.first().indexRank)
        assertEquals(250, ranked.first().indexSize)
        assertEquals("Top 150", ranked.first().indexLayer)
        assertEquals(22.0, ranked.first().todayVolumeScore)
        assertEquals(20.0, ranked.first().recent3dVolumeScore)
        assertEquals(9.0, ranked.first().persistenceScore)
        assertEquals(15.0, ranked.first().priceScore)
        assertEquals(66.0, ranked.first().score)
        assertEquals(240, ranked.last().indexRank)
        assertEquals("Bottom 50", ranked.last().indexLayer)
    }

    @Test
    fun `qualify reclassifies soft multi day buildup as buildup breakout instead of fresh spike`() {
        val qualified = S4Ranker.qualify(candidateInput(analysis = analysis(todayVolumeRatio = 2.6, recent3dAvgVolumeRatio = 1.9, recent5dMaxVolumeRatio = 2.6, spikePersistenceDays5d = 1, recent10dAvgVolumeRatio = 1.82, elevatedVolumeDays10d = 5, todayPriceChangePct = 2.3, priceReturn3dPct = 4.0, breakout = true)))
        assertNotNull(qualified)
        assertEquals("BUILDUP_BREAKOUT", qualified?.classification)
    }

    private fun candidateInput(symbol: String = "AAA", analysis: VolumeAnalysisResult): S4CandidateInput = S4CandidateInput(
        member = UniverseMember(symbol = symbol, instrumentToken = 1L, companyName = "$symbol Ltd", inBaseUniverse = true, inWatchlist = false),
        profile = profile,
        analysis = analysis,
    )

    private fun analysis(
        todayVolumeRatio: Double,
        recent3dAvgVolumeRatio: Double,
        recent5dMaxVolumeRatio: Double,
        spikePersistenceDays5d: Int,
        recent10dAvgVolumeRatio: Double,
        elevatedVolumeDays10d: Int,
        todayPriceChangePct: Double,
        priceReturn3dPct: Double,
        breakout: Boolean,
    ): VolumeAnalysisResult = VolumeAnalysisResult(
        asOfDate = LocalDate.of(2026, 4, 12),
        close = 120.0,
        avgVolume20d = 100.0,
        avgTradedValueCr20d = 20.0,
        todayVolumeRatio = todayVolumeRatio,
        recent3dAvgVolumeRatio = recent3dAvgVolumeRatio,
        recent5dMaxVolumeRatio = recent5dMaxVolumeRatio,
        spikePersistenceDays5d = spikePersistenceDays5d,
        recent10dAvgVolumeRatio = recent10dAvgVolumeRatio,
        elevatedVolumeDays10d = elevatedVolumeDays10d,
        todayPriceChangePct = todayPriceChangePct,
        priceReturn3dPct = priceReturn3dPct,
        breakoutAbove20dHigh = breakout,
    )
}
