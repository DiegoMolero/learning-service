package dev.learning.routes

import dev.learning.ErrorResponses
import dev.learning.repository.LearningRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Route.levelsRoute(learningRepository: LearningRepository) {
    authenticate("auth-jwt") {
        route("/levels") {
            
            // New dashboard endpoint - GET /levels (level overview)
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class)
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@get
                }
                
                val targetLanguage = call.request.queryParameters["targetLanguage"] ?: "en"
                
                try {
                    val levelOverview = learningRepository.getLevelOverview(userId, targetLanguage)
                    call.respond(HttpStatusCode.OK, levelOverview)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch level overview", call.request.local.uri)
                    )
                }
            }
            
            // New dashboard endpoint - GET /levels/:levelId/topics
            get("/{levelId}/topics") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class)
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
                
                val targetLanguage = call.request.queryParameters["targetLanguage"] ?: "en"
                
                try {
                    val levelTopics = learningRepository.getLevelTopics(userId, targetLanguage, levelId)
                    call.respond(HttpStatusCode.OK, levelTopics)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch level topics", call.request.local.uri)
                    )
                }
            }
            
            // Get available levels structure
            get("/available") {
                try {
                    val availableLevels = learningRepository.getAllAvailableLevels()
                    call.respond(HttpStatusCode.OK, availableLevels)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch available levels", call.request.local.uri)
                    )
                }
            }
            
            // Get all levels (legacy endpoint)
            get("/all") {
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
            
            // Get levels by language and level
            get("/{targetLanguage}/{level}") {
                val targetLanguage = call.parameters["targetLanguage"]
                val level = call.parameters["level"]
                
                if (targetLanguage.isNullOrBlank() || level.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Target language and level are required", call.request.local.uri)
                    )
                    return@get
                }
                
                try {
                    val levels = learningRepository.getLevelsByLanguageAndLevel(targetLanguage, level)
                    call.respond(HttpStatusCode.OK, levels)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch levels", call.request.local.uri)
                    )
                }
            }
            
            // Get specific level (legacy endpoint)
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
