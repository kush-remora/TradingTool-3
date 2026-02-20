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
        val json = """{"service":"TradingTool-3","status":"ok"}"""
        return Response.ok(json).type(MediaType.APPLICATION_JSON).build()
    }

    @GET
    @Path("health")
    @Produces(MediaType.APPLICATION_JSON)
    fun health(): Response {
        val json = """{"status":"ok"}"""
        return Response.ok(json).type(MediaType.APPLICATION_JSON).build()
    }
}
