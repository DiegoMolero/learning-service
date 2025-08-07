package dev.learning.repository

import dev.learning.DatabaseConfig
import dev.learning.UserSettingsResponse
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.*

interface UserRepository {
    suspend fun getUserSettings(userId: String): UserSettingsResponse?
    suspend fun updateUserSettings(userId: String, nativeLanguage: String?, targetLanguage: String?, darkMode: Boolean?, onboardingStep: String?, userLevel: String?): Pair<Boolean, List<String>>
    suspend fun createDefaultUserSettings(userId: String): Boolean
    
    // User management methods for auth service
    suspend fun createUser(userId: String): Boolean
    suspend fun deleteUser(userId: String): Boolean
}

// Database tables needed for user functions
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

object ExerciseAttempts : UUIDTable("exercise_attempts") {
    val userId = uuid("user_id")
    val exerciseId = varchar("exercise_id", 100)
    val userAnswer = text("user_answer")
    val isCorrect = bool("is_correct")
    val answerStatus = varchar("answer_status", 20) // "correct", "incorrect", "skipped", "revealed"
    val attemptedAt = timestamp("attempted_at").defaultExpression(CurrentTimestamp())
}

class DatabaseUserRepository(
    private val databaseConfig: DatabaseConfig,
    private val environmentName: String
) : UserRepository {
    
    private val dataSource: HikariDataSource
    
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

    override suspend fun updateUserSettings(userId: String, nativeLanguage: String?, targetLanguage: String?, darkMode: Boolean?, onboardingStep: String?, userLevel: String?): Pair<Boolean, List<String>> {
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
}
