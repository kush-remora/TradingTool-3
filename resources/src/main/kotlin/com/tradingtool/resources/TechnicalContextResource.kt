package com.tradingtool.resources

import com.tradingtool.core.technical.TechnicalContext
import com.tradingtool.core.technical.TechnicalContextService
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.runBlocking

import jakarta.inject.Inject

@Path("/api/stock")
@Produces(MediaType.APPLICATION_JSON)
class TechnicalContextResource @Inject constructor(
    private val service: TechnicalContextService
) {
    @GET
    @Path("/{symbol}/technical-context")
    fun getTechnicalContext(@PathParam("symbol") symbol: String): TechnicalContext {
        return runBlocking {
            service.getContext(symbol) ?: throw WebApplicationException(Response.Status.NOT_FOUND)
        }
    }
}
