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
import java.util.*

class ExerciseSubmissionTest {

    private lateinit var repository: LearningRepository
    private lateinit var config: Config
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        config = loadConfig("test")
        repository = DatabaseLearningRepository(config.database)
    }

    private suspend fun createTestUser(): Pair<String, String> {
        val userId = UUID.randomUUID().toString()
        repository.createUser(userId)
        val jwt = JWTTestHelper.createTestJWT(config, userId = userId)
        return Pair(userId, jwt)
    }

    @Test
    fun `POST submit answer should record correct answer successfully`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "must_subjective_obligation_1"
        val exerciseId = "ex_1"
        
        val request = SubmitAnswerRequest(
            topicId = topicId,
            exerciseId = exerciseId,
            userAnswer = "I must get my hair cut.",
            answerStatus = dev.learning.AnswerStatus.CORRECT
        )

        application {
            module(config)
        }

        // Act
        val response = client.post("/levels/$targetLanguage/$level/topics/$topicId/exercises/$exerciseId/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.decodeFromString<SubmitAnswerResponse>(response.bodyAsText())
        assertTrue(responseBody.success)
        assertTrue(responseBody.answerStatus == dev.learning.AnswerStatus.CORRECT)
        assertEquals("I must get my hair cut.", responseBody.correctAnswer)
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
        val topicId = "must_subjective_obligation_1"
        val exerciseId = "ex_1"
        
        val request = SubmitAnswerRequest(
            topicId = topicId,
            exerciseId = exerciseId,
            userAnswer = "I have to get my hair cut.",
            answerStatus = dev.learning.AnswerStatus.INCORRECT
        )

        application {
            module(config)
        }

        // Act
        val response = client.post("/levels/$targetLanguage/$level/topics/$topicId/exercises/$exerciseId/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.decodeFromString<SubmitAnswerResponse>(response.bodyAsText())
        assertTrue(responseBody.success)
        assertEquals(false, responseBody.answerStatus == dev.learning.AnswerStatus.CORRECT)
        assertEquals("I must get my hair cut.", responseBody.correctAnswer)
        assertEquals(0, responseBody.progress.correctAnswers)
        assertEquals(1, responseBody.progress.wrongAnswers)
        assertEquals(0, responseBody.progress.completedExercises) // Should not advance completion on wrong answer
    }

    @Test
    fun `POST submit answer should return 401 when not authenticated`() = testApplication {
        // Arrange
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "must_subjective_obligation_1"
        val exerciseId = "ex_1"
        
        val request = SubmitAnswerRequest(
            topicId = topicId,
            exerciseId = exerciseId,
            userAnswer = "I must get my hair cut.",
            answerStatus = dev.learning.AnswerStatus.CORRECT
        )

        application {
            module(config)
        }

        // Act
        val response = client.post("/levels/$targetLanguage/$level/topics/$topicId/exercises/$exerciseId/submit") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST submit answer should return 400 when topic and exercise IDs don't match URL`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "must_subjective_obligation_1"
        val exerciseId = "ex_1"
        
        val request = SubmitAnswerRequest(
            topicId = "different_topic",
            exerciseId = exerciseId,
            userAnswer = "I must get my hair cut.",
            answerStatus = dev.learning.AnswerStatus.CORRECT
        )

        application {
            module(config)
        }

        // Act
        val response = client.post("/levels/$targetLanguage/$level/topics/$topicId/exercises/$exerciseId/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST submit answer should return 404 when exercise doesn't exist`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "must_subjective_obligation_1"
        val exerciseId = "non_existent_exercise"
        
        val request = SubmitAnswerRequest(
            topicId = topicId,
            exerciseId = exerciseId,
            userAnswer = "Some answer",
            answerStatus = dev.learning.AnswerStatus.CORRECT
        )

        application {
            module(config)
        }

        // Act
        val response = client.post("/levels/$targetLanguage/$level/topics/$topicId/exercises/$exerciseId/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        // Assert
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST submit answer should track multiple answers and progress correctly`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "must_subjective_obligation_1"
        
        application {
            module(config)
        }

        // Submit first exercise correctly
        val request1 = SubmitAnswerRequest(
            topicId = topicId,
            exerciseId = "ex_1",
            userAnswer = "I must get my hair cut.",
            answerStatus = dev.learning.AnswerStatus.CORRECT
        )

        val response1 = client.post("/levels/$targetLanguage/$level/topics/$topicId/exercises/ex_1/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request1))
        }

        // Submit second exercise incorrectly
        val request2 = SubmitAnswerRequest(
            topicId = topicId,
            exerciseId = "ex_2",
            userAnswer = "Wrong answer",
            answerStatus = dev.learning.AnswerStatus.INCORRECT
        )

        val response2 = client.post("/levels/$targetLanguage/$level/topics/$topicId/exercises/ex_2/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request2))
        }

        // Submit second exercise correctly
        val request3 = SubmitAnswerRequest(
            topicId = topicId,
            exerciseId = "ex_2",
            userAnswer = "My optician says I must wear glasses for reading.",
            answerStatus = dev.learning.AnswerStatus.CORRECT
        )

        val response3 = client.post("/levels/$targetLanguage/$level/topics/$topicId/exercises/ex_2/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request3))
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response1.status)
        assertEquals(HttpStatusCode.OK, response2.status)
        assertEquals(HttpStatusCode.OK, response3.status)
        
        val finalResponse = json.decodeFromString<SubmitAnswerResponse>(response3.bodyAsText())
        assertEquals(2, finalResponse.progress.correctAnswers)
        assertEquals(1, finalResponse.progress.wrongAnswers)
        assertEquals(2, finalResponse.progress.completedExercises) // Should have completed 2 exercises
    }
}
