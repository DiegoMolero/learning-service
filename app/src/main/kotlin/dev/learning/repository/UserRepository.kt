package dev.learning.repository

import dev.learning.DatabaseConfig
import dev.learning.UserSettingsResponse
import dev.learning.UnitProgress
import dev.learning.AnswerStatus
import dev.learning.content.ContentLibrary
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.*
import kotlinx.datetime.Clock

interface UserRepository {
    suspend fun getUserSettings(userId: String): UserSettingsResponse?
    suspend fun updateUserSettings(userId: String, nativeLanguage: String?, targetLanguage: String?, darkMode: Boolean?, onboardingStep: String?, userLevel: String?): Pair<Boolean, List<String>>
    suspend fun createDefaultUserSettings(userId: String): Boolean
    
    // User management methods for auth service
    suspend fun createUser(userId: String): Boolean
    suspend fun deleteUser(userId: String): Boolean
    
    // Progress tracking methods
    suspend fun recordExerciseProgress(userId: String, lang: String, moduleId: String, unitId: String, exerciseId: String, answerStatus: AnswerStatus, userAnswer: String): Boolean
    suspend fun getUnitProgress(userId: String, lang: String, moduleId: String, unitId: String): UnitProgress?
    suspend fun getExerciseResults(userId: String, lang: String, moduleId: String, unitId: String): Map<String, ExerciseResult>
}

data class ExerciseResult(
    val exerciseId: String,
    val lastStatus: AnswerStatus,
    val isCorrect: Boolean,
    val attemptCount: Int,
    val lastAttempted: String
)

