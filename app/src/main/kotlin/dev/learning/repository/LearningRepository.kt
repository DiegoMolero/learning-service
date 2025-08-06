package dev.learning.repository

import dev.learning.DatabaseConfig
import dev.learning.Level
import dev.learning.CategoryConfig
import dev.learning.LevelOverviewResponse
import dev.learning.LevelTopicsResponse
import dev.learning.ExerciseResponse
import dev.learning.NextExerciseResponse
import dev.learning.LevelSummary
import dev.learning.LevelProgress
import dev.learning.TopicSummary
import dev.learning.TopicProgress
import dev.learning.UserProgressResponse
import dev.learning.UserSettingsResponse
import dev.learning.AnswerStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import java.util.*
import java.io.File

interface LearningRepository {
    // New dashboard methods
    suspend fun getLevelOverview(userId: String, targetLanguage: String): LevelOverviewResponse
    suspend fun getLevelTopics(userId: String, targetLanguage: String, level: String): LevelTopicsResponse
    suspend fun getExercise(userId: String, targetLanguage: String, level: String, topicId: String, exerciseId: String): ExerciseResponse?
    suspend fun getNextExercise(userId: String, targetLanguage: String, level: String, topicId: String): NextExerciseResponse
    
    suspend fun getUserProgress(userId: String): List<UserProgressResponse>
    suspend fun getUserProgressForLevel(userId: String, levelId: String): UserProgressResponse?
    suspend fun updateUserProgress(userId: String, levelId: String, completedPhraseIds: List<String>): Boolean
    suspend fun getUserSettings(userId: String): UserSettingsResponse?
    suspend fun updateUserSettingsWithWarnings(userId: String, nativeLanguage: String?, targetLanguage: String?, darkMode: Boolean?, onboardingStep: String?, userLevel: String?): Pair<Boolean, List<String>>
    suspend fun createDefaultUserSettings(userId: String): Boolean
    
    // User management methods for auth service
    suspend fun createUser(userId: String): Boolean
    suspend fun deleteUser(userId: String): Boolean
    suspend fun deleteUserProgress(userId: String): Boolean
    
    // Exercise answer submission
    suspend fun submitExerciseAnswer(userId: String, targetLanguage: String, level: String, topicId: String, exerciseId: String, userAnswer: String, answerStatus: AnswerStatus): Pair<Boolean, TopicProgress?>
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
    val nativeLanguage = varchar("native_language", 10)
    val targetLanguage = varchar("target_language", 10)
    val darkMode = bool("dark_mode").default(false)
    val onboardingStep = varchar("onboarding_step", 50).default("native")
    val userLevel = varchar("user_level", 5).nullable() // A1, A2, B1, B2, C1, C2
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
}

object ExerciseAttempts : UUIDTable("exercise_attempts") {
    val userId = uuid("user_id")
    val exerciseId = varchar("exercise_id", 100)
    val userAnswer = text("user_answer")
    val isCorrect = bool("is_correct")
    val answerStatus = varchar("answer_status", 20) // "correct", "incorrect", "skipped", "revealed"
    val attemptedAt = timestamp("attempted_at").defaultExpression(CurrentTimestamp())
}

