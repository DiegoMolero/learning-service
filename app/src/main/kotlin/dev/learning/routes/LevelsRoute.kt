package dev.learning.routes

import dev.learning.ErrorResponses
import dev.learning.repository.LearningRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Route.levelsRoute(learningRepository: LearningRepository) {
    authenticate("auth-jwt") {
        route("/levels") {
            
            // Get all levels
            get {
                try {
                    val levels = learningRepository.getAllLevels()
                    call.respond(HttpStatusCode.OK, levels)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch levels", call.request.local.uri)
                    )
                }
            }
            
            // Get specific level
            get("/{levelId}") {
                val levelId = call.parameters["levelId"]
                if (levelId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Level ID is required", call.request.local.uri)
                    )
                    return@get
                }
                
                try {
                    val level = learningRepository.getLevel(levelId)
                    if (level != null) {
                        call.respond(HttpStatusCode.OK, level)
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponses.notFound("Level not found", call.request.local.uri)
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch level", call.request.local.uri)
                    )
                }
            }
        }
    }
}
