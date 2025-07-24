package dev.learning.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val service: String)

fun Route.healthRoute() {
    get("/health") {
        call.respond(
            HttpStatusCode.OK,
            HealthResponse(status = "UP", service = "learning-service")
        )
    }
}
