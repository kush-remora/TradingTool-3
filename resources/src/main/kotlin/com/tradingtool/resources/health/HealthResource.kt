package com.tradingtool.resources.health

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/")
class HealthResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun root(): Response {
        return Response.ok(
            RootResponse(
                service = "TradingTool-3",
                status = "ok",
            ),
        ).build()
    }

    @GET
    @Path("health")
    @Produces(MediaType.APPLICATION_JSON)
    fun health(): Response {
        return Response.ok(
            StatusResponse(status = "ok"),
        ).build()
    }

    private data class RootResponse(
        val service: String,
        val status: String,
    )

    private data class StatusResponse(
        val status: String,
    )
}