class DatabaseUserRepository(
    private val databaseConfig: DatabaseConfig
) : UserRepository {
    
    private val dataSource: HikariDataSource
    private val contentLibrary = ContentLibrary()
    
    init {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = databaseConfig.url
            username = databaseConfig.user
            password = databaseConfig.password
            driverClassName = when {
                databaseConfig.url.contains("postgresql") -> "org.postgresql.Driver"
                databaseConfig.url.contains("h2") -> "org.h2.Driver"
                else -> throw IllegalArgumentException("Unsupported database type")
            }
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
        }
        dataSource = HikariDataSource(hikariConfig)
        
        Database.connect(dataSource)
        initializeDatabase()
    }

    private fun initializeDatabase() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                UserSettings,
                ExerciseProgressTable
            )
        }
    }

    override suspend fun getUserSettings(userId: String): UserSettingsResponse? {
        return transaction {
            UserSettings.select {
                UserSettings.userId eq userId
            }.singleOrNull()?.let { row ->
                val settingsJson = Json.parseToJsonElement(row[UserSettings.settings])
                Json.decodeFromJsonElement(UserSettingsResponse.serializer(), settingsJson)
            }
        }
    }

    override suspend fun updateUserSettings(
        userId: String,
        nativeLanguage: String?,
        targetLanguage: String?,
        darkMode: Boolean?,
        onboardingStep: String?,
        userLevel: String?
    ): Pair<Boolean, List<String>> {
        val warnings = mutableListOf<String>()
        
        // Get current settings first, outside of transaction
        val existingSettings = getUserSettings(userId)
        
        // Determine final languages with conflict resolution
        val currentNative = existingSettings?.nativeLanguage ?: "en"
        val currentTarget = existingSettings?.targetLanguage ?: "es"
        
        var finalNative = nativeLanguage ?: currentNative
        var finalTarget = targetLanguage ?: currentTarget
        
        // Check for language conflicts and auto-resolve
        if (finalNative == finalTarget) {
            when {
                // If user is updating native language and it conflicts with current target
                nativeLanguage != null && nativeLanguage == currentTarget -> {
                    finalTarget = if (finalNative == "es") "en" else "es"
                    warnings.add("Target language automatically changed to avoid conflict with native language")
                }
                // If user is updating target language and it conflicts with current native
                targetLanguage != null && targetLanguage == currentNative -> {
                    finalNative = if (finalTarget == "es") "en" else "es"
                    warnings.add("Native language automatically changed to avoid conflict with target language")
                }
                // Default case - if somehow they're the same, make target different
                else -> {
                    finalTarget = if (finalNative == "es") "en" else "es"
                    warnings.add("Languages automatically adjusted to avoid conflict")
                }
            }
        }
        
        // Validate target language change
        if (targetLanguage != null && currentTarget != finalTarget) {
            warnings.add("Changing target language will reset your progress")
        }
        
        // Validate native language change  
        if (nativeLanguage != null && currentNative != finalNative) {
            warnings.add("Changing native language may affect your learning experience")
        }
        
        // Validate onboarding completion - prevent completion without both languages set
        var finalOnboardingStep = onboardingStep ?: existingSettings?.onboardingStep ?: "native"
        
        // Validate onboarding step values
        val validOnboardingSteps = setOf("native", "learning", "level", "complete")
        if (onboardingStep != null && !validOnboardingSteps.contains(onboardingStep)) {
            throw IllegalArgumentException("Invalid onboarding step: $onboardingStep. Valid values are: ${validOnboardingSteps.joinToString(", ")}")
        }
        
        if (finalOnboardingStep == "complete") {
            // Check if both languages are explicitly set (not just defaults)
            val hasNativeSet = existingSettings?.nativeLanguage != null || nativeLanguage != null
            val hasTargetSet = existingSettings?.targetLanguage != null || targetLanguage != null
            
            if (!hasNativeSet || !hasTargetSet) {
                // Don't allow completion, keep current step or default to "native"
                finalOnboardingStep = existingSettings?.onboardingStep ?: "native"
                warnings.add("Cannot complete onboarding without setting both native and target languages")
            }
        }

        return transaction {
            // Create updated settings object using resolved languages and validated onboarding step
            val updatedSettings = UserSettingsResponse(
                userId = userId,
                nativeLanguage = finalNative,
                targetLanguage = finalTarget,
                darkMode = darkMode ?: existingSettings?.darkMode ?: false,
                onboardingStep = finalOnboardingStep,
                userLevel = userLevel ?: existingSettings?.userLevel
            )

            val settingsJson = Json.encodeToString(UserSettingsResponse.serializer(), updatedSettings)

            // Update or insert
            val existingRow = UserSettings.select { UserSettings.userId eq userId }.singleOrNull()
            if (existingRow == null) {
                UserSettings.insert {
                    it[this.userId] = userId
                    it[lang] = updatedSettings.targetLanguage
                    it[settings] = settingsJson
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
            } else {
                UserSettings.update({ UserSettings.userId eq userId }) {
                    it[lang] = updatedSettings.targetLanguage
                    it[settings] = settingsJson
                    it[updatedAt] = Clock.System.now()
                }
            }

            Pair(true, warnings)
        }
    }

    override suspend fun createDefaultUserSettings(userId: String): Boolean {
        return transaction {
            try {
                val defaultSettings = UserSettingsResponse(
                    userId = userId,
                    nativeLanguage = "en",
                    targetLanguage = "es",
                    darkMode = false,
                    onboardingStep = "native",
                    userLevel = null
                )

                val settingsJson = Json.encodeToString(UserSettingsResponse.serializer(), defaultSettings)

                UserSettings.insert {
                    it[this.userId] = userId
                    it[lang] = defaultSettings.targetLanguage
                    it[settings] = settingsJson
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
                true
            } catch (e: Exception) {
                // Check if it's a constraint violation (duplicate user)
                if (e.message?.contains("constraint", ignoreCase = true) == true || 
                    e.message?.contains("unique", ignoreCase = true) == true ||
                    e.message?.contains("duplicate", ignoreCase = true) == true) {
                    throw IllegalStateException("User with ID $userId already exists")
                }
                // For other exceptions, return false
                false
            }
        }
    }

    override suspend fun createUser(userId: String): Boolean {
        // Simply call createDefaultUserSettings which already does what we need
        return createDefaultUserSettings(userId)
    }

    override suspend fun deleteUser(userId: String): Boolean {
        return transaction {
            try {
                UserSettings.deleteWhere { UserSettings.userId eq userId }
                ExerciseProgressTable.deleteWhere { ExerciseProgressTable.userId eq userId }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun recordExerciseProgress(
        userId: String,
        lang: String,
        moduleId: String,
        unitId: String,
        exerciseId: String,
        answerStatus: AnswerStatus,
        userAnswer: String
    ): Boolean {
        return transaction {
            try {
                // Simply insert the exercise result
                ExerciseProgressTable.insert {
                    it[this.userId] = userId
                    it[this.lang] = lang
                    it[this.moduleId] = moduleId
                    it[this.unitId] = unitId
                    it[this.exerciseId] = exerciseId
                    it[this.userAnswer] = userAnswer
                    it[this.answerStatus] = answerStatus.name
                    it[this.attemptedAt] = Clock.System.now()
                }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun getUnitProgress(userId: String, lang: String, moduleId: String, unitId: String): UnitProgress? {
        return transaction {
            // Get all exercise results for this unit
            val exerciseResults = ExerciseProgressTable.select { 
                (ExerciseProgressTable.userId eq userId) and 
                (ExerciseProgressTable.lang eq lang) and
                (ExerciseProgressTable.moduleId eq moduleId) and 
                (ExerciseProgressTable.unitId eq unitId)
            }.orderBy(ExerciseProgressTable.attemptedAt, SortOrder.DESC)

            // If no results, check if unit exists in content
            if (exerciseResults.empty()) {
                val unitContent = contentLibrary.getUnitContent(lang, moduleId, unitId)
                return@transaction if (unitContent != null) {
                    // Unit exists but no progress yet
                    UnitProgress(
                        unitId = unitId,
                        completedExercises = 0,
                        correctAnswers = 0,
                        wrongAnswers = 0,
                        totalExercises = unitContent.exercises.size,
                        lastAttempted = null
                    )
                } else {
                    null // Unit doesn't exist and no data
                }
            }

            // Group by exerciseId to get the latest attempt for each exercise
            val latestAttempts = mutableMapOf<String, ResultRow>()
            exerciseResults.forEach { result ->
                val exerciseId = result[ExerciseProgressTable.exerciseId]
                if (!latestAttempts.containsKey(exerciseId)) {
                    latestAttempts[exerciseId] = result
                }
            }

            // Calculate statistics
            var correctAnswers = 0
            var wrongAnswers = 0
            var lastAttemptedTime: String? = null

            latestAttempts.values.forEach { result ->
                val status = result[ExerciseProgressTable.answerStatus]
                when (status) {
                    "CORRECT" -> correctAnswers++
                    "INCORRECT", "SKIPPED", "REVEALED" -> wrongAnswers++
                }
                
                // Track the most recent attempt time
                val attemptTime = result[ExerciseProgressTable.attemptedAt].toString()
                if (lastAttemptedTime == null || attemptTime > lastAttemptedTime!!) {
                    lastAttemptedTime = attemptTime
                }
            }

            val completedExercises = correctAnswers // Only correct answers count as completed

            // Get total exercises from content, or use number of unique exercises attempted for tests
            val unitContent = contentLibrary.getUnitContent(lang, moduleId, unitId)
            val totalExercises = unitContent?.exercises?.size ?: latestAttempts.size

            UnitProgress(
                unitId = unitId,
                completedExercises = completedExercises,
                correctAnswers = correctAnswers,
                wrongAnswers = wrongAnswers,
                totalExercises = totalExercises,
                lastAttempted = lastAttemptedTime
            )
        }
    }

    override suspend fun getExerciseResults(userId: String, lang: String, moduleId: String, unitId: String): Map<String, ExerciseResult> {
        return transaction {
            val exerciseResults = ExerciseProgressTable.select { 
                (ExerciseProgressTable.userId eq userId) and 
                (ExerciseProgressTable.lang eq lang) and
                (ExerciseProgressTable.moduleId eq moduleId) and 
                (ExerciseProgressTable.unitId eq unitId)
            }.orderBy(ExerciseProgressTable.attemptedAt, SortOrder.DESC)

            val exerciseMap = mutableMapOf<String, MutableList<ResultRow>>()
            
            // Group by exerciseId
            exerciseResults.forEach { result ->
                val exerciseId = result[ExerciseProgressTable.exerciseId]
                exerciseMap.computeIfAbsent(exerciseId) { mutableListOf() }.add(result)
            }

            // Convert to ExerciseResult objects
            exerciseMap.mapValues { (exerciseId, attempts) ->
                val latestAttempt = attempts.first() // Already ordered by attemptedAt DESC
                val isCorrect = latestAttempt[ExerciseProgressTable.answerStatus] == "CORRECT"
                
                ExerciseResult(
                    exerciseId = exerciseId,
                    lastStatus = AnswerStatus.valueOf(latestAttempt[ExerciseProgressTable.answerStatus]),
                    isCorrect = isCorrect,
                    attemptCount = attempts.size,
                    lastAttempted = latestAttempt[ExerciseProgressTable.attemptedAt].toString()
                )
            }
        }
    }
}