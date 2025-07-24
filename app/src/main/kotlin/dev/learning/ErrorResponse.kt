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
