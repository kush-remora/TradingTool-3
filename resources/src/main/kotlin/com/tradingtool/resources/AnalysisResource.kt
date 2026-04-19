package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.analysis.swing.SwingService
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.notFound
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.concurrent.CompletableFuture

@Path("/api/analysis")
@Produces(MediaType.APPLICATION_JSON)
class AnalysisResource @Inject constructor(
    private val swingService: SwingService,
    private val resourceScope: ResourceScope,
) {
    private val ioScope = resourceScope.ioScope

    /**
     * GET /api/analysis/swings/{symbol}
     * Returns local peaks and troughs based on a percentage reversal.
     */
    @GET
    @Path("/swings/{symbol}")
    fun getSwings(
        @PathParam("symbol") symbol: String,
        @QueryParam("reversal") reversal: Double?,
        @QueryParam("lookback") lookback: Int?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val sym = symbol.trim().uppercase()
        if (sym.isEmpty()) return@endpoint badRequest("Symbol is required")
        
        val reversalPct = reversal ?: 5.0
        if (reversalPct <= 0.0) return@endpoint badRequest("Reversal percentage must be positive")
        
        val lookbackDays = lookback ?: 365
        if (lookbackDays <= 0) return@endpoint badRequest("Lookback days must be positive")

        val result = swingService.analyzeSwings(sym, reversalPct, lookbackDays)
            ?: return@endpoint notFound("Analysis failed for '$sym'. Check if stock exists and has data.")
        
        ok(result)
    }
}
