package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.breakouttracker.BreakoutTrackerService
import com.tradingtool.core.breakouttracker.SaveBreakoutTrackerEntryRequest
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.notFound
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.concurrent.CompletableFuture

@Path("/api/breakout-tracker")
@Produces(MediaType.APPLICATION_JSON)
class BreakoutTrackerResource @Inject constructor(
    private val resourceScope: ResourceScope,
    private val breakoutTrackerService: BreakoutTrackerService,
) {
    private val ioScope = resourceScope.ioScope

    @GET
    fun list(): CompletableFuture<Response> = ioScope.endpoint { ok(breakoutTrackerService.list()) }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    fun save(request: SaveBreakoutTrackerEntryRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        if (body.instrumentToken <= 0 || body.symbol.isBlank() || body.breakoutPrice <= 0.0) {
            return@endpoint badRequest("Symbol, instrument token, and a positive breakout price are required.")
        }
        ok(
            breakoutTrackerService.save(
                body.copy(
                    symbol = body.symbol.trim().uppercase(),
                    companyName = body.companyName.trim(),
                    notes = body.notes.trim(),
                ),
            ),
        )
    }

    @DELETE
    @Path("/{id}")
    fun delete(@PathParam("id") id: Long): CompletableFuture<Response> = ioScope.endpoint {
        if (!breakoutTrackerService.delete(id)) return@endpoint notFound("Breakout tracker entry not found.")
        ok(mapOf("success" to true))
    }
}
