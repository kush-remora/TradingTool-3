package com.tradingtool.core.strategy.s4

import com.tradingtool.core.strategy.rsimomentum.UniverseBuildResult
import com.tradingtool.core.strategy.rsimomentum.UniverseMember
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class S4ServiceLogicTest {
    @Test
    fun `profile analysis keeps current analyzed date even when no candidates qualify`() {
        val analysis = S4ProfileAnalysis(
            universe = UniverseBuildResult(
                members = listOf(
                    UniverseMember(
                        symbol = "AAA",
                        instrumentToken = 1L,
                        companyName = "AAA Ltd",
                        inBaseUniverse = true,
                        inWatchlist = false,
                    ),
                ),
                unresolvedSymbols = emptyList(),
                baseUniverseCount = 1,
                watchlistCount = 0,
                watchlistAdditionsCount = 0,
            ),
            qualifiedCandidates = emptyList(),
            latestAnalyzedDate = LocalDate.of(2026, 4, 12),
            insufficientHistorySymbols = emptyList(),
            illiquidSymbols = listOf("AAA"),
            disqualifiedSymbols = emptyList(),
            backfilledSymbols = emptyList(),
            failedSymbols = emptyList(),
        )

        assertEquals(LocalDate.of(2026, 4, 12), analysis.latestAnalyzedDate)
    }
}
