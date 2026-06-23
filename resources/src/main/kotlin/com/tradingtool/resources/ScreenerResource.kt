package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.strategy.wyckoff.phase1.WyckoffPhase1ScannerService
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.concurrent.CompletableFuture

@Path("/api/screener")
@Produces(MediaType.APPLICATION_JSON)
class ScreenerResource @Inject constructor(
    resourceScope: ResourceScope,
    private val wyckoffPhase1ScannerService: WyckoffPhase1ScannerService,
) {
    private val ioScope = resourceScope.ioScope

    @GET
    @Path("/universes")
    fun getUniverseOptions(): CompletableFuture<Response> = ioScope.endpoint {
        ok(wyckoffPhase1ScannerService.listUniverseOptions())
    }
}
