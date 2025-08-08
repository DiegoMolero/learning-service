package dev.learning.repository

import dev.learning.*
import dev.learning.AnswerStatus
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.util.*

class UserRepositoryProgressTest {

    private lateinit var repository: UserRepository
    private lateinit var config: Config
    private val testUserId = "test-${UUID.randomUUID().toString().take(20)}"
    private val testLang = "es"
    private val testModuleId = "module-1"
    private val testUnitId = "unit-1"
    private val testExerciseId = "exercise-1"

    @BeforeEach
    fun setUp() {
        config = loadConfig("test")
        repository = DatabaseUserRepository(config.database)
        
        // Create test user with default settings
        runBlocking {
            repository.createDefaultUserSettings(testUserId)
        }
    }

    @AfterEach
    fun tearDown() {
        // Clean up test user
        runBlocking {
            repository.deleteUser(testUserId)
        }
    }

    @Test
    fun `recordExerciseProgress should create new progress record successfully`() = runBlocking {
        // Arrange
        val userAnswer = "Mi respuesta"
        val answerStatus = AnswerStatus.CORRECT

        // Act
        val result = repository.recordExerciseProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId,
            exerciseId = testExerciseId,
            answerStatus = answerStatus,
            userAnswer = userAnswer
        )

        // Assert
        assertTrue(result, "recordExerciseProgress should return true for successful recording")
    }

    @Test
    fun `getUnitProgress should return null when no progress exists`() = runBlocking {
        // Act
        val progress = repository.getUnitProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId
        )

        // Assert
        assertNull(progress, "getUnitProgress should return null when no progress exists")
    }

    @Test
    fun `recordExerciseProgress and getUnitProgress should work together - single correct answer`() = runBlocking {
        // Arrange
        val userAnswer = "Correct answer"
        val answerStatus = AnswerStatus.CORRECT

        // Act - Record progress
        val recordResult = repository.recordExerciseProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId,
            exerciseId = testExerciseId,
            answerStatus = answerStatus,
            userAnswer = userAnswer
        )

        // Get progress
        val progress = repository.getUnitProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId
        )

        // Assert
        assertTrue(recordResult, "recordExerciseProgress should succeed")
        assertNotNull(progress, "getUnitProgress should return progress after recording")
        assertEquals(testUnitId, progress.unitId)
        assertEquals(1, progress.completedExercises)
        assertEquals(1, progress.correctAnswers)
        assertEquals(0, progress.wrongAnswers)
        assertEquals(50, progress.totalExercises) // Default value from implementation
        assertNotNull(progress.lastAttempted)
    }

    @Test
    fun `recordExerciseProgress and getUnitProgress should work together - single incorrect answer`() = runBlocking {
        // Arrange
        val userAnswer = "Wrong answer"
        val answerStatus = AnswerStatus.INCORRECT

        // Act - Record progress
        val recordResult = repository.recordExerciseProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId,
            exerciseId = testExerciseId,
            answerStatus = answerStatus,
            userAnswer = userAnswer
        )

        // Get progress
        val progress = repository.getUnitProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId
        )

        // Assert
        assertTrue(recordResult, "recordExerciseProgress should succeed")
        assertNotNull(progress, "getUnitProgress should return progress after recording")
        assertEquals(testUnitId, progress.unitId)
        assertEquals(0, progress.completedExercises) // Only correct answers count as completed
        assertEquals(0, progress.correctAnswers)
        assertEquals(1, progress.wrongAnswers)
        assertEquals(50, progress.totalExercises)
        assertNotNull(progress.lastAttempted)
    }

    @Test
    fun `recordExerciseProgress and getUnitProgress should handle multiple exercises`() = runBlocking {
        // Arrange - Multiple exercises with different statuses
        val exercises = listOf(
            Triple("exercise-1", "Correct answer 1", AnswerStatus.CORRECT),
            Triple("exercise-2", "Wrong answer", AnswerStatus.INCORRECT),
            Triple("exercise-3", "Correct answer 2", AnswerStatus.CORRECT),
            Triple("exercise-4", "Skipped", AnswerStatus.SKIPPED),
            Triple("exercise-5", "Revealed", AnswerStatus.REVEALED)
        )

        // Act - Record progress for multiple exercises
        exercises.forEach { (exerciseId, userAnswer, status) ->
            val result = repository.recordExerciseProgress(
                userId = testUserId,
                lang = testLang,
                moduleId = testModuleId,
                unitId = testUnitId,
                exerciseId = exerciseId,
                answerStatus = status,
                userAnswer = userAnswer
            )
            assertTrue(result, "recordExerciseProgress should succeed for exercise $exerciseId")
        }

        // Get progress
        val progress = repository.getUnitProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId
        )

        // Assert
        assertNotNull(progress, "getUnitProgress should return progress after recording multiple exercises")
        assertEquals(testUnitId, progress.unitId)
        assertEquals(2, progress.completedExercises) // Only correct answers count as completed
        assertEquals(2, progress.correctAnswers) // 2 correct answers
        assertEquals(3, progress.wrongAnswers) // 1 incorrect + 1 skipped + 1 revealed count as wrong
        assertEquals(50, progress.totalExercises)
        assertNotNull(progress.lastAttempted)
    }

    @Test
    fun `recordExerciseProgress should handle same exercise multiple attempts`() = runBlocking {
        // Arrange - Same exercise attempted multiple times
        val sameExerciseId = "exercise-retry"
        val attempts = listOf(
            Pair("First wrong attempt", AnswerStatus.INCORRECT),
            Pair("Second wrong attempt", AnswerStatus.INCORRECT),
            Pair("Finally correct", AnswerStatus.CORRECT)
        )

        // Act - Record multiple attempts for same exercise
        attempts.forEach { (userAnswer, status) ->
            val result = repository.recordExerciseProgress(
                userId = testUserId,
                lang = testLang,
                moduleId = testModuleId,
                unitId = testUnitId,
                exerciseId = sameExerciseId,
                answerStatus = status,
                userAnswer = userAnswer
            )
            assertTrue(result, "recordExerciseProgress should succeed for attempt: $userAnswer")
        }

        // Get progress
        val progress = repository.getUnitProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId
        )

        // Assert
        assertNotNull(progress, "getUnitProgress should return progress after multiple attempts")
        assertEquals(testUnitId, progress.unitId)
        assertEquals(1, progress.completedExercises) // Only 1 correct answer (the last attempt)
        assertEquals(1, progress.correctAnswers) // 1 correct answer
        assertEquals(2, progress.wrongAnswers) // 2 incorrect attempts
        assertEquals(50, progress.totalExercises)
        assertNotNull(progress.lastAttempted)
    }

    @Test
    fun `recordExerciseProgress should work across different units for same user`() = runBlocking {
        // Arrange - Different units
        val unit1 = "unit-1"
        val unit2 = "unit-2"

        // Act - Record progress in different units
        val result1 = repository.recordExerciseProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = unit1,
            exerciseId = "exercise-1",
            answerStatus = AnswerStatus.CORRECT,
            userAnswer = "Answer for unit 1"
        )

        val result2 = repository.recordExerciseProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = unit2,
            exerciseId = "exercise-1",
            answerStatus = AnswerStatus.INCORRECT,
            userAnswer = "Answer for unit 2"
        )

        // Get progress for both units
        val progress1 = repository.getUnitProgress(testUserId, testLang, testModuleId, unit1)
        val progress2 = repository.getUnitProgress(testUserId, testLang, testModuleId, unit2)

        // Assert
        assertTrue(result1, "recordExerciseProgress should succeed for unit 1")
        assertTrue(result2, "recordExerciseProgress should succeed for unit 2")

        assertNotNull(progress1, "Should have progress for unit 1")
        assertEquals(unit1, progress1.unitId)
        assertEquals(1, progress1.correctAnswers)
        assertEquals(0, progress1.wrongAnswers)

        assertNotNull(progress2, "Should have progress for unit 2")
        assertEquals(unit2, progress2.unitId)
        assertEquals(0, progress2.correctAnswers)
        assertEquals(1, progress2.wrongAnswers)
    }

    @Test
    fun `recordExerciseProgress should handle different answer statuses correctly`() = runBlocking {
        // Test each status individually to verify they're handled correctly
        val statuses = listOf(
            AnswerStatus.CORRECT,
            AnswerStatus.INCORRECT,
            AnswerStatus.SKIPPED,
            AnswerStatus.REVEALED
        )

        statuses.forEachIndexed { index, status ->
            val exerciseId = "exercise-status-$index"
            
            // Act
            val result = repository.recordExerciseProgress(
                userId = testUserId,
                lang = testLang,
                moduleId = testModuleId,
                unitId = testUnitId,
                exerciseId = exerciseId,
                answerStatus = status,
                userAnswer = "Answer for $status"
            )

            // Assert
            assertTrue(result, "recordExerciseProgress should succeed for status $status")
        }

        // Get final progress
        val progress = repository.getUnitProgress(testUserId, testLang, testModuleId, testUnitId)

        // Assert final counts
        assertNotNull(progress)
        assertEquals(1, progress.correctAnswers) // Only 1 CORRECT
        assertEquals(3, progress.wrongAnswers) // INCORRECT, SKIPPED, REVEALED count as wrong
        assertEquals(1, progress.completedExercises) // Only correct answers count as completed
    }
}
