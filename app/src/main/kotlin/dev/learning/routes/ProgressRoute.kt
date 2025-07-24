package dev.learning.routes

import dev.learning.ErrorResponses
import dev.learning.UpdateProgressRequest
import dev.learning.repository.LearningRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Route.progressRoute(learningRepository: LearningRepository) {
    authenticate("auth-jwt") {
        route("/progress") {
            
            // Get all progress for user
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@get
                }
                
                try {
                    val progress = learningRepository.getUserProgress(userId)
                    call.respond(HttpStatusCode.OK, progress)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch progress", call.request.local.uri)
                    )
                }
            }
            
            // Get progress for specific level
            get("/{levelId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                val levelId = call.parameters["levelId"]
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@get
                }
                
                if (levelId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Level ID is required", call.request.local.uri)
                    )
                    return@get
                }
                
                try {
                    val progress = learningRepository.getUserProgressForLevel(userId, levelId)
                    if (progress != null) {
                        call.respond(HttpStatusCode.OK, progress)
                    } else {
                        // Return empty progress if none exists
                        call.respond(
                            HttpStatusCode.OK,
                            dev.learning.UserProgressResponse(
                                userId = userId,
                                levelId = levelId,
                                completedPhraseIds = emptyList()
                            )
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch progress", call.request.local.uri)
                    )
                }
            }
            
            // Update progress for a level
            put("/{levelId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                val levelId = call.parameters["levelId"]
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@put
                }
                
                if (levelId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Level ID is required", call.request.local.uri)
                    )
                    return@put
                }
                
                try {
                    val request = call.receive<UpdateProgressRequest>()
                    
                    if (request.levelId != levelId) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponses.badRequest("Level ID in URL must match level ID in body", call.request.local.uri)
                        )
                        return@put
                    }
                    
                    val success = learningRepository.updateUserProgress(
                        userId = userId,
                        levelId = levelId,
                        completedPhraseIds = request.completedPhraseIds
                    )
                    
                    if (success) {
                        val updatedProgress = learningRepository.getUserProgressForLevel(userId, levelId)
                        call.respond(HttpStatusCode.OK, updatedProgress!!)
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponses.internalServerError("Failed to update progress", call.request.local.uri)
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Invalid request body", call.request.local.uri)
                    )
                }
            }
        }
    }
}
