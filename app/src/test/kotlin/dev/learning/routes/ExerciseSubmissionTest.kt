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
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import java.util.*

class ExerciseSubmissionTest {

    private lateinit var repository: LearningRepository
    private lateinit var config: Config
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        config = loadConfig("test")
        repository = DatabaseLearningRepository(config.database, config.environmentName)
    }

    private fun createTestUser(): Pair<String, String> {
        val userId = UUID.randomUUID().toString()
        val userJWT = JWTTestHelper.createTestJWT(config, userId = userId)
        return Pair(userId, userJWT)
    }

    @Test
    fun `POST submit answer should record correct answer successfully`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "test_topic_1"
        val exerciseId = "ex_1"
        
        val request = SubmitAnswerRequest(
            targetLanguage = targetLanguage,
            levelId = level,
            topicId = topicId,
            exerciseId = exerciseId,
            userAnswer = "Hello world",
            answerStatus = dev.learning.AnswerStatus.CORRECT
        )

        application {
            module(config)
        }

        // Act
        val response = client.post("/levels/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.decodeFromString<SubmitAnswerResponse>(response.bodyAsText())
        assertTrue(responseBody.success)
        assertTrue(responseBody.answerStatus == dev.learning.AnswerStatus.CORRECT)
        assertEquals("Hello world", responseBody.correctAnswer)
        assertEquals(1, responseBody.progress.correctAnswers)
        assertEquals(0, responseBody.progress.wrongAnswers)
        assertEquals(1, responseBody.progress.completedExercises)
    }

    @Test
    fun `POST submit answer should record wrong answer successfully`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "test_topic_1"
        val exerciseId = "ex_1"
        
        val request = SubmitAnswerRequest(
            targetLanguage = targetLanguage,
            levelId = level,
            topicId = topicId,
            exerciseId = exerciseId,
            userAnswer = "Hi world",
            answerStatus = dev.learning.AnswerStatus.INCORRECT
        )

        application {
            module(config)
        }

        // Act
        val response = client.post("/levels/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.decodeFromString<SubmitAnswerResponse>(response.bodyAsText())
        assertTrue(responseBody.success)
        assertTrue(responseBody.answerStatus == dev.learning.AnswerStatus.INCORRECT)
        assertEquals("Hello world", responseBody.correctAnswer)
        assertEquals(0, responseBody.progress.correctAnswers)
        assertEquals(1, responseBody.progress.wrongAnswers)
        assertEquals(0, responseBody.progress.completedExercises)
    }

    @Test
    fun `POST submit answer should return 401 when not authenticated`() = testApplication {
        // Arrange
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "test_topic_1"
        val exerciseId = "ex_1"
        
        val request = SubmitAnswerRequest(
            targetLanguage = targetLanguage,
            levelId = level,
            topicId = topicId,
            exerciseId = exerciseId,
            userAnswer = "Hello world",
            answerStatus = dev.learning.AnswerStatus.CORRECT
        )

        application {
            module(config)
        }

        // Act
        val response = client.post("/levels/submit") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST submit answer should return 404 when exercise doesn't exist`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "test_topic_1"
        val exerciseId = "non_existent_exercise"
        
        val request = SubmitAnswerRequest(
            targetLanguage = targetLanguage,
            levelId = level,
            topicId = topicId,
            exerciseId = exerciseId,
            userAnswer = "Hello world",
            answerStatus = dev.learning.AnswerStatus.CORRECT
        )

        application {
            module(config)
        }

        // Act
        val response = client.post("/levels/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST submit answer should return 400 when request body is invalid`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()

        application {
            module(config)
        }

        // Act
        val response = client.post("/levels/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("invalid json")
        }

        // Assert
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST submit answer should handle multiple submissions for same exercise`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "test_topic_1"
        val exerciseId = "ex_1"

        application {
            module(config)
        }

        // First submission (incorrect)
        val request1 = SubmitAnswerRequest(
            targetLanguage = targetLanguage,
            levelId = level,
            topicId = topicId,
            exerciseId = exerciseId,
            userAnswer = "Wrong answer",
            answerStatus = dev.learning.AnswerStatus.CORRECT
        )

        client.post("/levels/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request1))
        }

        // Second submission (incorrect again)
        val request2 = SubmitAnswerRequest(
            targetLanguage = targetLanguage,
            levelId = level,
            topicId = topicId,
            exerciseId = exerciseId,
            userAnswer = "Still wrong",
            answerStatus = dev.learning.AnswerStatus.INCORRECT
        )

        val response2 = client.post("/levels/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request2))
        }

        // Third submission (correct)
        val request3 = SubmitAnswerRequest(
            targetLanguage = targetLanguage,
            levelId = level,
            topicId = topicId,
            exerciseId = exerciseId,
            userAnswer = "Hello world",
            answerStatus = dev.learning.AnswerStatus.CORRECT
        )

        val response3 = client.post("/levels/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request3))
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response2.status)
        assertEquals(HttpStatusCode.OK, response3.status)
        
        val responseBody3 = json.decodeFromString<SubmitAnswerResponse>(response3.bodyAsText())
        assertTrue(responseBody3.success)
        // Should reflect multiple attempts
        assertTrue(responseBody3.progress.completedExercises >= 1)
    }

    @Test
    fun `POST submit answer should handle SKIPPED status successfully`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "test_topic_1"
        val exerciseId = "ex_1"
        
        val request = SubmitAnswerRequest(
            targetLanguage = targetLanguage,
            levelId = level,
            topicId = topicId,
            exerciseId = exerciseId,
            userAnswer = "",
            answerStatus = dev.learning.AnswerStatus.SKIPPED
        )

        application {
            module(config)
        }

        // Act
        val response = client.post("/levels/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.decodeFromString<SubmitAnswerResponse>(response.bodyAsText())
        assertTrue(responseBody.success)
        assertTrue(responseBody.answerStatus == dev.learning.AnswerStatus.SKIPPED)
        assertEquals("Hello world", responseBody.correctAnswer)
        assertEquals(0, responseBody.progress.correctAnswers)
        assertEquals(0, responseBody.progress.wrongAnswers)
        // Skipped exercises still count as attempted but not completed
        assertEquals(0, responseBody.progress.completedExercises)
    }
}
