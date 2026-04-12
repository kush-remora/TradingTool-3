package com.tradingtool.core.strategy.s4

import com.tradingtool.core.technical.roundTo2

object S4Ranker {
    private data class ScoreBreakdown(
        val todayVolumeScore: Double,
        val recent3dVolumeScore: Double,
        val persistenceScore: Double,
        val priceScore: Double,
        val totalScore: Double,
    )

    fun qualify(input: S4CandidateInput): S4QualifiedCandidate? {
        val profile = input.profile
        val analysis = input.analysis
        val passesVolume = analysis.todayVolumeRatio >= profile.todayVolumeRatioThreshold ||
            analysis.recent3dAvgVolumeRatio >= profile.recent3dAvgVolumeRatioThreshold ||
            (analysis.spikePersistenceDays5d >= profile.spikePersistenceThreshold &&
                analysis.recent5dMaxVolumeRatio >= profile.recent5dMaxVolumeRatioThreshold)

        val passesPrice = analysis.breakoutAbove20dHigh ||
            analysis.todayPriceChangePct >= profile.todayPriceChangePctThreshold ||
            analysis.priceReturn3dPct >= profile.priceReturn3dPctThreshold

        if (!passesVolume || !passesPrice) {
            return null
        }

        val classification = when {
            analysis.spikePersistenceDays5d >= 4 || analysis.elevatedVolumeDays10d >= 7 -> "EXTENDED_SPIKE"
            analysis.todayVolumeRatio >= profile.todayVolumeRatioThreshold &&
                analysis.spikePersistenceDays5d <= 1 &&
                analysis.elevatedVolumeDays10d <= 2 &&
                analysis.recent10dAvgVolumeRatio < 1.7 -> "FRESH_SPIKE"
            else -> "BUILDUP_BREAKOUT"
        }

        val scoreBreakdown = calculateScoreBreakdown(input)
        return S4QualifiedCandidate(
            member = input.member,
            analysis = analysis,
            classification = classification,
            todayVolumeScore = scoreBreakdown.todayVolumeScore,
            recent3dVolumeScore = scoreBreakdown.recent3dVolumeScore,
            persistenceScore = scoreBreakdown.persistenceScore,
            priceScore = scoreBreakdown.priceScore,
            score = scoreBreakdown.totalScore,
        )
    }

    fun rank(
        profileId: String,
        baseUniversePreset: String,
        candidates: List<S4QualifiedCandidate>,
        candidateCount: Int,
        orderedUniverseSymbols: List<String>,
    ): List<S4RankedCandidate> {
        val indexRankBySymbol = orderedUniverseSymbols
            .mapIndexed { index, symbol -> symbol to (index + 1) }
            .toMap()
        val indexSize = orderedUniverseSymbols.size

        return candidates
            .sortedWith(
                compareByDescending<S4QualifiedCandidate> { candidate -> candidate.score }
                    .thenByDescending { candidate -> candidate.analysis.todayVolumeRatio }
                    .thenByDescending { candidate -> candidate.analysis.recent3dAvgVolumeRatio }
                    .thenByDescending { candidate -> candidate.analysis.todayPriceChangePct }
                    .thenBy { candidate -> candidate.member.symbol }
            )
            .take(candidateCount)
            .mapIndexed { index, candidate ->
                val indexRank = indexRankBySymbol[candidate.member.symbol] ?: indexSize
                S4RankedCandidate(
                    rank = index + 1,
                    symbol = candidate.member.symbol,
                    companyName = candidate.member.companyName,
                    instrumentToken = candidate.member.instrumentToken,
                    profileId = profileId,
                    baseUniversePreset = baseUniversePreset,
                    close = candidate.analysis.close,
                    avgVolume20d = candidate.analysis.avgVolume20d,
                    avgTradedValueCr20d = candidate.analysis.avgTradedValueCr20d,
                    todayVolumeRatio = candidate.analysis.todayVolumeRatio,
                    recent3dAvgVolumeRatio = candidate.analysis.recent3dAvgVolumeRatio,
                    recent5dMaxVolumeRatio = candidate.analysis.recent5dMaxVolumeRatio,
                    spikePersistenceDays5d = candidate.analysis.spikePersistenceDays5d,
                    recent10dAvgVolumeRatio = candidate.analysis.recent10dAvgVolumeRatio,
                    elevatedVolumeDays10d = candidate.analysis.elevatedVolumeDays10d,
                    todayPriceChangePct = candidate.analysis.todayPriceChangePct,
                    priceReturn3dPct = candidate.analysis.priceReturn3dPct,
                    breakoutAbove20dHigh = candidate.analysis.breakoutAbove20dHigh,
                    indexRank = indexRank,
                    indexSize = indexSize,
                    indexLayer = indexLayer(indexRank),
                    todayVolumeScore = candidate.todayVolumeScore,
                    recent3dVolumeScore = candidate.recent3dVolumeScore,
                    persistenceScore = candidate.persistenceScore,
                    priceScore = candidate.priceScore,
                    classification = candidate.classification,
                    score = candidate.score,
                )
            }
    }

    private fun indexLayer(indexRank: Int): String {
        return when {
            indexRank <= 50 -> "Top 50"
            indexRank <= 100 -> "Top 100"
            indexRank <= 150 -> "Top 150"
            indexRank <= 200 -> "Top 200"
            else -> "Bottom 50"
        }
    }

    private fun calculateScoreBreakdown(input: S4CandidateInput): ScoreBreakdown {
        val profile = input.profile
        val analysis = input.analysis
        val todayVolumeScore = (normalize(analysis.todayVolumeRatio, profile.todayVolumeRatioThreshold) * 40.0).roundTo2()
        val recent3dScore = (normalize(analysis.recent3dAvgVolumeRatio, profile.recent3dAvgVolumeRatioThreshold) * 30.0).roundTo2()
        val persistenceScore = ((analysis.spikePersistenceDays5d.coerceIn(0, 5) / 5.0) * 15.0).roundTo2()
        val priceScore = (maxOf(
            normalize(analysis.todayPriceChangePct, profile.todayPriceChangePctThreshold),
            normalize(analysis.priceReturn3dPct, profile.priceReturn3dPctThreshold),
            if (analysis.breakoutAbove20dHigh) 1.0 else 0.0,
        ) * 15.0).roundTo2()
        val totalScore = (todayVolumeScore + recent3dScore + persistenceScore + priceScore).roundTo2()

        return ScoreBreakdown(
            todayVolumeScore = todayVolumeScore,
            recent3dVolumeScore = recent3dScore,
            persistenceScore = persistenceScore,
            priceScore = priceScore,
            totalScore = totalScore,
        )
    }

    private fun normalize(value: Double, threshold: Double): Double {
        if (threshold <= 0.0) {
            return 0.0
        }
        return (value / threshold).coerceAtLeast(0.0).coerceAtMost(2.0) / 2.0
    }
}
