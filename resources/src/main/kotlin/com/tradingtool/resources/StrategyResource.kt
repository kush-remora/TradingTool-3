package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.strategy.s4.S4Service
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumService
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.concurrent.CompletableFuture

@Path("/api/strategy")
@Produces(MediaType.APPLICATION_JSON)
class StrategyResource @Inject constructor(
    private val rsiMomentumService: RsiMomentumService,
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
