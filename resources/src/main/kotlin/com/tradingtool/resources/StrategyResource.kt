package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.strategy.s4.S4Service
import com.tradingtool.core.strategy.rsimomentum.BackfillRequest
import com.tradingtool.core.strategy.rsimomentum.BacktestRequest
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumBacktestRequest
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumBackfillService
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumBacktestService
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumHistoryService
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumService
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.LocalDate
import java.util.concurrent.CompletableFuture

@Path("/api/strategy")
@Produces(MediaType.APPLICATION_JSON)
class StrategyResource @Inject constructor(
    private val rsiMomentumService: RsiMomentumService,
    private val rsiMomentumHistoryService: RsiMomentumHistoryService,
    private val rsiMomentumBackfillService: RsiMomentumBackfillService,
    private val rsiMomentumBacktestService: RsiMomentumBacktestService,
    private val s4Service: S4Service,
    resourceScope: ResourceScope,
) {
    private val ioScope = resourceScope.ioScope

    @GET
    @Path("/rsi-momentum/latest")
    fun getLatestRsiMomentum(): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiMomentumService.getLatest())
    }

    @POST
    @Path("/rsi-momentum/refresh")
    fun refreshRsiMomentum(): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiMomentumService.refreshLatest())
    }

    @GET
    @Path("/rsi-momentum/history")
    fun getRsiMomentumHistory(
        @QueryParam("profileId") profileId: String,
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val fromDate = from?.let { LocalDate.parse(it) } ?: LocalDate.now().minusMonths(3)
        val toDate = to?.let { LocalDate.parse(it) } ?: LocalDate.now()
        ok(rsiMomentumHistoryService.getHistory(profileId, fromDate, toDate))
    }

    @GET
    @Path("/rsi-momentum/history/{date}")
    fun getRsiMomentumHistoryForDate(
        @PathParam("date") date: String,
        @QueryParam("profileId") profileId: String,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val asOfDate = LocalDate.parse(date)
        val entry = rsiMomentumHistoryService.getHistoryForDate(profileId, asOfDate)
        if (entry == null) ok(mapOf("available" to false, "message" to "No snapshot for $date"))
        else ok(entry)
    }

    @POST
    @Path("/rsi-momentum/backtest")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runRsiMomentumBacktest(request: BacktestRequest): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiMomentumHistoryService.runBacktest(request))
    }

    @POST
    @Path("/rsi-momentum/backtest/sniper")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runRsiMomentumSniperBacktest(request: RsiMomentumBacktestRequest): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiMomentumBacktestService.runBacktest(request))
    }

    @POST
    @Path("/rsi-momentum/backfill")
    @Consumes(MediaType.APPLICATION_JSON)
    fun backfillRsiMomentumSnapshots(request: BackfillRequest): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiMomentumBackfillService.backfill(request))
    }

    @GET
    @Path("/rsi-momentum/lifecycle")
    fun getRsiMomentumLifecycle(
        @QueryParam("profileId") profileId: String,
        @QueryParam("symbol") symbol: String,
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val fromDate = from?.let { LocalDate.parse(it) } ?: LocalDate.now().minusMonths(3)
        val toDate = to?.let { LocalDate.parse(it) } ?: LocalDate.now()
        ok(rsiMomentumHistoryService.getLifecycleForSymbol(profileId, symbol, fromDate, toDate))
    }

    @GET
    @Path("/rsi-momentum/lifecycle/summary")
    fun getRsiMomentumLifecycleSummary(
        @QueryParam("profileId") profileId: String,
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val fromDate = from?.let { LocalDate.parse(it) } ?: LocalDate.now().minusMonths(3)
        val toDate = to?.let { LocalDate.parse(it) } ?: LocalDate.now()
        ok(rsiMomentumHistoryService.getLifecycleSummary(profileId, fromDate, toDate))
    }

    @GET
    @Path("/rsi-momentum/history/symbols")
    fun getRsiMomentumMultiSymbolHistory(
        @QueryParam("profileId") profileId: String,
        @QueryParam("symbols") symbols: List<String>,
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val fromDate = from?.let { LocalDate.parse(it) } ?: LocalDate.now().minusMonths(3)
        val toDate = to?.let { LocalDate.parse(it) } ?: LocalDate.now()
        ok(rsiMomentumHistoryService.getMultiSymbolHistory(profileId, symbols, fromDate, toDate))
    }

    @GET
    @Path("/s4/latest")
    fun getLatestS4(): CompletableFuture<Response> = ioScope.endpoint {
        ok(s4Service.getLatest())
    }

    @POST
    @Path("/s4/refresh")
    fun refreshS4(): CompletableFuture<Response> = ioScope.endpoint {
        ok(s4Service.refreshLatest())
    }
}
