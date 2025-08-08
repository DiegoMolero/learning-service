package dev.learning.routes

import dev.learning.*
import dev.learning.repository.ContentRepository
import dev.learning.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class SubmitProgressRequest(
    val targetLang: String,
    val moduleId: String,
    val unitId: String,
    val exerciseId: String,
    val userAnswer: String,
    val answerStatus: AnswerStatus
)

@Serializable
data class SubmitProgressResponse(
    val success: Boolean,
    val answerStatus: AnswerStatus,
    val correctAnswer: String,
    val explanation: Map<String, String>? = null,
    val progress: UnitProgress? = null
)

fun Route.progressRoutes(
    contentRepository: ContentRepository,
    userRepository: UserRepository
) {
    route("/progress") {
        authenticate("auth-jwt") {
            post("/submit") {
                try {
                    val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing or invalid user ID"))
                        return@post
                    }

                    val request = call.receive<SubmitProgressRequest>()
                    
                    // Validate that the exercise exists
                    val exercise = contentRepository.getExerciseDetails(
                        userId = userId,
                        lang = request.targetLang,
                        moduleId = request.moduleId,
                        unitId = request.unitId,
                        exerciseId = request.exerciseId
                    )
                    
                    if (exercise == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Exercise not found"))
                        return@post
                    }

                    // Record the user's progress
                    val success = userRepository.recordExerciseProgress(
                        userId = userId,
                        lang = request.targetLang,
                        moduleId = request.moduleId,
                        unitId = request.unitId,
                        exerciseId = request.exerciseId,
                        answerStatus = request.answerStatus,
                        userAnswer = request.userAnswer
                    )

                    // Get updated progress information
                    val unitProgress = userRepository.getUnitProgress(userId, request.targetLang, request.moduleId, request.unitId)

                    // Prepare explanation if the exercise has a tip
                    val explanation = exercise.tip

                    val response = SubmitProgressResponse(
                        success = success,
                        answerStatus = request.answerStatus,
                        correctAnswer = exercise.solution,
                        explanation = explanation,
                        progress = unitProgress
                    )

                    call.respond(HttpStatusCode.OK, response)

                } catch (e: Exception) {
                    call.application.log.error("Error submitting progress", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
                }
            }
        }
    }
}