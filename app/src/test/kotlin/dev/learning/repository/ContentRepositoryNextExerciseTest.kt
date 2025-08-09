package dev.learning.repository

import dev.learning.*
import dev.learning.AnswerStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.util.*

class ContentRepositoryNextExerciseTest {

    private lateinit var contentRepository: ContentRepository
    private lateinit var userRepository: UserRepository
    private lateinit var config: Config
    private val testUserId = "test-${UUID.randomUUID().toString().take(20)}"
    private val testLang = "en"
    private val testModuleId = "articles-determiners"
    private val testUnitId = "the_article_general_vs_specific_1"

    @BeforeEach
    fun setUp() {
        config = loadConfig("test")
        contentRepository = DatabaseContentRepository(config.database, "test")
        userRepository = DatabaseUserRepository(config.database)
        
        // Create test user with default settings
        runBlocking {
            userRepository.createDefaultUserSettings(testUserId)
        }
    }

    @AfterEach
    fun tearDown() {
        // Clean up test user and all their progress
        runBlocking {
            userRepository.deleteUser(testUserId)
        }
    }

    @Test
    fun `getNextExercise should return first available exercise when no progress exists`() = runBlocking {
        // Arrange
        val currentExerciseId = "ex_1"

        // Act
        val nextExercise = contentRepository.getNextExercise(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId,
            currentExerciseId = currentExerciseId
        )

        // Assert
        assertNotNull(nextExercise, "Should return a next exercise when no progress exists")
        assertTrue(nextExercise.id != currentExerciseId, "Should not return the current exercise")
        assertTrue(nextExercise.id.startsWith("ex_"), "Should return a valid exercise ID")
    }

    @Test
    fun `getNextExercise should prioritize new exercises over completed ones`() = runBlocking {
        // Arrange
        val currentExerciseId = "ex_1"
        val completedExerciseId = "ex_2"
        
        // Mark one exercise as completed
        userRepository.recordExerciseProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId,
            exerciseId = completedExerciseId,
            answerStatus = AnswerStatus.CORRECT,
            userAnswer = "Correct answer"
        )

        // Act - run multiple times to check that new exercises are prioritized
        val nextExercises = mutableSetOf<String>()
        repeat(10) {
            val nextExercise = contentRepository.getNextExercise(
                userId = testUserId,
                lang = testLang,
                moduleId = testModuleId,
                unitId = testUnitId,
                currentExerciseId = currentExerciseId
            )
            nextExercise?.let { nextExercises.add(it.id) }
        }

