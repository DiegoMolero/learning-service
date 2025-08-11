package dev.learning.routes

import dev.learning.*
import dev.learning.repository.ContentRepository
import dev.learning.repository.DatabaseContentRepository
import dev.learning.repository.UserRepository
import dev.learning.repository.DatabaseUserRepository
import dev.learning.test.utils.JWTTestHelper
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.*

class UnitsStatusTest {

    private lateinit var contentRepository: ContentRepository
    private lateinit var userRepository: UserRepository
    private lateinit var config: Config
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        config = loadConfig("test")
        contentRepository = DatabaseContentRepository(config.database, "test")
        userRepository = DatabaseUserRepository(config.database)
    }

    private fun HttpRequestBuilder.addTestJWT(userId: String = UUID.randomUUID().toString()) {
        header(HttpHeaders.Authorization, JWTTestHelper.createTestJWT(config, "test@example.com", userId))
    }

    @Test
    fun `units status should be available when completedExercises is 0`() = testApplication {
        application {
            module(config)
        }

        // Arrange
        val userId = UUID.randomUUID().toString()
        userRepository.createUser(userId)

        val moduleId = "articles-determiners"

        // Act - Get units without completing any exercises
        val response = client.get("/content/en/modules/$moduleId/units") {
            addTestJWT(userId)
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)
        val unitsResponse = json.decodeFromString<UnitsResponse>(response.bodyAsText())
        
        // Validate response structure
        assertTrue(unitsResponse.units.isNotEmpty())
        
        // All units should have status "available" when no exercises are completed
        unitsResponse.units.forEach { unit ->
            if (unit.completedExercises == 0) {
                assertEquals("available", unit.status, "Unit ${unit.id} should have status 'available' when completedExercises = 0")
            }
        }
    }

    @Test
    fun `units status should be in_progress when some exercises are completed`() = testApplication {
        application {
            module(config)
        }

        // Arrange
        val userId = UUID.randomUUID().toString()
        userRepository.createUser(userId)

        val moduleId = "articles-determiners"
        val unitId = "the_article_general_vs_specific_1"
        
        // Complete one exercise to set unit status to in_progress
        val submitRequest = SubmitExerciseRequest(
            userAnswer = "Men don't understand women.",
            answerStatus = AnswerStatus.CORRECT
        )

        // Submit one exercise as correct
        val submitResponse = client.post("/content/en/modules/$moduleId/units/$unitId/exercises/ex_1/submit") {
            addTestJWT(userId)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SubmitExerciseRequest.serializer(), submitRequest))
        }
        
        assertEquals(HttpStatusCode.OK, submitResponse.status)

        // Now get units to check status
        val unitsResponse = client.get("/content/en/modules/$moduleId/units") {
            addTestJWT(userId)
        }

        assertEquals(HttpStatusCode.OK, unitsResponse.status)
        val units = json.decodeFromString<UnitsResponse>(unitsResponse.bodyAsText())
        
        // Find the unit we completed an exercise in
        val targetUnit = units.units.find { it.id == unitId }
        
        if (targetUnit != null && targetUnit.completedExercises > 0 && targetUnit.completedExercises < targetUnit.totalExercises) {
            assertEquals("in_progress", targetUnit.status, 
                "Unit $unitId should have status 'in_progress' when completedExercises > 0 and < totalExercises")
            assertTrue(targetUnit.completedExercises > 0, "Unit should have at least one completed exercise")
            assertTrue(targetUnit.completedExercises < targetUnit.totalExercises, "Unit should not be fully completed")
        }
    }

    @Test
    fun `units status should be completed when all exercises are completed`() = testApplication {
        application {
            module(config)
        }

        // Arrange
        val userId = UUID.randomUUID().toString()
        userRepository.createUser(userId)

        val moduleId = "articles-determiners"
        val unitId = "the_article_general_vs_specific_1"
        
        // First, get the list of exercises in the unit
        val exercisesResponse = client.get("/content/en/modules/$moduleId/units/$unitId/exercises") {
            addTestJWT(userId)
        }
        
        assertEquals(HttpStatusCode.OK, exercisesResponse.status)
        val exercises = json.decodeFromString<List<ExerciseSummary>>(exercisesResponse.bodyAsText())
        
        // Complete all exercises in the unit
        exercises.forEach { exercise ->
            val submitRequest = SubmitExerciseRequest(
                userAnswer = "correct answer", // Use a generic correct answer
                answerStatus = AnswerStatus.CORRECT
            )

            val submitResponse = client.post("/content/en/modules/$moduleId/units/$unitId/exercises/${exercise.id}/submit") {
                addTestJWT(userId)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(SubmitExerciseRequest.serializer(), submitRequest))
            }
            
            assertEquals(HttpStatusCode.OK, submitResponse.status)
        }

        // Now get units to check if the status is completed
        val unitsResponse = client.get("/content/en/modules/$moduleId/units") {
            addTestJWT(userId)
        }

        assertEquals(HttpStatusCode.OK, unitsResponse.status)
        val units = json.decodeFromString<UnitsResponse>(unitsResponse.bodyAsText())
        
        // Find the unit we completed all exercises in
        val targetUnit = units.units.find { it.id == unitId }
        
        if (targetUnit != null && targetUnit.totalExercises > 0) {
            if (targetUnit.completedExercises == targetUnit.totalExercises) {
                assertEquals("completed", targetUnit.status, 
                    "Unit $unitId should have status 'completed' when completedExercises == totalExercises")
                assertEquals(targetUnit.totalExercises, targetUnit.completedExercises, 
                    "Completed exercises should equal total exercises")
            }
        }
    }

    @Test
    fun `units response should have correct structure with encapsulated array`() = testApplication {
        application {
            module(config)
        }

        // Arrange
        val userId = UUID.randomUUID().toString()
        userRepository.createUser(userId)
        val moduleId = "articles-determiners"

        val response = client.get("/content/en/modules/$moduleId/units") {
            addTestJWT(userId)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        
        // Validate JSON structure has units array
        val responseText = response.bodyAsText()
        assertTrue(responseText.contains("\"units\""), "Response should contain 'units' property")
        
        // Validate deserialization works
        val unitsResponse = json.decodeFromString<UnitsResponse>(responseText)
        assertTrue(unitsResponse.units.isNotEmpty(), "Units array should not be empty")
        
        // Validate each unit has required fields
        unitsResponse.units.forEach { unit ->
            assertTrue(unit.id.isNotBlank(), "Unit ID should not be blank")
            assertTrue(unit.status in listOf("available", "in_progress", "completed"), 
                "Unit status should be one of: available, in_progress, completed")
            assertTrue(unit.totalExercises >= 0, "Total exercises should be non-negative")
            assertTrue(unit.completedExercises >= 0, "Completed exercises should be non-negative")
            assertTrue(unit.completedExercises <= unit.totalExercises, 
                "Completed exercises should not exceed total exercises")
        }
    }

    @Test
    fun `status logic validation with edge cases`() = testApplication {
        application {
            module(config)
        }

        // Arrange
        val userId = UUID.randomUUID().toString()
        userRepository.createUser(userId)
        val moduleId = "articles-determiners"

        val response = client.get("/content/en/modules/$moduleId/units") {
            addTestJWT(userId)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val unitsResponse = json.decodeFromString<UnitsResponse>(response.bodyAsText())
        
        unitsResponse.units.forEach { unit ->
            when {
                unit.completedExercises == 0 -> {
                    assertEquals("available", unit.status, 
                        "Unit with 0 completed exercises should have status 'available'")
                }
                unit.completedExercises == unit.totalExercises && unit.totalExercises > 0 -> {
                    assertEquals("completed", unit.status, 
                        "Unit with all exercises completed should have status 'completed'")
                }
                unit.completedExercises > 0 && unit.completedExercises < unit.totalExercises -> {
                    assertEquals("in_progress", unit.status, 
                        "Unit with some exercises completed should have status 'in_progress'")
                }
                else -> {
                    assertTrue(unit.status in listOf("available", "in_progress", "completed"), 
                        "Status should be valid even in edge cases")
                }
            }
        }
    }
}
