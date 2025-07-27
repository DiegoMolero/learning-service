package dev.learning.routes

import dev.learning.Config
import dev.learning.CreateUserRequest
import dev.learning.ErrorResponses
import dev.learning.UserManagementResponse
import dev.learning.repository.LearningRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Routes for user management - exclusively for auth service
 * These endpoints should only be accessible by the auth service, not end users
 */
fun Route.userManagementRoute(learningRepository: LearningRepository, config: Config) {
    route("/users") {
        
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
                    val success = learningRepository.createUser(request.userId)
                    
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
                val success = learningRepository.deleteUser(userId)
                
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
    }
}
