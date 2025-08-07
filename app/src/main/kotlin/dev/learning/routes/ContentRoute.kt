package dev.learning.routes

import dev.learning.ErrorResponses
import dev.learning.repository.ContentRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Route.contentRoute(contentRepository: ContentRepository) {
    authenticate("auth-jwt") {
        route("/content") {
            
            // Modules endpoints
            // GET /content/:lang/modules - Get all modules for a language
            get("/{lang}/modules") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class)
                val language = call.parameters["lang"]
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@get
                }
                
                if (language.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Language is required", call.request.local.uri)
                    )
                    return@get
                }
                
                try {
                    val modules = contentRepository.getModules(userId, language)
                    call.respond(HttpStatusCode.OK, modules)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch modules", call.request.local.uri)
                    )
                }
            }
            
            // GET /content/:lang/modules/:moduleId - Get module details
            get("/{lang}/modules/{moduleId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class)
                val language = call.parameters["lang"]
                val moduleId = call.parameters["moduleId"]
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@get
                }
                
                if (language.isNullOrBlank() || moduleId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Language and module ID are required", call.request.local.uri)
                    )
                    return@get
                }
                
                try {
                    val module = contentRepository.getModule(userId, language, moduleId)
                    if (module != null) {
                        call.respond(HttpStatusCode.OK, module)
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponses.notFound("Module not found", call.request.local.uri)
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch module", call.request.local.uri)
                    )
                }
            }
            
            // Units endpoints
            // GET /content/:lang/modules/:moduleId/units - Get units for a module
            get("/{lang}/modules/{moduleId}/units") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class)
                val language = call.parameters["lang"]
                val moduleId = call.parameters["moduleId"]
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@get
                }
                
                if (language.isNullOrBlank() || moduleId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Language and module ID are required", call.request.local.uri)
                    )
                    return@get
                }
                
                try {
                    val units = contentRepository.getUnits(userId, language, moduleId)
                    call.respond(HttpStatusCode.OK, units)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch module units", call.request.local.uri)
                    )
                }
            }
            
            // GET /content/:lang/modules/:moduleId/units/:unitId - Get unit details
            get("/{lang}/modules/{moduleId}/units/{unitId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class)
                val language = call.parameters["lang"]
                val moduleId = call.parameters["moduleId"]
                val unitId = call.parameters["unitId"]
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@get
                }
                
                if (language.isNullOrBlank() || moduleId.isNullOrBlank() || unitId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Language, module ID, and unit ID are required", call.request.local.uri)
                    )
                    return@get
                }
                
                try {
                    val unit = contentRepository.getUnit(userId, language, moduleId, unitId)
                    if (unit != null) {
                        call.respond(HttpStatusCode.OK, unit)
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponses.notFound("Unit not found", call.request.local.uri)
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch unit", call.request.local.uri)
                    )
                }
            }
            
            // Exercises endpoints
            // GET /content/:lang/modules/:moduleId/units/:unitId/exercises - Get exercises for a unit
            get("/{lang}/modules/{moduleId}/units/{unitId}/exercises") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class)
                val language = call.parameters["lang"]
                val moduleId = call.parameters["moduleId"]
                val unitId = call.parameters["unitId"]
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@get
                }
                
                if (language.isNullOrBlank() || moduleId.isNullOrBlank() || unitId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Language, module ID, and unit ID are required", call.request.local.uri)
                    )
                    return@get
                }
                
                try {
                    val exercises = contentRepository.getExercises(userId, language, moduleId, unitId)
                    call.respond(HttpStatusCode.OK, exercises)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponses.internalServerError("Failed to fetch unit exercises", call.request.local.uri)
                    )
                }
            }
            
            // GET /content/:lang/modules/:moduleId/units/:unitId/exercises/:exerciseId - Get exercise details
            get("/{lang}/modules/{moduleId}/units/{unitId}/exercises/{exerciseId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class)
                val language = call.parameters["lang"]
                val moduleId = call.parameters["moduleId"]
                val unitId = call.parameters["unitId"]
                val exerciseId = call.parameters["exerciseId"]
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@get
                }
                
                if (language.isNullOrBlank() || moduleId.isNullOrBlank() || unitId.isNullOrBlank() || exerciseId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Language, module ID, unit ID, and exercise ID are required", call.request.local.uri)
                    )
                    return@get
                }
                
                try {
                    val exercise = contentRepository.getExerciseDetails(userId, language, moduleId, unitId, exerciseId)
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
            
            // Submit exercise response - POST /content/:lang/modules/:moduleId/units/:unitId/exercises/:exerciseId/submit
            post("/{lang}/modules/{moduleId}/units/{unitId}/exercises/{exerciseId}/submit") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class)
                val language = call.parameters["lang"]
                val moduleId = call.parameters["moduleId"]
                val unitId = call.parameters["unitId"]
                val exerciseId = call.parameters["exerciseId"]
                
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponses.unauthorized("Invalid token", call.request.local.uri)
                    )
                    return@post
                }
                
                if (language.isNullOrBlank() || moduleId.isNullOrBlank() || unitId.isNullOrBlank() || exerciseId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses.badRequest("Language, module ID, unit ID, and exercise ID are required", call.request.local.uri)
                    )
                    return@post
                }
                
                try {
                    val request = call.receive<dev.learning.SubmitExerciseRequest>()
                    
                    // Get the exercise to validate it exists and get metadata
                    val exercise = contentRepository.getExerciseDetails(userId, language, moduleId, unitId, exerciseId)
                    if (exercise == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponses.notFound("Exercise not found", call.request.local.uri)
                        )
                        return@post
                    }
                    
                    // Submit the exercise response
                    val result = contentRepository.submitExercise(
                        userId = userId,
                        lang = language,
                        moduleId = moduleId,
                        unitId = unitId,
                        exerciseId = exerciseId,
                        userAnswer = request.userAnswer,
                        answerStatus = request.answerStatus
                    )
                    
                    call.respond(HttpStatusCode.OK, result)
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
