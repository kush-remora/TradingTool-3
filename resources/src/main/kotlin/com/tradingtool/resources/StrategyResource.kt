package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.strategy.s4.S4Service
import com.tradingtool.core.strategy.rsimomentum.BackfillRequest
import com.tradingtool.core.strategy.rsimomentum.BackfillFreshRequest
import com.tradingtool.core.strategy.rsimomentum.BacktestRequest
import com.tradingtool.core.strategy.rsimomentum.MomentumDataPrepareRequest
import com.tradingtool.core.strategy.rsimomentum.MomentumDataPrepService
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumBacktestRequest
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumBackfillService
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumBacktestService
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumHistoryService
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumService
import com.tradingtool.core.strategy.rsimomentum.SimpleMomentumBacktestPrepService
import com.tradingtool.core.strategy.rsimomentum.SimpleMomentumBacktestPrepareRequest
import com.tradingtool.core.strategy.rsimomentum.SimpleMomentumBacktestRequest
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.Consumes
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
    private val simpleMomentumBacktestPrepService: SimpleMomentumBacktestPrepService,
    private val momentumDataPrepService: MomentumDataPrepService,
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
    @Path("/rsi-momentum/leaders-drawdown")
    fun getRsiMomentumLeadersDrawdown(
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
        @QueryParam("profileIds") profileIds: List<String>,
        @QueryParam("topN") topN: Int?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val toDate = to?.let { LocalDate.parse(it) } ?: LocalDate.now()
        val fromDate = from?.let { LocalDate.parse(it) } ?: toDate.minusDays(365)
        val normalizedProfileIds = profileIds
            .flatMap { value -> value.split(",") }
            .map { value -> value.trim() }
            .filter { value -> value.isNotEmpty() }
            .distinct()
            .ifEmpty { emptyList() }
        ok(
            rsiMomentumHistoryService.getLeadersDrawdown(
                from = fromDate,
                to = toDate,
                requestedProfileIds = normalizedProfileIds,
                topN = topN,
            )
        )
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
    @Path("/rsi-momentum/backtest/simple")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runSimpleMomentumBacktest(request: SimpleMomentumBacktestRequest): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiMomentumHistoryService.runSimpleMomentumBacktest(request))
    }

    @POST
    @Path("/rsi-momentum/backtest/simple/prepare")
    @Consumes(MediaType.APPLICATION_JSON)
    fun prepareSimpleMomentumBacktest(request: SimpleMomentumBacktestPrepareRequest): CompletableFuture<Response> = ioScope.endpoint {
        runCatching { simpleMomentumBacktestPrepService.prepare(request) }
            .fold(
                onSuccess = { result -> ok(result) },
                onFailure = { error ->
                    if (error is IllegalArgumentException) badRequest(error.message ?: "Invalid request.")
                    else throw error
                },
            )
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

    @POST
    @Path("/rsi-momentum/backfill/fresh")
    @Consumes(MediaType.APPLICATION_JSON)
    fun backfillRsiMomentumSnapshotsFresh(request: BackfillFreshRequest): CompletableFuture<Response> = ioScope.endpoint {
        val rebuild = rsiMomentumBackfillService.backfillFresh(request)
        rsiMomentumService.clearLatestSnapshotCache()
        val latest = rsiMomentumService.refreshLatest()
        ok(mapOf("rebuild" to rebuild, "latest" to latest))
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

    @POST
    @Path("/momentum-data/prepare")
    @Consumes(MediaType.APPLICATION_JSON)
    fun prepareMomentumData(request: MomentumDataPrepareRequest): CompletableFuture<Response> = ioScope.endpoint {
        runCatching { momentumDataPrepService.prepare(request) }
            .fold(
                onSuccess = { result -> ok(result) },
                onFailure = { error ->
                    if (error is IllegalArgumentException) badRequest(error.message ?: "Invalid request.")
                    else throw error
                },
            )
    }

    @GET
    @Path("/momentum-data/export/range")
    fun exportMomentumRange(
        @QueryParam("profileId") profileId: String?,
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val requiredProfileId = profileId?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@endpoint badRequest("profileId query param is required.")
        val requiredFrom = from?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@endpoint badRequest("from query param is required.")
        val requiredTo = to?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@endpoint badRequest("to query param is required.")
        val document = runCatching {
            momentumDataPrepService.buildRangeExport(requiredProfileId, requiredFrom, requiredTo)
        }.getOrElse { error ->
            if (error is IllegalArgumentException) return@endpoint badRequest(error.message ?: "Invalid request.")
            throw error
        }
        val json = momentumDataPrepService.toJson(document)
        val fileName = "top50_by_day_${requiredProfileId}_${requiredFrom}_${requiredTo}.json"
        Response.ok(json, MediaType.APPLICATION_JSON)
            .header("Content-Disposition", "attachment; filename=\"$fileName\"")
            .build()
    }

    @GET
    @Path("/momentum-data/export/today")
    fun exportMomentumToday(
        @QueryParam("profileId") profileId: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val requiredProfileId = profileId?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@endpoint badRequest("profileId query param is required.")
        val document = runCatching {
            momentumDataPrepService.buildTodayExport(requiredProfileId)
        }.getOrElse { error ->
            if (error is IllegalArgumentException) return@endpoint badRequest(error.message ?: "Invalid request.")
            throw error
        }
        val json = momentumDataPrepService.toJson(document)
        val fileName = "top50_today_with_1y_candles_${requiredProfileId}_${document.as_of_date_used}.json"
        Response.ok(json, MediaType.APPLICATION_JSON)
            .header("Content-Disposition", "attachment; filename=\"$fileName\"")
            .build()
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
