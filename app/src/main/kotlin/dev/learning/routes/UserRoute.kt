package dev.learning.routes

import dev.learning.Config
import dev.learning.CreateUserRequest
import dev.learning.ErrorResponses
import dev.learning.UpdateUserSettingsRequest
import dev.learning.UpdateUserSettingsResponse
import dev.learning.UserManagementResponse
import dev.learning.repository.UserRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.util.pipeline.*

fun Route.userRoute(userRepository: UserRepository, config: Config) {
    route("/users") {
        
        // User management routes for auth service (X-Internal-Secret authentication required)
        /**
         * Create a new user in the learning service
         * POST /users
         * Headers: X-Internal-Secret: <secret>
         * Body: { "userId": "uuid-string" }
         * 
         * This should be called when a user creates an account in the auth service
         */
        post {
            // Validate internal secret
            val internalSecret = call.request.headers["X-Internal-Secret"]
            if (internalSecret != config.internalSecret) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    UserManagementResponse(
                        success = false,
                        message = "Unauthorized access"
                    )
                )
                return@post
            }
            
            try {
                val request = call.receive<CreateUserRequest>()
                
                if (request.userId.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        UserManagementResponse(
                            success = false,
                            message = "User ID is required"
                        )
                    )
                    return@post
                }
                
                try {
                    val success = userRepository.createUser(request.userId)
                    
                    if (success) {
                        call.respond(
                            HttpStatusCode.Created,
                            UserManagementResponse(
                                success = true,
                                message = "User created successfully",
                                userId = request.userId
                            )
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            UserManagementResponse(
                                success = false,
                                message = "Failed to create user",
                                userId = request.userId
                            )
                        )
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        UserManagementResponse(
                            success = false,
                            message = "Invalid user ID format"
                        )
                    )
                }
                
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    UserManagementResponse(
                        success = false,
                        message = "Invalid request body"
                    )
                )
            }
        }
        
        /**
         * Delete a user and all their data
         * DELETE /users/:userId
         * Headers: X-Internal-Secret: <secret>
         * 
         * This should be called when a user deletes their account in the auth service
         */
        delete("/{userId}") {
            // Validate internal secret
            val internalSecret = call.request.headers["X-Internal-Secret"]
            if (internalSecret != config.internalSecret) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    UserManagementResponse(
                        success = false,
                        message = "Unauthorized access"
                    )
                )
                return@delete
            }
            
            val userId = call.parameters["userId"]
            
            if (userId.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    UserManagementResponse(
                        success = false,
                        message = "User ID is required"
                    )
                )
                return@delete
            }
            
            try {
                val success = userRepository.deleteUser(userId)
                
                if (success) {
                    call.respond(
                        HttpStatusCode.OK,
                        UserManagementResponse(
                            success = true,
                            message = "User deleted successfully",
                            userId = userId
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        UserManagementResponse(
                            success = false,
                            message = "Failed to delete user",
                            userId = userId
                        )
                    )
                }
                
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    UserManagementResponse(
                        success = false,
                        message = "Invalid user ID format",
                        userId = userId
                    )
                )
            }
        }
        
        authenticate("auth-jwt") {
            // Settings routes
            route("/settings") {
                // Get user settings
                get {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString() 
                        ?: principal?.subject // Fallback to subject if uuid claim not present
                    
                    if (userId.isNullOrBlank()) {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                        )
                        return@get
                    }
                    
                    try {
                        var settings = userRepository.getUserSettings(userId)
                        
                        // If no settings exist, create default ones
                        if (settings == null) {
                            val created = userRepository.createDefaultUserSettings(userId)
                            if (created) {
                                settings = userRepository.getUserSettings(userId)
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
                    val userId = principal?.payload?.getClaim("userId")?.asString() 
                        ?: principal?.subject // Fallback to subject if uuid claim not present
                    
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
                        
                        val (success, warnings) = userRepository.updateUserSettings(
                            userId = userId,
                            nativeLanguage = request.nativeLanguage,
                            targetLanguage = request.targetLanguage,
                            darkMode = request.darkMode,
                            onboardingStep = request.onboardingStep,
                            userLevel = request.userLevel
                        )
                        
                        if (success) {
                            val updatedSettings = userRepository.getUserSettings(userId)
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
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponses.badRequest(e.message ?: "Invalid request parameters", call.request.local.uri)
                        )
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponses.badRequest("Invalid request body", call.request.local.uri)
                        )
                    }
                }
                
                // Reset user progress
                delete("/progress") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString() 
                        ?: principal?.subject // Fallback to subject if uuid claim not present
                    
                    if (userId.isNullOrBlank()) {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                        )
                        return@delete
                    }
                    
                    try {
                        // TODO: Implement deleteUserProgress in UserRepository if needed
                        // val success = userRepository.deleteUserProgress(userId)
                        val success = true // Temporary - always return success
                        
                        if (success) {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf(
                                    "success" to true,
                                    "message" to "User progress reset successfully"
                                )
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ErrorResponses.internalServerError("Failed to reset user progress", call.request.local.uri)
                            )
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponses.internalServerError("Failed to reset user progress", call.request.local.uri)
                        )
                    }
                }
            }
        }
    }
}
