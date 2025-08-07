package dev.learning.routes

import dev.learning.*
import dev.learning.repository.UserRepository
import dev.learning.repository.DatabaseUserRepository
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContains
import java.util.*

class UserManagementRouteTest {

  private lateinit var repository: UserRepository
  private lateinit var config: Config
  private val json = Json { ignoreUnknownKeys = true }

  @BeforeEach
  fun setUp() {
    config = loadConfig("test")
    repository = DatabaseUserRepository(config.database)
  }

  private fun HttpRequestBuilder.addInternalSecret() {
    header("X-Internal-Secret", config.internalSecret)
  }

  // AUTHENTICATION TESTS

  @Test
  fun `POST users should return 401 when X-Internal-Secret header is missing`() = testApplication {
    // Arrange
    val userId = UUID.randomUUID().toString()
    val request = CreateUserRequest(userId = userId)

    application {
      module(config)
    }

    // Act
    val response = client.post("/users") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      setBody(json.encodeToString(request))
    }

    // Assert
    assertEquals(HttpStatusCode.Unauthorized, response.status)

    val responseBody = json.decodeFromString<UserManagementResponse>(response.bodyAsText())
    assertEquals(false, responseBody.success)
    assertEquals("Unauthorized access", responseBody.message)
  }

  @Test
  fun `POST users should return 401 when X-Internal-Secret header is incorrect`() = testApplication {
    // Arrange
    val userId = UUID.randomUUID().toString()
    val request = CreateUserRequest(userId = userId)

    application {
      module(config)
    }

    // Act
    val response = client.post("/users") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      header("X-Internal-Secret", "wrong-secret")
      setBody(json.encodeToString(request))
    }

    // Assert
    assertEquals(HttpStatusCode.Unauthorized, response.status)

    val responseBody = json.decodeFromString<UserManagementResponse>(response.bodyAsText())
    assertEquals(false, responseBody.success)
    assertEquals("Unauthorized access", responseBody.message)
  }

  @Test
  fun `DELETE users should return 401 when X-Internal-Secret header is missing`() = testApplication {
    // Arrange
    val userId = UUID.randomUUID().toString()

    application {
      module(config)
    }

    // Act
    val response = client.delete("/users/$userId")

    // Assert
    assertEquals(HttpStatusCode.Unauthorized, response.status)

    val responseBody = json.decodeFromString<UserManagementResponse>(response.bodyAsText())
    assertEquals(false, responseBody.success)
    assertEquals("Unauthorized access", responseBody.message)
  }

  // CREATE USER TESTS

  @Test
  fun `POST users should create user successfully with valid request`() = testApplication {
    // Arrange
    val userId = UUID.randomUUID().toString()
    val request = CreateUserRequest(userId = userId)

    application {
      module(config)
    }

    // Act
    val response = client.post("/users") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      addInternalSecret()
      setBody(json.encodeToString(request))
    }

    // Assert
    assertEquals(HttpStatusCode.Created, response.status)

    val responseBody = json.decodeFromString<UserManagementResponse>(response.bodyAsText())
    assertTrue(responseBody.success)
    assertEquals("User created successfully", responseBody.message)
    assertEquals(userId, responseBody.userId)
  }

  @Test
  fun `POST users should return 400 for empty userId`() = testApplication {
    // Arrange
    val request = CreateUserRequest(userId = "")

    application {
      module(config)
    }

    // Act
    val response = client.post("/users") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      addInternalSecret()
      setBody(json.encodeToString(request))
    }

    // Assert
    assertEquals(HttpStatusCode.BadRequest, response.status)

    val responseBody = json.decodeFromString<UserManagementResponse>(response.bodyAsText())
    assertEquals(false, responseBody.success)
    assertEquals("User ID is required", responseBody.message)
  }

  @Test
  fun `POST users should return 400 for blank userId`() = testApplication {
    // Arrange
    val request = CreateUserRequest(userId = "   ")

    application {
      module(config)
    }

    // Act
    val response = client.post("/users") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      addInternalSecret()
      setBody(json.encodeToString(request))
    }

    // Assert
    assertEquals(HttpStatusCode.BadRequest, response.status)

    val responseBody = json.decodeFromString<UserManagementResponse>(response.bodyAsText())
    assertEquals(false, responseBody.success)
    assertEquals("User ID is required", responseBody.message)
  }

  @Test
  fun `POST users should return 400 for invalid JSON`() = testApplication {
    application {
      module(config)
    }

    // Act
    val response = client.post("/users") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      addInternalSecret()
      setBody("{ invalid json }")
    }

    // Assert
    assertEquals(HttpStatusCode.BadRequest, response.status)

    val responseBody = json.decodeFromString<UserManagementResponse>(response.bodyAsText())
    assertEquals(false, responseBody.success)
    assertEquals("Invalid request body", responseBody.message)
  }

  @Test
  fun `POST users should return 400 for invalid UUID format`() = testApplication {
    // Arrange
    val request = CreateUserRequest(userId = "invalid-uuid-format")

    application {
      module(config)
    }

    // Act
    val response = client.post("/users") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      addInternalSecret()
      setBody(json.encodeToString(request))
    }

    // Assert
    assertEquals(HttpStatusCode.BadRequest, response.status)

    val responseBody = json.decodeFromString<UserManagementResponse>(response.bodyAsText())
    assertEquals(false, responseBody.success)
    assertEquals("Invalid user ID format", responseBody.message)
  }

  @Test
  fun `POST users should handle duplicate user creation gracefully`() = testApplication {
    // Arrange
    val userId = UUID.randomUUID().toString()
    val request = CreateUserRequest(userId = userId)

    application {
      module(config)
    }

    // Act - Create user first time
    val firstResponse = client.post("/users") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      addInternalSecret()
      setBody(json.encodeToString(request))
    }

    // Act - Try to create same user again
    val secondResponse = client.post("/users") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      addInternalSecret()
      setBody(json.encodeToString(request))
    }

    // Assert - First creation should succeed
    assertEquals(HttpStatusCode.Created, firstResponse.status)

    // Assert - Second creation should fail gracefully
    assertEquals(HttpStatusCode.InternalServerError, secondResponse.status)

    val responseBody = json.decodeFromString<UserManagementResponse>(secondResponse.bodyAsText())
    assertEquals(false, responseBody.success)
    assertEquals("Failed to create user", responseBody.message)
  }

  // DELETE USER TESTS

  @Test
  fun `DELETE users should delete user successfully`() = testApplication {
    // Arrange
    val userId = UUID.randomUUID().toString()
    val createRequest = CreateUserRequest(userId = userId)

    application {
      module(config)
    }

    // Create user first
    client.post("/users") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      addInternalSecret()
      setBody(json.encodeToString(createRequest))
    }

    // Act - Delete user
    val response = client.delete("/users/$userId") {
      addInternalSecret()
    }

    // Assert
    assertEquals(HttpStatusCode.OK, response.status)

    val responseBody = json.decodeFromString<UserManagementResponse>(response.bodyAsText())
    assertTrue(responseBody.success)
    assertEquals("User deleted successfully", responseBody.message)
    assertEquals(userId, responseBody.userId)
  }

  @Test
  fun `DELETE users should return 400 for missing userId`() = testApplication {
    application {
      module(config)
    }

    // Act
    val response = client.delete("/users/")

    // Assert
    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  @Test
  fun `DELETE users should return 400 for blank userId`() = testApplication {
    application {
      module(config)
    }

    // Act - Use URL encoding for spaces to ensure they reach the handler
    val response = client.delete("/users/%20%20%20") {
      addInternalSecret()
    }

    // Assert
    assertEquals(HttpStatusCode.BadRequest, response.status)

    val responseBody = json.decodeFromString<UserManagementResponse>(response.bodyAsText())
    assertEquals(false, responseBody.success)
    assertEquals("User ID is required", responseBody.message)
  }

  @Test
  fun `DELETE users should return 400 for invalid UUID format`() = testApplication {
    application {
      module(config)
    }

    // Act
    val response = client.delete("/users/invalid-uuid") {
      addInternalSecret()
    }

    // Assert
    assertEquals(HttpStatusCode.BadRequest, response.status)

    val responseBody = json.decodeFromString<UserManagementResponse>(response.bodyAsText())
    assertEquals(false, responseBody.success)
    assertEquals("Invalid user ID format", responseBody.message)
  }

  @Test
  fun `DELETE users should handle non-existent user gracefully`() = testApplication {
    // Arrange
    val nonExistentUserId = UUID.randomUUID().toString()

    application {
      module(config)
    }

    // Act
    val response = client.delete("/users/$nonExistentUserId") {
      addInternalSecret()
    }

    // Assert - Should still return success for idempotency
    assertEquals(HttpStatusCode.OK, response.status)

    val responseBody = json.decodeFromString<UserManagementResponse>(response.bodyAsText())
    assertTrue(responseBody.success)
    assertEquals("User deleted successfully", responseBody.message)
    assertEquals(nonExistentUserId, responseBody.userId)
  }

  // INTEGRATION TESTS

  @Test
  fun `Full user lifecycle should work correctly`() = testApplication {
    // Arrange
    val userId = UUID.randomUUID().toString()
    val createRequest = CreateUserRequest(userId = userId)

    application {
      module(config)
    }

    // Act & Assert - Create user
    val createResponse = client.post("/users") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      addInternalSecret()
      setBody(json.encodeToString(createRequest))
    }
    assertEquals(HttpStatusCode.Created, createResponse.status)

    // Act & Assert - Delete user
    val deleteResponse = client.delete("/users/$userId") {
      addInternalSecret()
    }
    assertEquals(HttpStatusCode.OK, deleteResponse.status)
  }

  @Test
  fun `User management endpoints should require X-Internal-Secret authentication`() = testApplication {
    // Arrange
    val userId = UUID.randomUUID().toString()
    val request = CreateUserRequest(userId = userId)

    application {
      module(config)
    }

    // Act - All requests without X-Internal-Secret header should return 401
    val createResponse = client.post("/users") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      setBody(json.encodeToString(request))
    }

    val deleteResponse = client.delete("/users/$userId")

    // Assert - All should return 401 Unauthorized
    assertEquals(HttpStatusCode.Unauthorized, createResponse.status)
    assertEquals(HttpStatusCode.Unauthorized, deleteResponse.status)
  }
}
