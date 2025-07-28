package dev.learning.repository

import dev.learning.DatabaseConfig
import dev.learning.Level
import dev.learning.UserProgressResponse
import dev.learning.UserSettingsResponse
import dev.learning.LevelOverviewResponse
import dev.learning.LevelTopicsResponse
import dev.learning.ExerciseResponse
import dev.learning.LevelSummary
import dev.learning.LevelProgress
import dev.learning.TopicSummary
import dev.learning.TopicProgress
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

interface LearningRepository {
    // New dashboard methods
    suspend fun getLevelOverview(userId: String, targetLanguage: String): LevelOverviewResponse
    suspend fun getLevelTopics(userId: String, targetLanguage: String, level: String): LevelTopicsResponse
    suspend fun getExercise(userId: String, targetLanguage: String, level: String, topicId: String, exerciseId: String): ExerciseResponse?
    suspend fun getNextExercise(userId: String, targetLanguage: String, level: String, topicId: String): ExerciseResponse?
    
    suspend fun getUserProgress(userId: String): List<UserProgressResponse>
    suspend fun getUserProgressForLevel(userId: String, levelId: String): UserProgressResponse?
    suspend fun updateUserProgress(userId: String, levelId: String, completedPhraseIds: List<String>): Boolean
    suspend fun getUserSettings(userId: String): UserSettingsResponse?
    suspend fun updateUserSettingsWithWarnings(userId: String, nativeLanguage: String?, targetLanguage: String?, darkMode: Boolean?, onboardingStep: String?): Pair<Boolean, List<String>>
    suspend fun createDefaultUserSettings(userId: String): Boolean
    
    // User management methods for auth service
    suspend fun createUser(userId: String): Boolean
    suspend fun deleteUser(userId: String): Boolean
    suspend fun deleteUserProgress(userId: String): Boolean
}

// Database tables
object UserProgress : UUIDTable("user_progress") {
    val userId = uuid("user_id")
    val levelId = varchar("level_id", 100)
    val completedPhraseIds = text("completed_phrase_ids") // JSON array
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
    
    init {
        uniqueIndex(userId, levelId)
    }
}

// New table for detailed topic progress
object TopicProgressTable : UUIDTable("topic_progress") {
    val userId = uuid("user_id")
    val topicId = varchar("topic_id", 100) // e.g., "en-A1-basic_greetings"
    val completedExercises = integer("completed_exercises").default(0)
    val correctAnswers = integer("correct_answers").default(0)
    val wrongAnswers = integer("wrong_answers").default(0)
    val lastAttempted = timestamp("last_attempted").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
    
    init {
        uniqueIndex(userId, topicId)
    }
}

object UserSettings : UUIDTable("user_settings") {
    val userId = uuid("user_id").uniqueIndex()
    val nativeLanguage = varchar("native_language", 10).default("en")
    val targetLanguage = varchar("target_language", 10).default("es")
    val darkMode = bool("dark_mode").default(false)
    val onboardingStep = varchar("onboarding_step", 20).default("native")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
}

class DatabaseLearningRepository(private val databaseConfig: DatabaseConfig) : LearningRepository {
    
