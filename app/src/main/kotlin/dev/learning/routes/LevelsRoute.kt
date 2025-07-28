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
            
            // Level overview endpoint - GET /levels/{language}
            get("/{language}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class)
                val targetLanguage = call.parameters["language"]
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@get
                }
                
                if (targetLanguage.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Language is required", call.request.local.uri)
                    )
                    return@get
                }
                
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
            
            // Level topics endpoint - GET /levels/{language}/{level}/topics
            get("/{language}/{level}/topics") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class)
                val targetLanguage = call.parameters["language"]
                val level = call.parameters["level"]
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@get
                }
                
                if (targetLanguage.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Language is required", call.request.local.uri)
                    )
                    return@get
                }
                
                if (level.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Level is required", call.request.local.uri)
                    )
                    return@get
                }
                
                try {
                    val levelTopics = learningRepository.getLevelTopics(userId, targetLanguage, level)
                    call.respond(HttpStatusCode.OK, levelTopics)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch level topics", call.request.local.uri)
                    )
                }
            }
            
            // Exercise endpoint - GET /levels/{language}/{level}/topics/{topicId}/exercises/{exerciseId}
            get("/{language}/{level}/topics/{topicId}/exercises/{exerciseId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class)
                val targetLanguage = call.parameters["language"]
                val level = call.parameters["level"]
                val topicId = call.parameters["topicId"]
                val exerciseId = call.parameters["exerciseId"]
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@get
                }
                
                if (targetLanguage.isNullOrBlank() || level.isNullOrBlank() || topicId.isNullOrBlank() || exerciseId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Language, level, topicId and exerciseId are required", call.request.local.uri)
                    )
                    return@get
                }
                
                try {
                    val exercise = learningRepository.getExercise(userId, targetLanguage, level, topicId, exerciseId)
                    if (exercise != null) {
                        call.respond(HttpStatusCode.OK, exercise)
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponses.notFound("Exercise not found", call.request.local.uri)
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch exercise", call.request.local.uri)
                    )
                }
            }

            // Next exercise endpoint - GET /levels/{language}/{level}/topics/{topicId}/exercises/next
            get("/{language}/{level}/topics/{topicId}/exercises/next") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class)
                val targetLanguage = call.parameters["language"]
                val level = call.parameters["level"]
                val topicId = call.parameters["topicId"]
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@get
                }
                
                if (targetLanguage.isNullOrBlank() || level.isNullOrBlank() || topicId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Language, level and topicId are required", call.request.local.uri)
                    )
                    return@get
                }
                
                try {
                    val exercise = learningRepository.getNextExercise(userId, targetLanguage, level, topicId)
                    if (exercise != null) {
                        call.respond(HttpStatusCode.OK, exercise)
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponses.notFound("No more exercises available in this topic", call.request.local.uri)
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch next exercise", call.request.local.uri)
                    )
                }
            }
        }
    }
}
