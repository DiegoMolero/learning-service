package dev.learning.routes

import dev.learning.*
import dev.learning.repository.DatabaseLearningRepository
import dev.learning.repository.LearningRepository
import dev.learning.test.utils.JWTTestHelper
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

class SettingsRouteTest {

    private lateinit var repository: LearningRepository
    private lateinit var config: Config
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        config = loadConfig("test")
        repository = DatabaseLearningRepository(config.database)
    }

    @Test
    fun `GET settings should return 401 without valid token`() = testApplication {
        application {
            module(config)
        }

        // Act
        val response = client.get("/settings")

        // Assert
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `PUT settings should return 401 without valid token`() = testApplication {
        // Arrange
        val request = UpdateUserSettingsRequest(darkMode = true)

        application {
            module(config)
        }

        // Act
        val response = client.put("/settings") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `PUT settings should return 400 for invalid JSON`() = testApplication {
        application {
            module(config)
        }

        // Act
        val response = client.put("/settings") {
            header(HttpHeaders.Authorization, JWTTestHelper.createTestJWT(config))
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("{ invalid json }")
        }

        // Assert
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT settings should reject same native and target language`() = testApplication {
        // Arrange
        val request = UpdateUserSettingsRequest(
            nativeLanguage = "en",
            targetLanguage = "en"
        )

        application {
            module(config)
        }

        // Act
        val response = client.put("/settings") {
            header(HttpHeaders.Authorization, JWTTestHelper.createTestJWT(config))
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseText = response.bodyAsText()
        assertContains(responseText, "cannot be the same")
    }

    @Test
    fun `PUT settings should reject unsupported language`() = testApplication {
        // Arrange
        val request = UpdateUserSettingsRequest(nativeLanguage = "fr")

        application {
            module(config)
        }

        // Act
        val response = client.put("/settings") {
            header(HttpHeaders.Authorization, JWTTestHelper.createTestJWT(config))
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseText = response.bodyAsText()
        assertContains(responseText, "Unsupported native language")
    }

    @Test
    fun `GET settings should return default settings for valid token`() = testApplication {
        application {
            module(config)
        }

        // Act
        val response = client.get("/settings") {
            header(HttpHeaders.Authorization, JWTTestHelper.createTestJWT(config))
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        val settings = json.decodeFromString<UserSettingsResponse>(response.bodyAsText())
        assertEquals("test-user-123", settings.userId)
        assertTrue(listOf("en", "es").contains(settings.nativeLanguage))
        assertTrue(listOf("en", "es").contains(settings.targetLanguage))
        assertTrue(settings.nativeLanguage != settings.targetLanguage)
    }

    @Test
    fun `PUT settings should update dark mode successfully`() = testApplication {
        // Arrange
        val request = UpdateUserSettingsRequest(darkMode = true)

        application {
            module(config)
        }

        // Act
        val response = client.put("/settings") {
            header(HttpHeaders.Authorization, JWTTestHelper.createTestJWT(config))
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        val result = json.decodeFromString<UpdateUserSettingsResponse>(response.bodyAsText())
        assertEquals(true, result.settings.darkMode)
        
        // Verify GET returns updated settings
        val getResponse = client.get("/settings") {
            header(HttpHeaders.Authorization, JWTTestHelper.createTestJWT(config))
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val getSettings = json.decodeFromString<UserSettingsResponse>(getResponse.bodyAsText())
        assertEquals(true, getSettings.darkMode)
    }

    @Test
    fun `PUT settings should update native language successfully`() = testApplication {
        // Arrange
        val request = UpdateUserSettingsRequest(nativeLanguage = "es")

        application {
            module(config)
        }

        // Act
        val response = client.put("/settings") {
            header(HttpHeaders.Authorization, JWTTestHelper.createTestJWT(config))
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        val result = json.decodeFromString<UpdateUserSettingsResponse>(response.bodyAsText())
        assertEquals("es", result.settings.nativeLanguage)
        assertEquals("en", result.settings.targetLanguage) // Should auto-switch to avoid conflict
        
        // Verify GET returns updated settings
        val getResponse = client.get("/settings") {
            header(HttpHeaders.Authorization, JWTTestHelper.createTestJWT(config))
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val getSettings = json.decodeFromString<UserSettingsResponse>(getResponse.bodyAsText())
        assertEquals("es", getSettings.nativeLanguage)
        assertEquals("en", getSettings.targetLanguage)
    }

    @Test
    fun `createTestJWT should generate different tokens for different users`() {
        // Arrange & Act
        val token1 = JWTTestHelper.createTestJWT(config, "user1@example.com", "user-1")
        val token2 = JWTTestHelper.createTestJWT(config, "user2@example.com", "user-2")

        // Assert
        assertTrue(token1 != token2)
        assertTrue(token1.startsWith("Bearer "))
        assertTrue(token2.startsWith("Bearer "))
    }

    @Test
    fun `createTestJWT should create valid JWT with correct claims`() {
        // Arrange
        val email = "test@domain.com"
        val userId = "user-456"

        // Act
        val tokenString = JWTTestHelper.createTestJWT(config, email, userId)
        
        // Assert
        val decodedJWT = JWTTestHelper.verifyTestJWT(config, tokenString)
        
        assertEquals(userId, decodedJWT.subject)
        assertEquals(email, decodedJWT.getClaim("email").asString())
        assertEquals(config.issuer, decodedJWT.issuer)
        assertTrue(decodedJWT.expiresAt.after(Date()))
    }
}
