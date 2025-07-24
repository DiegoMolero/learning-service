package dev.learning

import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Serializable
data class ErrorResponse(
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val type: String,
    val message: String,
    val status: Int,
    val timestamp: String,
    val path: String
)

object ErrorTypes {
    const val INVALID_EMAIL = "INVALID_EMAIL"
    const val INVALID_PASSWORD = "INVALID_PASSWORD"
    const val INVALID_NAME = "INVALID_NAME"
    const val INVALID_INPUT = "INVALID_INPUT"
    const val USER_ALREADY_EXISTS = "USER_ALREADY_EXISTS"
    const val USER_NOT_FOUND = "USER_NOT_FOUND"
    const val USER_UPDATE_FAILED = "USER_UPDATE_FAILED"
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val USER_CREATION_FAILED = "USER_CREATION_FAILED"
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
}

fun createErrorResponse(
    type: String,
    message: String,
    status: Int,
    path: String
): ErrorResponse {
    return ErrorResponse(
        error = ErrorDetail(
            type = type,
            message = message,
            status = status,
            timestamp = Clock.System.now().toString(),
            path = path
        )
    )
}

// Common error responses
object ErrorResponses {
    fun badRequest(message: String, path: String) = createErrorResponse("BAD_REQUEST", message, 400, path)
    fun unauthorized(message: String = "Unauthorized", path: String) = createErrorResponse("UNAUTHORIZED", message, 401, path)
    fun forbidden(message: String = "Forbidden", path: String) = createErrorResponse("FORBIDDEN", message, 403, path)
    fun notFound(message: String, path: String) = createErrorResponse("NOT_FOUND", message, 404, path)
    fun conflict(message: String, path: String) = createErrorResponse("CONFLICT", message, 409, path)
    fun internalServerError(message: String = "Internal Server Error", path: String) = createErrorResponse("INTERNAL_SERVER_ERROR", message, 500, path)
}
