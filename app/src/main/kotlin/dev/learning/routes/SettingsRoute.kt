package dev.learning.routes

import dev.learning.ErrorResponses
import dev.learning.UpdateUserSettingsRequest
import dev.learning.UpdateUserSettingsResponse
import dev.learning.repository.LearningRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Route.settingsRoute(learningRepository: LearningRepository) {
    authenticate("auth-jwt") {
        route("/settings") {
            
            // Get user settings
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
                    var settings = learningRepository.getUserSettings(userId)
                    
                    // If no settings exist, create default ones
                    if (settings == null) {
                        val created = learningRepository.createDefaultUserSettings(userId)
                        if (created) {
                            settings = learningRepository.getUserSettings(userId)
                        }
                    }
                    
                    if (settings != null) {
                        call.respond(HttpStatusCode.OK, settings)
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponses.internalServerError("Failed to get or create user settings", call.request.local.uri)
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch settings", call.request.local.uri)
                    )
                }
            }
            
            // Update user settings
            put {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@put
                }
                
                try {
                    val request = call.receive<UpdateUserSettingsRequest>()
                    
                    // Validate supported languages
                    val supportedLanguages = listOf("en", "es")
                    request.nativeLanguage?.let { 
                        if (it !in supportedLanguages) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponses.badRequest("Unsupported native language: $it. Supported: ${supportedLanguages.joinToString()}", call.request.local.uri)
                            )
                            return@put
                        }
                    }
                    request.targetLanguage?.let { 
                        if (it !in supportedLanguages) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponses.badRequest("Unsupported target language: $it. Supported: ${supportedLanguages.joinToString()}", call.request.local.uri)
                            )
                            return@put
                        }
                    }
                    
                    // Validate that native and target languages are different
                    if (request.nativeLanguage != null && request.targetLanguage != null && 
                        request.nativeLanguage == request.targetLanguage) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponses.badRequest("Native language and target language cannot be the same", call.request.local.uri)
                        )
                        return@put
                    }
                    
                    // Note: The repository will automatically ignore onboarding completion 
                    // if both languages are not set, so we don't need to validate here
                    
                    val (success, warnings) = learningRepository.updateUserSettingsWithWarnings(
                        userId = userId,
                        nativeLanguage = request.nativeLanguage,
                        targetLanguage = request.targetLanguage,
                        darkMode = request.darkMode,
                        onboardingPhase = request.onboardingPhase
                    )
                    
                    if (success) {
                        val updatedSettings = learningRepository.getUserSettings(userId)
                        if (updatedSettings != null) {
                            val response = UpdateUserSettingsResponse(
                                settings = updatedSettings,
                                warnings = warnings
                            )
                            call.respond(HttpStatusCode.OK, response)
                        } else {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ErrorResponses.internalServerError("Failed to retrieve updated settings", call.request.local.uri)
                            )
                        }
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponses.internalServerError("Failed to update settings", call.request.local.uri)
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