    private val dataSource: HikariDataSource
    private val json = Json { ignoreUnknownKeys = true }
    
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
            if (databaseConfig.dropOnStart) {
                SchemaUtils.drop(UserProgress, UserSettings, TopicProgressTable)
            }
            SchemaUtils.create(UserProgress, UserSettings, TopicProgressTable)
        }
    }

    private suspend fun getLevel(levelId: String): Level? {
        return try {
            // First try the old structure for backward compatibility
            val oldResource = {}::class.java.getResource("/levels/$levelId.json")
            if (oldResource != null) {
                val content = oldResource.readText()
                val level = json.decodeFromString<Level>(content)
                return level.copy(id = levelId)
            }
            
            // If not found in old structure, try to find in new structure
            // This requires parsing the levelId to extract language, level, and file
            // Format expected: "en-A1-basic_greetings" or similar
            val parts = levelId.split("-")
            if (parts.size >= 3) {
                val targetLanguage = parts[0]
                val level = parts[1]
                val fileName = parts.drop(2).joinToString("-")
                
                val newResource = {}::class.java.getResource("/levels/$targetLanguage/$level/$fileName.json")
                if (newResource != null) {
                    val content = newResource.readText()
                    val levelData = json.decodeFromString<Level>(content)
                    return levelData.copy(id = levelId)
                }
            }
            
            null
        } catch (e: Exception) {
            println("Error loading level $levelId: ${e.message}")
            null
        }
    }

    private suspend fun getLevelsByLanguageAndLevel(targetLanguage: String, level: String): List<Level> {
        return try {
            val levels = mutableListOf<Level>()
            val resource = {}::class.java.getResource("/levels/$targetLanguage/$level")
            
            if (resource != null) {
                val directory = java.io.File(resource.toURI())
                if (directory.exists() && directory.isDirectory) {
                    directory.listFiles { file -> file.name.endsWith(".json") }?.forEach { file ->
                        try {
                            val content = file.readText()
                            val levelData = json.decodeFromString<Level>(content)
                            val levelId = "$targetLanguage-$level-${file.nameWithoutExtension}"
                            levels.add(levelData.copy(id = levelId))
                        } catch (e: Exception) {
                            println("Error loading level from ${file.name}: ${e.message}")
                        }
                    }
                }
            }
            
            levels
        } catch (e: Exception) {
            println("Error loading levels for $targetLanguage/$level: ${e.message}")
            emptyList()
        }
    }

    private suspend fun getAllAvailableLevels(): Map<String, Map<String, List<String>>> {
        return try {
            val result = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
            val levelsResource = {}::class.java.getResource("/levels")
            
            if (levelsResource != null) {
                val levelsDir = java.io.File(levelsResource.toURI())
                if (levelsDir.exists() && levelsDir.isDirectory) {
                    levelsDir.listFiles { file -> file.isDirectory }?.forEach { languageDir ->
                        val language = languageDir.name
                        result[language] = mutableMapOf()
                        
                        languageDir.listFiles { file -> file.isDirectory }?.forEach { levelDir ->
                            val level = levelDir.name
                            result[language]!![level] = mutableListOf()
                            
                            levelDir.listFiles { file -> file.name.endsWith(".json") }?.forEach { jsonFile ->
                                result[language]!![level]!!.add(jsonFile.name)
                            }
                        }
                    }
                }
            }
            
            result
        } catch (e: Exception) {
            println("Error scanning available levels: ${e.message}")
            emptyMap()
        }
    }

    override suspend fun getUserProgress(userId: String): List<UserProgressResponse> {
        return try {
            transaction {
                UserProgress.select { UserProgress.userId eq UUID.fromString(userId) }
                    .map { row ->
                        val completedIds = try {
                            json.decodeFromString<List<String>>(row[UserProgress.completedPhraseIds])
                        } catch (e: Exception) {
                            emptyList()
                        }
                        
                        UserProgressResponse(
                            userId = userId,
                            levelId = row[UserProgress.levelId],
                            completedPhraseIds = completedIds
                        )
                    }
            }
        } catch (e: Exception) {
            println("Error getting user progress: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getUserProgressForLevel(userId: String, levelId: String): UserProgressResponse? {
        return try {
            transaction {
                UserProgress.select { 
                    (UserProgress.userId eq UUID.fromString(userId)) and 
                    (UserProgress.levelId eq levelId) 
                }
                .singleOrNull()
                ?.let { row ->
                    val completedIds = try {
                        json.decodeFromString<List<String>>(row[UserProgress.completedPhraseIds])
                    } catch (e: Exception) {
                        emptyList()
                    }
                    
                    UserProgressResponse(
                        userId = userId,
                        levelId = levelId,
                        completedPhraseIds = completedIds
                    )
                }
            }
        } catch (e: Exception) {
            println("Error getting user progress for level: ${e.message}")
            null
        }
    }

    override suspend fun updateUserProgress(userId: String, levelId: String, completedPhraseIds: List<String>): Boolean {
        return try {
            transaction {
                val userUuid = UUID.fromString(userId)
                val completedIdsJson = completedPhraseIds.joinToString(",", "[", "]") { "\"$it\"" }
                
                val existing = UserProgress.select { 
                    (UserProgress.userId eq userUuid) and 
                    (UserProgress.levelId eq levelId) 
                }.singleOrNull()
                
                if (existing != null) {
                    UserProgress.update({ 
                        (UserProgress.userId eq userUuid) and 
                        (UserProgress.levelId eq levelId) 
                    }) {
                        it[UserProgress.completedPhraseIds] = completedIdsJson
                        it[UserProgress.updatedAt] = kotlinx.datetime.Clock.System.now()
                    } > 0
                } else {
                    UserProgress.insert {
                        it[UserProgress.userId] = userUuid
                        it[UserProgress.levelId] = levelId
                        it[UserProgress.completedPhraseIds] = completedIdsJson
                    }.insertedCount > 0
                }
            }
        } catch (e: Exception) {
            println("Error updating user progress: ${e.message}")
            false
        }
    }

    override suspend fun getUserSettings(userId: String): UserSettingsResponse? {
        return try {
            transaction {
                UserSettings.select { UserSettings.userId eq UUID.fromString(userId) }
                    .singleOrNull()
                    ?.let { row ->
                        UserSettingsResponse(
                            userId = userId,
                            nativeLanguage = row[UserSettings.nativeLanguage],
                            targetLanguage = row[UserSettings.targetLanguage],
                            darkMode = row[UserSettings.darkMode],
                            onboardingStep = row[UserSettings.onboardingStep]
                        )
                    }
            }
        } catch (e: Exception) {
            println("Error getting user settings: ${e.message}")
            null
        }
    }

    override suspend fun updateUserSettingsWithWarnings(userId: String, nativeLanguage: String?, targetLanguage: String?, darkMode: Boolean?, onboardingStep: String?): Pair<Boolean, List<String>> {
        val warnings = mutableListOf<String>()
        
        return try {
            transaction {
                val userUuid = UUID.fromString(userId)
                val existing = UserSettings.select { UserSettings.userId eq userUuid }.singleOrNull()
                
                // Validate that nativeLanguage and targetLanguage are different if both are provided
                if (nativeLanguage != null && targetLanguage != null && nativeLanguage == targetLanguage) {
                    throw IllegalArgumentException("Native language and target language cannot be the same")
                }
                
                // Auto-switch logic: if only one language is provided and it conflicts with the existing one,
                // automatically switch the other language
                var finalNativeLanguage = nativeLanguage
                var finalTargetLanguage = targetLanguage
                
                if (existing != null) {
                    val currentNative = existing[UserSettings.nativeLanguage]
                    val currentTarget = existing[UserSettings.targetLanguage]
                    
                    // If only native language is provided and it conflicts with current target
                    if (nativeLanguage != null && targetLanguage == null && nativeLanguage == currentTarget) {
                        // Auto-switch target language
                        finalTargetLanguage = if (nativeLanguage == "en") "es" else "en"
                        warnings.add("Target language automatically changed to avoid conflict with native language")
                    }
                    
                    // If only target language is provided and it conflicts with current native
                    if (targetLanguage != null && nativeLanguage == null && targetLanguage == currentNative) {
                        // Auto-switch native language
                        finalNativeLanguage = if (targetLanguage == "en") "es" else "en"
                        warnings.add("Native language automatically changed to avoid conflict with target language")
                    }
                }
                
                // Validate supported languages
                val supportedLanguages = listOf("en", "es")
                finalNativeLanguage?.let { 
                    if (it !in supportedLanguages) {
                        throw IllegalArgumentException("Unsupported native language: $it. Supported: ${supportedLanguages.joinToString()}")
                    }
                }
                finalTargetLanguage?.let { 
                    if (it !in supportedLanguages) {
                        throw IllegalArgumentException("Unsupported target language: $it. Supported: ${supportedLanguages.joinToString()}")
                    }
                }
                
                // Validate onboarding phase transitions
                val validPhases = listOf("native", "learning", "complete")
                onboardingStep?.let { phase ->
                    if (phase !in validPhases) {
                        throw IllegalArgumentException("Invalid onboarding phase: $phase. Valid phases: ${validPhases.joinToString()}")
                    }
                }
                
                // Validate that onboarding can only progress to complete if both languages are set
                var finalOnboardingStep = onboardingStep
                if (onboardingStep == "complete") {
                    val finalNative = finalNativeLanguage ?: (existing?.get(UserSettings.nativeLanguage))
                    val finalTarget = finalTargetLanguage ?: (existing?.get(UserSettings.targetLanguage))
                    
                    if (finalNative.isNullOrBlank() || finalTarget.isNullOrBlank()) {
                        // Don't allow completion if languages are not set
                        finalOnboardingStep = null
                        warnings.add("Onboarding completion ignored - both native and target languages must be selected first")
                    }
                }
                
                val success = if (existing != null) {
                    UserSettings.update({ UserSettings.userId eq userUuid }) {
                        finalNativeLanguage?.let { lang -> it[UserSettings.nativeLanguage] = lang }
                        finalTargetLanguage?.let { lang -> it[UserSettings.targetLanguage] = lang }
                        darkMode?.let { mode -> it[UserSettings.darkMode] = mode }
                        finalOnboardingStep?.let { phase -> it[UserSettings.onboardingStep] = phase }
                        it[UserSettings.updatedAt] = kotlinx.datetime.Clock.System.now()
                    } > 0
                } else {
                    // Create default settings with provided updates
                    val finalNative = finalNativeLanguage ?: "en"
                    val finalTarget = finalTargetLanguage ?: if (finalNative == "es") "en" else "es"
                    
                    // Final validation for new records
                    if (finalNative == finalTarget) {
                        throw IllegalArgumentException("Native language and target language cannot be the same")
                    }
                    
                    // For new records, set default onboarding phase or validate provided phase
                    var newUserOnboardingStep = finalOnboardingStep ?: "native"
                    if (onboardingStep == "complete" && (finalNative.isBlank() || finalTarget.isBlank())) {
                        newUserOnboardingStep = "native"
                        warnings.add("Onboarding phase set to 'native' for new user - both languages must be selected first")
                    }
                    
                    UserSettings.insert {
                        it[UserSettings.userId] = userUuid
                        it[UserSettings.nativeLanguage] = finalNative
                        it[UserSettings.targetLanguage] = finalTarget
                        it[UserSettings.darkMode] = darkMode ?: false
                        it[UserSettings.onboardingStep] = newUserOnboardingStep
                    }.insertedCount > 0
                }
                
                Pair(success, warnings)
            }
        } catch (e: IllegalArgumentException) {
            println("Validation error updating user settings: ${e.message}")
            Pair(false, listOf(e.message ?: "Validation error"))
        } catch (e: Exception) {
            println("Error updating user settings: ${e.message}")
            Pair(false, listOf("Internal error updating settings"))
        }
    }

    override suspend fun getLevelOverview(userId: String, targetLanguage: String): LevelOverviewResponse {
        return try {
            val availableLevels = getAllAvailableLevels()[targetLanguage] ?: emptyMap()
            
            transaction {
                val levelSummaries = mutableListOf<LevelSummary>()
                
                // Define level order and titles
                val levelOrder = listOf("A1", "A2", "B1", "B2", "C1", "C2")
                val levelTitles = mapOf(
                    "A1" to mapOf("en" to "Beginner", "es" to "Principiante"),
                    "A2" to mapOf("en" to "Elementary", "es" to "Elemental"),
                    "B1" to mapOf("en" to "Intermediate", "es" to "Intermedio"),
                    "B2" to mapOf("en" to "Upper Intermediate", "es" to "Intermedio Alto"),
                    "C1" to mapOf("en" to "Advanced", "es" to "Avanzado"),
                    "C2" to mapOf("en" to "Proficiency", "es" to "Dominio")
                )
                
                for (level in levelOrder) {
                    val topicsInLevel = availableLevels[level] ?: emptyList()
                    if (topicsInLevel.isNotEmpty()) {
                        // Count completed topics for this level
                        val userUuid = UUID.fromString(userId)
                        val completedTopics = TopicProgressTable.select {
                            (TopicProgressTable.userId eq userUuid) and
                            TopicProgressTable.topicId.like("$targetLanguage-$level-%") and
                            (TopicProgressTable.completedExercises greater 0)
                        }.count().toInt()
                        
                        val totalTopics = topicsInLevel.size
                        
                        // Determine status
                        val status = when {
                            completedTopics == 0 && level != "A1" -> {
                                // Check if previous level is completed
                                val previousLevelIndex = levelOrder.indexOf(level) - 1
                                if (previousLevelIndex >= 0) {
                                    val previousLevel = levelOrder[previousLevelIndex]
                                    val previousLevelTopics = availableLevels[previousLevel]?.size ?: 0
                                    val previousLevelCompleted = if (previousLevelTopics > 0) {
                                        TopicProgressTable.select {
                                            (TopicProgressTable.userId eq userUuid) and
                                            TopicProgressTable.topicId.like("$targetLanguage-$previousLevel-%") and
                                            (TopicProgressTable.completedExercises greater 0)
                                        }.count().toInt()
                                    } else 0
                                    
                                    if (previousLevelCompleted < previousLevelTopics) "locked" else "in_progress"
                                } else "locked"
                            }
                            completedTopics == totalTopics -> "completed"
                            else -> "in_progress"
                        }
                        
                        levelSummaries.add(
                            LevelSummary(
                                level = level,
                                title = levelTitles[level] ?: mapOf("en" to level, "es" to level),
                                progress = LevelProgress(
                                    completedTopics = completedTopics,
                                    totalTopics = totalTopics
                                ),
                                status = status
                            )
                        )
                    }
                }
                
                LevelOverviewResponse(levels = levelSummaries)
            }
        } catch (e: Exception) {
            println("Error getting level overview: ${e.message}")
            LevelOverviewResponse(levels = emptyList())
        }
    }

    override suspend fun getLevelTopics(userId: String, targetLanguage: String, level: String): LevelTopicsResponse {
        return try {
            val topicsInLevel = getLevelsByLanguageAndLevel(targetLanguage, level)
            
            transaction {
                val userUuid = UUID.fromString(userId)
                val topicSummaries = mutableListOf<TopicSummary>()
                
                for (topic in topicsInLevel) {
                    val topicId = topic.id ?: continue
                    
                    // Calculate total exercises in this topic
                    val totalExercises = (topic.exercises?.size ?: 0) + (topic.phrases?.size ?: 0)
                    
                    // Get progress for this topic
                    val progress = TopicProgressTable.select {
                        (TopicProgressTable.userId eq userUuid) and
                        (TopicProgressTable.topicId eq topicId)
                    }.singleOrNull()
                    
                    val topicProgress = if (progress != null) {
                        TopicProgress(
                            completedExercises = progress[TopicProgressTable.completedExercises],
                            correctAnswers = progress[TopicProgressTable.correctAnswers],
                            wrongAnswers = progress[TopicProgressTable.wrongAnswers],
                            totalExercises = totalExercises,
                            lastAttempted = progress[TopicProgressTable.lastAttempted]?.toString()
                        )
                    } else {
                        // Even without progress, show the total exercises
                        TopicProgress(
                            completedExercises = 0,
                            correctAnswers = 0,
                            wrongAnswers = 0,
                            totalExercises = totalExercises,
                            lastAttempted = null
                        )
                    }
                    
                    // Determine status
                    val status = when {
                        progress != null && progress[TopicProgressTable.completedExercises] > 0 -> "completed"
                        progress == null -> {
                            // Check if this topic should be locked
                            val topicIndex = topicsInLevel.indexOf(topic)
                            if (topicIndex == 0) "available" // First topic is always available
                            else {
                                // Check if previous topic is completed
                                val previousTopic = topicsInLevel[topicIndex - 1]
                                val previousTopicId = previousTopic.id ?: ""
                                val previousProgress = TopicProgressTable.select {
                                    (TopicProgressTable.userId eq userUuid) and
                                    (TopicProgressTable.topicId eq previousTopicId)
                                }.singleOrNull()
                                
                                if (previousProgress != null && previousProgress[TopicProgressTable.completedExercises] > 0) {
                                    "available"
                                } else {
                                    "locked"
                                }
                            }
                        }
                        else -> "in_progress"
                    }
                    
                    val lockedReason = if (status == "locked") {
                        mapOf(
                            "en" to "Complete previous topic first",
                            "es" to "Completa el tema anterior primero"
                        )
                    } else null
                    
                    topicSummaries.add(
                        TopicSummary(
                            id = topicId.substringAfterLast("-"), // Remove language-level prefix for cleaner ID
                            title = topic.title,
                            description = topic.description,
                            tip = topic.tip,
                            status = status,
                            progress = topicProgress, // Always include progress now
                            lockedReason = lockedReason
                        )
                    )
                }
                
                LevelTopicsResponse(
                    level = level,
                    topics = topicSummaries
                )
            }
        } catch (e: Exception) {
            println("Error getting level topics: ${e.message}")
            LevelTopicsResponse(level = level, topics = emptyList())
        }
    }

    override suspend fun getExercise(userId: String, targetLanguage: String, level: String, topicId: String, exerciseId: String): ExerciseResponse? {
        return try {
            withContext(Dispatchers.IO) {
                // Load the topic file
                val resource = {}::class.java.getResource("/levels/$targetLanguage/$level/$topicId.json")
                if (resource == null) return@withContext null
                
                val topicFile = resource.readText()
                val topic = json.decodeFromString<Level>(topicFile)
                
                // Find the specific exercise
                val exercise = topic.exercises?.find { it.id == exerciseId } ?: return@withContext null
                
                // Get user progress for this topic
                val topicProgress = transaction {
                    TopicProgressTable.select {
                        (TopicProgressTable.userId eq UUID.fromString(userId)) and
                        (TopicProgressTable.topicId eq "$targetLanguage-$level-$topicId")
                    }.singleOrNull()
                }
                
                // Check if this exercise was completed
                val completedExercises = topicProgress?.get(TopicProgressTable.completedExercises) ?: 0
                val exerciseIndex = topic.exercises.indexOf(exercise)
                val isCompleted = exerciseIndex < completedExercises
                
                ExerciseResponse(
                    id = exercise.id,
                    topicId = topicId,
                    type = exercise.type,
                    prompt = exercise.prompt,
                    solution = if (isCompleted) exercise.solution else null, // Only show solution if completed
                    options = exercise.options,
                    previousAttempts = 0, // TODO: Track individual exercise attempts if needed
                    isCompleted = isCompleted,
                    tip = exercise.tip // Include the educational tip
                )
            }
        } catch (e: Exception) {
            println("Error getting exercise: ${e.message}")
            null
        }
    }

    override suspend fun getNextExercise(userId: String, targetLanguage: String, level: String, topicId: String): ExerciseResponse? {
        return try {
            withContext(Dispatchers.IO) {
                // Load the topic file
                val resource = {}::class.java.getResource("/levels/$targetLanguage/$level/$topicId.json")
                if (resource == null) return@withContext null
                
                val topicFile = resource.readText()
                val topic = json.decodeFromString<Level>(topicFile)
                
                if (topic.exercises.isNullOrEmpty()) return@withContext null
                
                // Get user progress for this topic
                val topicProgress = transaction {
                    TopicProgressTable.select {
                        (TopicProgressTable.userId eq UUID.fromString(userId)) and
                        (TopicProgressTable.topicId eq "$targetLanguage-$level-$topicId")
                    }.singleOrNull()
                }
                
                // Get the number of completed exercises for this topic
                val completedExercises = topicProgress?.get(TopicProgressTable.completedExercises) ?: 0
                
                // Return the next exercise (the one at index = completedExercises)
                if (completedExercises >= topic.exercises.size) {
                    // All exercises in this topic are completed
                    return@withContext null
                }
                
                val nextExercise = topic.exercises[completedExercises]
                
                ExerciseResponse(
                    id = nextExercise.id,
                    topicId = topicId,
                    type = nextExercise.type,
                    prompt = nextExercise.prompt,
                    solution = null, // Don't show solution until completed
                    options = nextExercise.options,
                    previousAttempts = 0, // TODO: Track individual exercise attempts if needed
                    isCompleted = false,
                    tip = nextExercise.tip // Include the educational tip
                )
            }
        } catch (e: Exception) {
            println("Error getting next exercise: ${e.message}")
            null
        }
    }

    override suspend fun createDefaultUserSettings(userId: String): Boolean {
        return try {
            transaction {
                UserSettings.insert {
                    it[UserSettings.userId] = UUID.fromString(userId)
                    it[UserSettings.nativeLanguage] = "en"
                    it[UserSettings.targetLanguage] = "es"
                    it[UserSettings.darkMode] = false
                    it[UserSettings.onboardingStep] = "native"
                }.insertedCount > 0
            }
        } catch (e: Exception) {
            println("Error creating default user settings: ${e.message}")
            false
        }
    }

    // User management methods for auth service
    override suspend fun createUser(userId: String): Boolean {
        return try {
            val userUuid = UUID.fromString(userId) // This will throw IllegalArgumentException for invalid UUID
            
            transaction {
                // Check if user already exists
                val existingUser = UserSettings.select { UserSettings.userId eq userUuid }.count()
                if (existingUser > 0) {
                    return@transaction false // User already exists
                }
                
                // Create default user settings
                UserSettings.insert {
                    it[UserSettings.userId] = userUuid
                    it[UserSettings.nativeLanguage] = "en"
                    it[UserSettings.targetLanguage] = "es"
                    it[UserSettings.darkMode] = false
                    it[UserSettings.onboardingStep] = "language_selection"
                }.insertedCount > 0
            }
        } catch (e: IllegalArgumentException) {
            // Re-throw UUID format exceptions to be handled by the route
            throw e
        } catch (e: Exception) {
            println("Error creating user: ${e.message}")
            false
        }
    }

    override suspend fun deleteUser(userId: String): Boolean {
        return try {
            val userUuid = UUID.fromString(userId) // This will throw IllegalArgumentException for invalid UUID
            
            transaction {
                // Delete all user data in order (foreign key constraints)
                TopicProgressTable.deleteWhere { TopicProgressTable.userId eq userUuid }
                UserProgress.deleteWhere { UserProgress.userId eq userUuid }
                UserSettings.deleteWhere { UserSettings.userId eq userUuid }
                
                true
            }
        } catch (e: IllegalArgumentException) {
            // Re-throw UUID format exceptions to be handled by the route
            throw e
        } catch (e: Exception) {
            println("Error deleting user: ${e.message}")
            false
        }
    }

    override suspend fun deleteUserProgress(userId: String): Boolean {
        return try {
            val userUuid = UUID.fromString(userId) // This will throw IllegalArgumentException for invalid UUID
            
            transaction {
                // Delete only progress data, keep user settings
                TopicProgressTable.deleteWhere { TopicProgressTable.userId eq userUuid }
                UserProgress.deleteWhere { UserProgress.userId eq userUuid }
                
                // Reset onboarding step to restart learning
                UserSettings.update({ UserSettings.userId eq userUuid }) {
                    it[UserSettings.onboardingStep] = "language_selection"
                }
                
                true
            }
        } catch (e: IllegalArgumentException) {
            // Re-throw UUID format exceptions to be handled by the route
            throw e
        } catch (e: Exception) {
            println("Error deleting user progress: ${e.message}")
            false
        }
    }
}
