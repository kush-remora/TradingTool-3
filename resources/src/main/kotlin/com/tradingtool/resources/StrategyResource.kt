package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.strategy.wyckoff.phase1.WyckoffPhase1ConfigService
import com.tradingtool.core.strategy.wyckoff.phase1.WyckoffPhase1RunConfig
import com.tradingtool.core.strategy.wyckoff.phase1.WyckoffPhase1RunRequest
import com.tradingtool.core.strategy.wyckoff.phase1.WyckoffPhase1ScannerService
import com.tradingtool.core.volumeshocker.groww.GrowwVolumeShockerDashboardService
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.LocalDate
import java.util.concurrent.CompletableFuture

@Path("/api/strategy")
@Produces(MediaType.APPLICATION_JSON)
class StrategyResource @Inject constructor(
    resourceScope: ResourceScope,
    private val wyckoffPhase1ScannerService: WyckoffPhase1ScannerService,
    private val wyckoffPhase1ConfigService: WyckoffPhase1ConfigService,
    private val growwVolumeShockerDashboardService: GrowwVolumeShockerDashboardService,
) {
    private val ioScope = resourceScope.ioScope

    @GET
    @Path("/wyckoff/phase1/config")
    fun getWyckoffPhase1Config(): CompletableFuture<Response> = ioScope.endpoint {
        ok(wyckoffPhase1ConfigService.loadPhase1Config())
    }

    @GET
    @Path("/wyckoff/phase1/columns")
    fun getWyckoffPhase1ColumnsConfig(): CompletableFuture<Response> = ioScope.endpoint {
        ok(wyckoffPhase1ConfigService.loadTableColumnsConfig())
    }

    @POST
    @Path("/wyckoff/phase1/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runWyckoffPhase1(request: WyckoffPhase1RunRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        val config = wyckoffPhase1ConfigService.loadPhase1Config()
        
        val runConfig = WyckoffPhase1RunConfig(
            universeKeys = body.universeKeys,
            symbols = body.symbols,
            asOfDate = body.asOfDate?.let { LocalDate.parse(it) } ?: LocalDate.now(),
            applyStrictBaseFilter = body.applyStrictBaseFilter
        )
        ok(wyckoffPhase1ScannerService.run(runConfig, config))
    }

    @GET
    @Path("/volume-shocker/dates")
    fun getVolumeShockerDates(): CompletableFuture<Response> = ioScope.endpoint {
        ok(growwVolumeShockerDashboardService.listAvailableDates())
    }

    @GET
    @Path("/volume-shocker/dashboard")
    fun getVolumeShockerDashboard(
        @jakarta.ws.rs.QueryParam("tradeDate") tradeDate: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val parsedTradeDate = tradeDate?.takeIf { value -> value.isNotBlank() }?.let(LocalDate::parse)
            ?: return@endpoint badRequest("tradeDate query parameter is required.")
        ok(growwVolumeShockerDashboardService.getDashboard(parsedTradeDate))
    }

    @GET
    @Path("/volume-shocker/detail")
    fun getVolumeShockerDetail(
        @jakarta.ws.rs.QueryParam("tradeDate") tradeDate: String?,
        @jakarta.ws.rs.QueryParam("symbol") symbol: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val parsedTradeDate = tradeDate?.takeIf { value -> value.isNotBlank() }?.let(LocalDate::parse)
            ?: return@endpoint badRequest("tradeDate query parameter is required.")
        val requestedSymbol = symbol?.trim()?.uppercase()?.takeIf { value -> value.isNotBlank() }
            ?: return@endpoint badRequest("symbol query parameter is required.")
        ok(growwVolumeShockerDashboardService.getDetail(parsedTradeDate, requestedSymbol))
    }
}