class DatabaseLearningRepository(
    private val databaseConfig: DatabaseConfig,
    private val environmentName: String
) : LearningRepository {
    
    private val dataSource: HikariDataSource
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Get the base path for resources depending on the environment.
     * In test mode, use "/test/levels", otherwise use "/levels"
     */
    private fun getResourceBasePath(): String {
        return if (environmentName == "test") "/test/levels" else "/levels"
    }
    
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
                SchemaUtils.drop(UserProgress, UserSettings, TopicProgressTable, ExerciseAttempts)
            }
            SchemaUtils.create(UserProgress, UserSettings, TopicProgressTable, ExerciseAttempts)
        }
    }

    private suspend fun loadLevelCategoriesConfig(targetLanguage: String): Map<String, CategoryConfig>? {
        return try {
            val configResource = {}::class.java.getResource("${getResourceBasePath()}/$targetLanguage/levels.json")
            if (configResource != null) {
                val configContent = File(configResource.toURI()).readText()
                json.decodeFromString<Map<String, CategoryConfig>>(configContent)
            } else {
                println("Level categories configuration file not found for language: $targetLanguage")
                null
            }
        } catch (e: Exception) {
            println("Error loading level categories configuration for $targetLanguage: ${e.message}")
            null
        }
    }

    private suspend fun getLevelsByLanguageAndLevel(targetLanguage: String, level: String): List<Level> {
        return try {
            val levels = mutableListOf<Level>()
            val categoriesConfig = loadLevelCategoriesConfig(targetLanguage)
            
            categoriesConfig?.values?.forEach { categoryConfig ->
                // Only process categories that support this difficulty level
                if (level in categoryConfig.difficulty) {
                    val categoryResource = {}::class.java.getResource("${getResourceBasePath()}/$targetLanguage/${categoryConfig.path}")
                    
                    if (categoryResource != null) {
                        val categoryDirectory = File(categoryResource.toURI())
                        if (categoryDirectory.exists() && categoryDirectory.isDirectory) {
                            categoryDirectory.listFiles { file -> file.name.endsWith(".json") }?.forEach { file ->
                                try {
                                    val content = file.readText()
                                    val levelData = json.decodeFromString<Level>(content)
                                    
                                    // Only include topics that match this level
                                    if (levelData.level == level) {
                                        val levelId = "$targetLanguage-$level-${file.nameWithoutExtension}"
                                        levels.add(levelData.copy(id = levelId))
                                    }
                                } catch (e: Exception) {
                                    println("Error loading level from ${file.name}: ${e.message}")
                                }
                            }
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
            
            // For now, we only support "en"
            val language = "en"
            val categoriesConfig = loadLevelCategoriesConfig(language)
            
            categoriesConfig?.let { categories ->
                result[language] = mutableMapOf()
                
                // Initialize all difficulty levels
                val allLevels = listOf("A1", "A2", "B1", "B2", "C1", "C2")
                allLevels.forEach { level ->
                    result[language]!![level] = mutableListOf()
                }
                
                // For each category, scan for topics and assign them to appropriate levels
                for ((categoryId, categoryConfig) in categories) {
                    val categoryDir = File(
                        {}::class.java.getResource("${getResourceBasePath()}/$language/${categoryConfig.path}")?.toURI() ?: continue
                    )
                    
                    if (categoryDir.exists() && categoryDir.isDirectory) {
                        categoryDir.listFiles { file -> file.name.endsWith(".json") }?.forEach { jsonFile ->
                            try {
                                val jsonContent = jsonFile.readText()
                                val topicData = Json.parseToJsonElement(jsonContent).jsonObject
                                val topicLevel = topicData["level"]?.jsonPrimitive?.content
                                
                                // Check if the topic's level matches any of the category's difficulty levels
                                if (topicLevel != null && topicLevel in categoryConfig.difficulty) {
                                    result[language]!![topicLevel]!!.add(jsonFile.nameWithoutExtension)
                                }
                            } catch (e: Exception) {
                                println("Error parsing topic file ${jsonFile.name}: ${e.message}")
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

    override suspend fun getLevelOverview(userId: String, targetLanguage: String): LevelOverviewResponse {
        return try {
            val availableLevels = getAllAvailableLevels()[targetLanguage] ?: emptyMap()
            
            transaction {
                val levelSummaries = mutableListOf<LevelSummary>()
                
                // Define level order, titles and descriptions
                val levelOrder = listOf("A1", "A2", "B1", "B2", "C1", "C2")
                val levelInfo = mapOf(
                    "A1" to Pair(
                        mapOf("en" to "Beginner", "es" to "Principiante"),
                        mapOf("en" to "Start your English journey with basic grammar and vocabulary", "es" to "Comienza tu viaje en inglés con gramática y vocabulario básico")
                    ),
                    "A2" to Pair(
                        mapOf("en" to "Elementary", "es" to "Elemental"),
                        mapOf("en" to "Build confidence with essential grammar and everyday expressions", "es" to "Gana confianza con gramática esencial y expresiones cotidianas")
                    ),
                    "B1" to Pair(
                        mapOf("en" to "Intermediate", "es" to "Intermedio"),
                        mapOf("en" to "Develop fluency with complex grammar and varied vocabulary", "es" to "Desarrolla fluidez con gramática compleja y vocabulario variado")
                    ),
                    "B2" to Pair(
                        mapOf("en" to "Upper Intermediate", "es" to "Intermedio Alto"),
                        mapOf("en" to "Express yourself clearly on complex topics with nuanced grammar", "es" to "Exprésate claramente sobre temas complejos con gramática matizada")
                    ),
                    "C1" to Pair(
                        mapOf("en" to "Advanced", "es" to "Avanzado"),
                        mapOf("en" to "Achieve near-native proficiency with sophisticated language use", "es" to "Alcanza competencia casi nativa con uso sofisticado del idioma")
                    ),
                    "C2" to Pair(
                        mapOf("en" to "Proficient", "es" to "Competente"),
                        mapOf("en" to "Perfect your English with native-like precision and subtlety", "es" to "Perfecciona tu inglés con precisión y sutileza como nativo")
                    )
                )
                
                for (level in levelOrder) {
                    val topicsInLevel = availableLevels[level] ?: emptyList()
                    val (title, description) = levelInfo[level] ?: continue
                    
                    if (topicsInLevel.isNotEmpty()) {
                        // Count completed topics for this level
                        val userUuid = UUID.fromString(userId)
                        val completedTopics = TopicProgressTable.select {
                            (TopicProgressTable.userId eq userUuid) and
                            TopicProgressTable.topicId.like("$targetLanguage-$level-%")
                        }.count().toInt()
                        
                        val totalTopics = topicsInLevel.size
                        
                        val status = when {
                            completedTopics == 0 -> if (level == "A1") "available" else "locked"
                            completedTopics == totalTopics -> "completed"
                            else -> "in_progress"
                        }
                        
                        levelSummaries.add(
                            LevelSummary(
                                level = level,
                                title = title,
                                description = description,
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
                        TopicProgress(
                            completedExercises = 0,
                            correctAnswers = 0,
                            wrongAnswers = 0,
                            totalExercises = totalExercises,
                            lastAttempted = null
                        )
                    }
                    
                    val status = when {
                        topicProgress.completedExercises == 0 -> "available"
                        topicProgress.completedExercises == totalExercises -> "completed"
                        else -> "in_progress"
                    }
                    
                    topicSummaries.add(
                        TopicSummary(
                            id = topicId,
                            title = topic.title,
                            description = topic.description,
                            tip = topic.tip,
                            status = status,
                            progress = topicProgress
                        )
                    )
                }
                
                LevelTopicsResponse(level = level, topics = topicSummaries)
            }
        } catch (e: Exception) {
            println("Error getting level topics: ${e.message}")
            LevelTopicsResponse(level = level, topics = emptyList())
        }
    }

    // User management methods
    override suspend fun createUser(userId: String): Boolean {
        return try {
            transaction {
                // Create default user settings
                UserSettings.insert {
                    it[UserSettings.userId] = UUID.fromString(userId)
                    it[nativeLanguage] = "en"
                    it[targetLanguage] = "es"
                    it[darkMode] = false
                    it[onboardingStep] = "native"
                    it[userLevel] = null // Will be set during level selection step
                }
                true
            }
        } catch (e: Exception) {
            println("Error creating user: ${e.message}")
            false
        }
    }

    override suspend fun deleteUser(userId: String): Boolean {
        return try {
            transaction {
                val userUuid = UUID.fromString(userId)
                UserSettings.deleteWhere { UserSettings.userId eq userUuid }
                UserProgress.deleteWhere { UserProgress.userId eq userUuid }
                TopicProgressTable.deleteWhere { TopicProgressTable.userId eq userUuid }
                ExerciseAttempts.deleteWhere { ExerciseAttempts.userId eq userUuid }
                true
            }
        } catch (e: Exception) {
            println("Error deleting user: ${e.message}")
            false
        }
    }

    override suspend fun deleteUserProgress(userId: String): Boolean {
        return try {
            transaction {
                val userUuid = UUID.fromString(userId)
                UserProgress.deleteWhere { UserProgress.userId eq userUuid }
                TopicProgressTable.deleteWhere { TopicProgressTable.userId eq userUuid }
                ExerciseAttempts.deleteWhere { ExerciseAttempts.userId eq userUuid }
                true
            }
        } catch (e: Exception) {
            println("Error deleting user progress: ${e.message}")
            false
        }
    }

    // TODO: Implement remaining methods
    override suspend fun getExercise(userId: String, targetLanguage: String, level: String, topicId: String, exerciseId: String): ExerciseResponse? {
        TODO("Not yet implemented")
    }

    override suspend fun getNextExercise(userId: String, targetLanguage: String, level: String, topicId: String): NextExerciseResponse {
        TODO("Not yet implemented")
    }

    override suspend fun getUserProgress(userId: String): List<UserProgressResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun getUserProgressForLevel(userId: String, levelId: String): UserProgressResponse? {
        TODO("Not yet implemented")
    }

    override suspend fun updateUserProgress(userId: String, levelId: String, completedPhraseIds: List<String>): Boolean {
        TODO("Not yet implemented")
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
                            onboardingStep = row[UserSettings.onboardingStep],
                            userLevel = row[UserSettings.userLevel]
                        )
                    }
            }
        } catch (e: Exception) {
            println("Error getting user settings: ${e.message}")
            null
        }
    }

    override suspend fun updateUserSettingsWithWarnings(userId: String, nativeLanguage: String?, targetLanguage: String?, darkMode: Boolean?, onboardingStep: String?, userLevel: String?): Pair<Boolean, List<String>> {
        return try {
            val warnings = mutableListOf<String>()
            val userUuid = UUID.fromString(userId)
            
            // Validate onboarding step if provided
            onboardingStep?.let { step ->
                val validSteps = listOf("native", "learning", "level", "complete")
                if (step !in validSteps) {
                    warnings.add("Invalid onboarding step. Valid steps are: ${validSteps.joinToString(", ")}")
                    return Pair(false, warnings)
                }
            }
            
            // Validate user level if provided
            userLevel?.let { level ->
                val validLevels = listOf("A1", "A2", "B1", "B2", "C1", "C2")
                if (level !in validLevels) {
                    warnings.add("Invalid user level. Valid levels are: ${validLevels.joinToString(", ")}")
                    return Pair(false, warnings)
                }
            }
            
            // Handle language conflicts
            if (nativeLanguage != null && targetLanguage != null && nativeLanguage == targetLanguage) {
                warnings.add("Native language and target language cannot be the same")
                return Pair(false, warnings)
            }
            
            transaction {
                val existing = UserSettings.select { UserSettings.userId eq userUuid }.singleOrNull()
                
                if (existing != null) {
                    // Update existing settings
                    UserSettings.update({ UserSettings.userId eq userUuid }) { row ->
                        nativeLanguage?.let { 
                            row[UserSettings.nativeLanguage] = it
                            // Auto-switch target language if conflict
                            if (it == existing[UserSettings.targetLanguage]) {
                                val autoTarget = if (it == "en") "es" else "en"
                                row[UserSettings.targetLanguage] = autoTarget
                                warnings.add("Target language automatically changed to $autoTarget to avoid conflict")
                            }
                        }
                        targetLanguage?.let { 
                            row[UserSettings.targetLanguage] = it
                            // Auto-switch native language if conflict
                            if (it == existing[UserSettings.nativeLanguage]) {
                                val autoNative = if (it == "en") "es" else "en"
                                row[UserSettings.nativeLanguage] = autoNative
                                warnings.add("Native language automatically changed to $autoNative to avoid conflict")
                            }
                        }
                        darkMode?.let { row[UserSettings.darkMode] = it }
                        onboardingStep?.let { row[UserSettings.onboardingStep] = it }
                        userLevel?.let { row[UserSettings.userLevel] = it }
                        row[UserSettings.updatedAt] = kotlinx.datetime.Clock.System.now()
                    }
                } else {
                    // Create new settings if they don't exist
                    UserSettings.insert { row ->
                        row[UserSettings.userId] = userUuid
                        row[UserSettings.nativeLanguage] = nativeLanguage ?: "en"
                        row[UserSettings.targetLanguage] = targetLanguage ?: "es"
                        row[UserSettings.darkMode] = darkMode ?: false
                        row[UserSettings.onboardingStep] = onboardingStep ?: "native"
                        row[UserSettings.userLevel] = userLevel
                    }
                }
                
                Pair(true, warnings)
            }
        } catch (e: Exception) {
            println("Error updating user settings: ${e.message}")
            Pair(false, listOf("Failed to update settings: ${e.message}"))
        }
    }

    override suspend fun createDefaultUserSettings(userId: String): Boolean {
        return try {
            transaction {
                UserSettings.insert {
                    it[UserSettings.userId] = UUID.fromString(userId)
                    it[nativeLanguage] = "en"
                    it[targetLanguage] = "es"
                    it[darkMode] = false
                    it[onboardingStep] = "native"
                    it[userLevel] = null // Will be set during level selection step
                }
                true
            }
        } catch (e: Exception) {
            println("Error creating default user settings: ${e.message}")
            false
        }
    }

    override suspend fun submitExerciseAnswer(userId: String, targetLanguage: String, level: String, topicId: String, exerciseId: String, userAnswer: String, answerStatus: AnswerStatus): Pair<Boolean, TopicProgress?> {
        TODO("Not yet implemented")
    }
}