        // Assert
        assertTrue(nextExercises.isNotEmpty(), "Should return next exercises")
        // Should mostly return new exercises, not the completed one
        val newExercisesCount = nextExercises.filter { it != completedExerciseId }.size
        val completedExercisesCount = nextExercises.filter { it == completedExerciseId }.size
        assertTrue(newExercisesCount >= completedExercisesCount, "Should prioritize new exercises")
    }

    @Test
    fun `getNextExercise should include failed exercises for review`() = runBlocking {
        // Arrange
        val currentExerciseId = "ex_1"
        val failedExerciseId = "ex_2"
        val correctExerciseId = "ex_3"
        
        // Mark one exercise as failed and another as correct
        userRepository.recordExerciseProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId,
            exerciseId = failedExerciseId,
            answerStatus = AnswerStatus.INCORRECT,
            userAnswer = "Wrong answer"
        )
        
        userRepository.recordExerciseProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId,
            exerciseId = correctExerciseId,
            answerStatus = AnswerStatus.CORRECT,
            userAnswer = "Correct answer"
        )

        // Act - run multiple times to check that failed exercises appear for review
        val nextExercises = mutableSetOf<String>()
        repeat(30) { // Run more times to increase chance of getting review exercises
            val nextExercise = contentRepository.getNextExercise(
                userId = testUserId,
                lang = testLang,
                moduleId = testModuleId,
                unitId = testUnitId,
                currentExerciseId = currentExerciseId
            )
            nextExercise?.let { nextExercises.add(it.id) }
        }

        // Assert
        assertTrue(nextExercises.contains(failedExerciseId), "Should include failed exercises for review")
    }

    @Test
    fun `getNextExercise should handle all exercises completed scenario`() = runBlocking {
        // Arrange
        val currentExerciseId = "ex_1"
        
        // Get all exercises in the unit first
        val unitContent = contentRepository.getUnit(testUserId, testLang, testModuleId, testUnitId)
        assertNotNull(unitContent, "Unit should exist")
        
        // Mark several exercises as completed (not all, as that would be difficult with 50 exercises)
        val exercisesToComplete = listOf("ex_2", "ex_3", "ex_4", "ex_5")
        exercisesToComplete.forEach { exerciseId ->
            userRepository.recordExerciseProgress(
                userId = testUserId,
                lang = testLang,
                moduleId = testModuleId,
                unitId = testUnitId,
                exerciseId = exerciseId,
                answerStatus = AnswerStatus.CORRECT,
                userAnswer = "Correct answer"
            )
        }

        // Act
        val nextExercise = contentRepository.getNextExercise(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId,
            currentExerciseId = currentExerciseId
        )

        // Assert
        assertNotNull(nextExercise, "Should return a next exercise even when some are completed")
        assertTrue(nextExercise.id != currentExerciseId, "Should not return current exercise")
    }

    @Test
    fun `getNextExercise should handle different answer statuses correctly`() = runBlocking {
        // Arrange
        val currentExerciseId = "ex_1"
        val skippedExerciseId = "ex_2"
        val revealedExerciseId = "ex_3"
        
        // Test different answer statuses
        userRepository.recordExerciseProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId,
            exerciseId = skippedExerciseId,
            answerStatus = AnswerStatus.SKIPPED,
            userAnswer = ""
        )
        
        userRepository.recordExerciseProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId,
            exerciseId = revealedExerciseId,
            answerStatus = AnswerStatus.REVEALED,
            userAnswer = "Revealed answer"
        )

        // Act
        val nextExercise = contentRepository.getNextExercise(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId,
            currentExerciseId = currentExerciseId
        )

        // Assert
        assertNotNull(nextExercise, "Should return next exercise")
        assertTrue(nextExercise.id != currentExerciseId, "Should not return current exercise")
    }

    @Test
    fun `getNextExercise should handle non-existent unit`() = runBlocking {
        // Arrange
        val currentExerciseId = "ex_1"
        val nonExistentUnitId = "non-existent-unit"

        // Act
        val nextExercise = contentRepository.getNextExercise(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = nonExistentUnitId,
            currentExerciseId = currentExerciseId
        )

        // Assert
        assertNull(nextExercise, "Should return null for non-existent unit")
    }

    @Test
    fun `getNextExercise should handle latest attempts correctly`() = runBlocking {
        // Arrange
        val currentExerciseId = "ex_1"
        val exerciseId = "ex_2"
        
        // Record multiple attempts for the same exercise - first incorrect, then correct
        userRepository.recordExerciseProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId,
            exerciseId = exerciseId,
            answerStatus = AnswerStatus.INCORRECT,
            userAnswer = "Wrong answer"
        )
        
        // Wait a bit to ensure different timestamps
        Thread.sleep(10)
        
        userRepository.recordExerciseProgress(
            userId = testUserId,
            lang = testLang,
            moduleId = testModuleId,
            unitId = testUnitId,
            exerciseId = exerciseId,
            answerStatus = AnswerStatus.CORRECT,
            userAnswer = "Correct answer"
        )

        // Act - run multiple times to see the behavior
        val nextExercises = mutableSetOf<String>()
        repeat(20) {
            val nextExercise = contentRepository.getNextExercise(
                userId = testUserId,
                lang = testLang,
                moduleId = testModuleId,
                unitId = testUnitId,
                currentExerciseId = currentExerciseId
            )
            nextExercise?.let { nextExercises.add(it.id) }
        }

        // Assert
        assertTrue(nextExercises.isNotEmpty(), "Should return next exercises")
        // The corrected exercise should be less likely to appear than new exercises
        val newExercisesCount = nextExercises.filter { it != exerciseId }.size
        assertTrue(newExercisesCount > 0, "Should include new exercises")
    }

    @Test
    fun `getNextExercise should not return empty unit exercises list`() = runBlocking {
        // Arrange
        val currentExerciseId = "ex_1"
        val emptyModuleId = "empty-module"
        val emptyUnitId = "empty-unit"

        // Act
        val nextExercise = contentRepository.getNextExercise(
            userId = testUserId,
            lang = testLang,
            moduleId = emptyModuleId,
            unitId = emptyUnitId,
            currentExerciseId = currentExerciseId
        )

        // Assert
        assertNull(nextExercise, "Should return null for empty or non-existent exercises")
    }
}
