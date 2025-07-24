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
        assertEquals("550e8400-e29b-41d4-a716-446655440000", settings.userId)
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

    @Test
    fun `complete onboarding flow should progress through all phases correctly`() = testApplication {
        application {
            module(config)
        }

        val userJWT = JWTTestHelper.createTestJWT(config)

        // Phase 1: Check initial state - should be 'native' phase by default
        val initialResponse = client.get("/settings") {
            header(HttpHeaders.Authorization, userJWT)
        }
        assertEquals(HttpStatusCode.OK, initialResponse.status)
        val initialSettings = json.decodeFromString<UserSettingsResponse>(initialResponse.bodyAsText())
        assertEquals("native", initialSettings.onboardingStep)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", initialSettings.userId)

        // Phase 2: User selects native language and progresses to 'learning' phase
        val nativeLanguageRequest = UpdateUserSettingsRequest(
            nativeLanguage = "es",
            onboardingStep = "learning"
        )
        val nativeResponse = client.put("/settings") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(nativeLanguageRequest))
        }
        assertEquals(HttpStatusCode.OK, nativeResponse.status)
        
        // Verify phase 2 state
        val phase2Response = client.get("/settings") {
            header(HttpHeaders.Authorization, userJWT)
        }
        assertEquals(HttpStatusCode.OK, phase2Response.status)
        val phase2Settings = json.decodeFromString<UserSettingsResponse>(phase2Response.bodyAsText())
        assertEquals("es", phase2Settings.nativeLanguage)
        assertEquals("en", phase2Settings.targetLanguage) // Should auto-switch
        assertEquals("learning", phase2Settings.onboardingStep)

        // Phase 3: User selects target language and completes onboarding
        val completeRequest = UpdateUserSettingsRequest(
            targetLanguage = "en",
            onboardingStep = "complete"
        )
        val completeResponse = client.put("/settings") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(completeRequest))
        }
        assertEquals(HttpStatusCode.OK, completeResponse.status)
        
        // Verify final completed state
        val finalResponse = client.get("/settings") {
            header(HttpHeaders.Authorization, userJWT)
        }
        assertEquals(HttpStatusCode.OK, finalResponse.status)
        val finalSettings = json.decodeFromString<UserSettingsResponse>(finalResponse.bodyAsText())
        assertEquals("es", finalSettings.nativeLanguage)
        assertEquals("en", finalSettings.targetLanguage)
        assertEquals("complete", finalSettings.onboardingStep)

        // Test that user can still update settings after onboarding completion
        val postOnboardingRequest = UpdateUserSettingsRequest(darkMode = true)
        val postOnboardingResponse = client.put("/settings") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(postOnboardingRequest))
        }
        assertEquals(HttpStatusCode.OK, postOnboardingResponse.status)
        
        // Verify dark mode was updated but onboarding phase remains complete
        val postOnboardingGetResponse = client.get("/settings") {
            header(HttpHeaders.Authorization, userJWT)
        }
        assertEquals(HttpStatusCode.OK, postOnboardingGetResponse.status)
        val postOnboardingSettings = json.decodeFromString<UserSettingsResponse>(postOnboardingGetResponse.bodyAsText())
        assertEquals(true, postOnboardingSettings.darkMode)
        assertEquals("complete", postOnboardingSettings.onboardingStep)
    }

    @Test
    fun `onboarding should not allow completion without both languages set`() = testApplication {
        application {
            module(config)
        }

        val userJWT = JWTTestHelper.createTestJWT(config)

        // Try to complete onboarding without setting both languages
        val prematureCompleteRequest = UpdateUserSettingsRequest(
            onboardingStep = "complete"
        )
        val prematureResponse = client.put("/settings") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(prematureCompleteRequest))
        }
        
        // Should still succeed but not actually complete onboarding
        assertEquals(HttpStatusCode.OK, prematureResponse.status)
        
        // Verify onboarding phase was not changed to complete
        val checkResponse = client.get("/settings") {
            header(HttpHeaders.Authorization, userJWT)
        }
        assertEquals(HttpStatusCode.OK, checkResponse.status)
        val settings = json.decodeFromString<UserSettingsResponse>(checkResponse.bodyAsText())
        // Should still be in native phase since completion was ignored
        assertTrue(settings.onboardingStep != "complete")
    }

    @Test
    fun `onboarding should validate phase values`() = testApplication {
        application {
            module(config)
        }

        val userJWT = JWTTestHelper.createTestJWT(config)

        // Try to set an invalid onboarding phase
        val invalidPhaseRequest = UpdateUserSettingsRequest(
            onboardingStep = "invalid_phase"
        )
        val invalidResponse = client.put("/settings") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(invalidPhaseRequest))
        }
        
        // Should return 500 due to validation error
        assertEquals(HttpStatusCode.InternalServerError, invalidResponse.status)
        
        // Verify original phase is unchanged
        val checkResponse = client.get("/settings") {
            header(HttpHeaders.Authorization, userJWT)
        }
        assertEquals(HttpStatusCode.OK, checkResponse.status)
        val settings = json.decodeFromString<UserSettingsResponse>(checkResponse.bodyAsText())
        assertEquals("native", settings.onboardingStep) // Should remain default
    }

    @Test
    fun `onboarding flow should handle language conflicts during target language selection`() = testApplication {
        application {
            module(config)
        }

        val userJWT = JWTTestHelper.createTestJWT(config)

        // Step 1: User selects native language "es" and moves to learning phase
        val step1Request = UpdateUserSettingsRequest(
            nativeLanguage = "es",
            onboardingStep = "learning"
        )
        val step1Response = client.put("/settings") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(step1Request))
        }
        assertEquals(HttpStatusCode.OK, step1Response.status)
        
        // Verify step 1 - native language is "es", target should auto-switch to "en"
        val afterStep1 = client.get("/settings") {
            header(HttpHeaders.Authorization, userJWT)
        }
        assertEquals(HttpStatusCode.OK, afterStep1.status)
        val step1Settings = json.decodeFromString<UserSettingsResponse>(afterStep1.bodyAsText())
        assertEquals("es", step1Settings.nativeLanguage)
        assertEquals("en", step1Settings.targetLanguage) // Auto-switched
        assertEquals("learning", step1Settings.onboardingStep)

        // Step 2: User tries to select target language "es" (same as current native)
        // This should auto-switch the native language to "en" to avoid conflict
        val step2Request = UpdateUserSettingsRequest(
            targetLanguage = "es",
            onboardingStep = "complete"
        )
        val step2Response = client.put("/settings") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(step2Request))
        }
        assertEquals(HttpStatusCode.OK, step2Response.status)
        
        // Verify step 2 - target language is "es", native should auto-switch to "en"
        val afterStep2 = client.get("/settings") {
            header(HttpHeaders.Authorization, userJWT)
        }
        assertEquals(HttpStatusCode.OK, afterStep2.status)
        val step2Settings = json.decodeFromString<UserSettingsResponse>(afterStep2.bodyAsText())
        assertEquals("en", step2Settings.nativeLanguage) // Auto-switched
        assertEquals("es", step2Settings.targetLanguage)
        assertEquals("complete", step2Settings.onboardingStep)

        // Verify that the response includes a warning about the auto-switch
        val step2Result = json.decodeFromString<UpdateUserSettingsResponse>(step2Response.bodyAsText())
        assertTrue(step2Result.warnings.any { it.contains("automatically changed") })
    }
}
