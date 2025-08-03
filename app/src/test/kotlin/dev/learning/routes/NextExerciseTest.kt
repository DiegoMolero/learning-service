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
import kotlin.test.assertNull
import java.util.*

class NextExerciseTest {

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
    fun `GET next exercise should return first exercise when no progress`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "test_topic_1" // Using test topic instead of real data

        application {
            module(config)
        }

        // Act
        val response = client.get("/levels/$targetLanguage/$level/topics/$topicId/exercises/next") {
            header(HttpHeaders.Authorization, userJWT)
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = response.bodyAsText()
        println("Response body: $responseBody")
        
        val nextExerciseResponse = json.decodeFromString<NextExerciseResponse>(responseBody)
        println("Parsed response: $nextExerciseResponse")
        
        assertTrue(nextExerciseResponse.hasMoreExercises)
        val exercise = nextExerciseResponse.exercise
        assertNotNull(exercise)
        assertEquals("ex_1", exercise.id) // Should return the first exercise
        assertEquals(0, exercise.previousAttempts)
        assertFalse(exercise.isCompleted)
    }

    @Test
    fun `GET next exercise should return next unattempted exercise after some attempts`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "test_topic_1" // Using test topic

        application {
            module(config)
        }

        // Submit first exercise
        val request1 = SubmitAnswerRequest(
            targetLanguage = targetLanguage,
            level = level,
            topicId = topicId,
            exerciseId = "ex_1",
            userAnswer = "Hello world",
            answerStatus = AnswerStatus.CORRECT
        )

        client.post("/levels/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request1))
        }

        // Submit third exercise (skip ex_2)
        val request3 = SubmitAnswerRequest(
            targetLanguage = targetLanguage,
            level = level,
            topicId = topicId,
            exerciseId = "ex_3",
            userAnswer = "Wrong answer",
            answerStatus = AnswerStatus.INCORRECT
        )

        client.post("/levels/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request3))
        }

        // Act - Get next exercise
        val response = client.get("/levels/$targetLanguage/$level/topics/$topicId/exercises/next") {
            header(HttpHeaders.Authorization, userJWT)
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        
        val nextExerciseResponse = json.decodeFromString<NextExerciseResponse>(response.bodyAsText())
        assertTrue(nextExerciseResponse.hasMoreExercises)
        val exercise = nextExerciseResponse.exercise
        assertNotNull(exercise)
        assertEquals("ex_2", exercise.id) // Should return ex_2 as it hasn't been attempted
        assertEquals(0, exercise.previousAttempts)
        assertFalse(exercise.isCompleted)
    }

    @Test
    fun `GET next exercise should return OK with no more exercises when all exercises have been attempted`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "test_topic_1" // Using test topic with only 3 exercises

        application {
            module(config)
        }

        // Submit all 3 exercises in the test topic
        val exerciseIds = (1..3).map { "ex_$it" }
        println("About to submit ${exerciseIds.size} exercises: $exerciseIds")
        
        for (exerciseId in exerciseIds) {
            val request = SubmitAnswerRequest(
                targetLanguage = targetLanguage,
                level = level,
                topicId = topicId,
                exerciseId = exerciseId,
                userAnswer = "Test answer",
                answerStatus = AnswerStatus.CORRECT
            )

            println("Submitting exercise: $exerciseId")
            val submitResponse = client.post("/levels/submit") {
                header(HttpHeaders.Authorization, userJWT)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
            println("Submit response for $exerciseId: ${submitResponse.status}")
            if (submitResponse.status.value >= 400) {
                println("Error response body: ${submitResponse.bodyAsText()}")
            }
        }

        // Act - Get next exercise
        val response = client.get("/levels/$targetLanguage/$level/topics/$topicId/exercises/next") {
            header(HttpHeaders.Authorization, userJWT)
        }

        // Assert
        println("Response status: ${response.status}")
        println("Response body: ${response.bodyAsText()}")
        assertEquals(HttpStatusCode.OK, response.status)
        
        val nextExerciseResponse = json.decodeFromString<NextExerciseResponse>(response.bodyAsText())
        assertFalse(nextExerciseResponse.hasMoreExercises)
        assertNull(nextExerciseResponse.exercise)
        assertNotNull(nextExerciseResponse.message)
    }

    @Test
    fun `GET next exercise should work correctly with skipped and revealed exercises`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "test_topic_1" // Using test topic

        application {
            module(config)
        }

        // Submit first exercise correctly
        val request1 = SubmitAnswerRequest(
            targetLanguage = targetLanguage,
            level = level,
            topicId = topicId,
            exerciseId = "ex_1",
            userAnswer = "Hello world",
            answerStatus = AnswerStatus.CORRECT
        )

        client.post("/levels/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request1))
        }

        // Skip second exercise
        val request2 = SubmitAnswerRequest(
            targetLanguage = targetLanguage,
            level = level,
            topicId = topicId,
            exerciseId = "ex_2",
            userAnswer = "",
            answerStatus = AnswerStatus.SKIPPED
        )

        client.post("/levels/submit") {
            header(HttpHeaders.Authorization, userJWT)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(request2))
        }

        // Act - Get next exercise (should return ex_3 since ex_2 was skipped but still attempted)
        val response = client.get("/levels/$targetLanguage/$level/topics/$topicId/exercises/next") {
            header(HttpHeaders.Authorization, userJWT)
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        
        val nextExerciseResponse = json.decodeFromString<NextExerciseResponse>(response.bodyAsText())
        assertTrue(nextExerciseResponse.hasMoreExercises)
        val exercise = nextExerciseResponse.exercise
        assertNotNull(exercise)
        assertEquals("ex_3", exercise.id) // Should return ex_3 as it's the only unattempted
        assertEquals(0, exercise.previousAttempts)
        assertFalse(exercise.isCompleted)
    }

    @Test
    fun `GET next exercise should return 401 when not authenticated`() = testApplication {
        // Arrange
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "must_subjective_obligation_1"

        application {
            module(config)
        }

        // Act
        val response = client.get("/levels/$targetLanguage/$level/topics/$topicId/exercises/next")

        // Assert
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET next exercise should return OK with message when topic doesn't exist`() = testApplication {
        // Arrange
        val (_, userJWT) = createTestUser()
        val targetLanguage = "en"
        val level = "A2"
        val topicId = "non_existent_topic"

        application {
            module(config)
        }

        // Act
        val response = client.get("/levels/$targetLanguage/$level/topics/$topicId/exercises/next") {
            header(HttpHeaders.Authorization, userJWT)
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        
        val nextExerciseResponse = json.decodeFromString<NextExerciseResponse>(response.bodyAsText())
        assertFalse(nextExerciseResponse.hasMoreExercises)
        assertNull(nextExerciseResponse.exercise)
        assertEquals("Topic not found", nextExerciseResponse.message)
    }
}
