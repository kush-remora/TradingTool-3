package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.strategy.hotsma.HotSmaRunConfig
import com.tradingtool.core.strategy.hotsma.HotSmaRunRequest
import com.tradingtool.core.strategy.hotsma.HotSmaScannerService
import com.tradingtool.core.strategy.hotsma.HotSmaTelegramRequest
import com.tradingtool.core.strategy.deliverybreakout.DeliveryBreakoutScannerService
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
    private val hotSmaScannerService: HotSmaScannerService,
    private val deliveryBreakoutScannerService: DeliveryBreakoutScannerService,
    private val wyckoffPhase1ScannerService: WyckoffPhase1ScannerService,
    private val wyckoffPhase1ConfigService: WyckoffPhase1ConfigService,
    private val growwVolumeShockerDashboardService: GrowwVolumeShockerDashboardService,
) {
    private val ioScope = resourceScope.ioScope

    @GET
    @Path("/hot-sma/universes")
    fun getHotSmaUniverseOptions(): CompletableFuture<Response> = ioScope.endpoint {
        ok(hotSmaScannerService.listUniverseOptions())
    }

    @GET
    @Path("/delivery-breakout/dashboard")
    fun getDeliveryBreakoutDashboard(
        @jakarta.ws.rs.QueryParam("tradeDate") tradeDate: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val parsedTradeDate = try {
            tradeDate?.takeIf { value -> value.isNotBlank() }?.let(LocalDate::parse)
        } catch (_: Exception) {
            return@endpoint badRequest("tradeDate must be a valid ISO date in YYYY-MM-DD format.")
        }
        ok(deliveryBreakoutScannerService.getDashboard(parsedTradeDate))
    }

    @POST
    @Path("/hot-sma/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runHotSma(request: HotSmaRunRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        val normalizedRequest = try {
            validateHotSmaRunRequest(body)
        } catch (error: IllegalArgumentException) {
            return@endpoint badRequest(error.message ?: "Invalid request.")
        }

        ok(hotSmaScannerService.run(HotSmaRunConfig(indexKey = normalizedRequest.indexKey)))
    }

    @GET
    @Path("/wyckoff/phase1/universes")
    fun getWyckoffPhase1UniverseOptions(): CompletableFuture<Response> = ioScope.endpoint {
        ok(wyckoffPhase1ScannerService.listUniverseOptions())
    }

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

internal fun validateHotSmaRunRequest(request: HotSmaRunRequest): HotSmaRunRequest {
    val normalizedIndexKey = normalizeIndexKeyForResource(request.indexKey)
    require(normalizedIndexKey.isNotBlank()) { "indexKey is required." }
    return request.copy(indexKey = normalizedIndexKey)
}

internal fun validateHotSmaTelegramRequest(request: HotSmaTelegramRequest): HotSmaTelegramRequest {
    val normalizedIndexKey = normalizeIndexKeyForResource(request.indexKey)
    require(normalizedIndexKey.isNotBlank()) { "indexKey is required." }

    val normalizedSymbol = request.symbol.trim().uppercase()
    require(normalizedSymbol.isNotBlank()) { "symbol is required." }
    require(request.currentPrice > 0.0) { "currentPrice must be a positive number." }

    return request.copy(
        indexKey = normalizedIndexKey,
        symbol = normalizedSymbol,
    )
}

private fun normalizeIndexKeyForResource(raw: String): String {
    return raw.trim()
        .replace(Regex("[^A-Za-z0-9]+"), "_")
        .trim('_')
        .uppercase()
}
