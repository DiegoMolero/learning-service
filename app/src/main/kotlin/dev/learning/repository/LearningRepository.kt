package dev.learning.repository

import dev.learning.DatabaseConfig
import dev.learning.Level
import dev.learning.UserProgressResponse
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

interface LearningRepository {
    suspend fun getLevel(levelId: String): Level?
    suspend fun getAllLevels(): List<Level>
    suspend fun getUserProgress(userId: String): List<UserProgressResponse>
    suspend fun getUserProgressForLevel(userId: String, levelId: String): UserProgressResponse?
    suspend fun updateUserProgress(userId: String, levelId: String, completedPhraseIds: List<String>): Boolean
    suspend fun getUserSettings(userId: String): UserSettingsResponse?
    suspend fun updateUserSettings(userId: String, nativeLanguage: String?, targetLanguage: String?, darkMode: Boolean?, onboardingPhase: String?): Boolean
    suspend fun updateUserSettingsWithWarnings(userId: String, nativeLanguage: String?, targetLanguage: String?, darkMode: Boolean?, onboardingPhase: String?): Pair<Boolean, List<String>>
    suspend fun createDefaultUserSettings(userId: String): Boolean
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

object UserSettings : UUIDTable("user_settings") {
    val userId = uuid("user_id").uniqueIndex()
    val nativeLanguage = varchar("native_language", 10).default("en")
    val targetLanguage = varchar("target_language", 10).default("es")
    val darkMode = bool("dark_mode").default(false)
    val onboardingPhase = varchar("onboarding_phase", 20).default("native")
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
                SchemaUtils.drop(UserProgress, UserSettings)
            }
            SchemaUtils.create(UserProgress, UserSettings)
        }
    }

    override suspend fun getLevel(levelId: String): Level? {
        return try {
            val resource = {}::class.java.getResource("/levels/$levelId.json")
            if (resource != null) {
                val content = resource.readText()
                json.decodeFromString<Level>(content)
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error loading level $levelId: ${e.message}")
            null
        }
    }

    override suspend fun getAllLevels(): List<Level> {
        return try {
            val levelsDir = {}::class.java.getResource("/levels")
            if (levelsDir != null) {
                val levels = mutableListOf<Level>()
                // For now, we'll manually list known levels
                // In a real implementation, you might scan the directory
                val knownLevels = listOf("level-1", "level-2") // Add more as you create them
                
                for (levelId in knownLevels) {
                    getLevel(levelId)?.let { levels.add(it) }
                }
                levels
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Error loading levels: ${e.message}")
            emptyList()
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
                            onboardingPhase = row[UserSettings.onboardingPhase]
                        )
                    }
            }
        } catch (e: Exception) {
            println("Error getting user settings: ${e.message}")
            null
        }
    }

    override suspend fun updateUserSettings(userId: String, nativeLanguage: String?, targetLanguage: String?, darkMode: Boolean?, onboardingPhase: String?): Boolean {
        return try {
            transaction {
                val userUuid = UUID.fromString(userId)
                val existing = UserSettings.select { UserSettings.userId eq userUuid }.singleOrNull()
                
                // Validate that nativeLanguage and targetLanguage are different if both are provided
                if (nativeLanguage != null && targetLanguage != null && nativeLanguage == targetLanguage) {
                    throw IllegalArgumentException("Native language and target language cannot be the same")
                }
                
                // If only one language is provided, validate against existing settings
                if (existing != null) {
                    val currentNative = existing[UserSettings.nativeLanguage]
                    val currentTarget = existing[UserSettings.targetLanguage]
                    
                    val finalNative = nativeLanguage ?: currentNative
                    val finalTarget = targetLanguage ?: currentTarget
                    
                    if (finalNative == finalTarget) {
                        throw IllegalArgumentException("Native language and target language cannot be the same")
                    }
                }
                
                // Validate supported languages
                val supportedLanguages = listOf("en", "es")
                nativeLanguage?.let { 
                    if (it !in supportedLanguages) {
                        throw IllegalArgumentException("Unsupported native language: $it. Supported: ${supportedLanguages.joinToString()}")
                    }
                }
                targetLanguage?.let { 
                    if (it !in supportedLanguages) {
                        throw IllegalArgumentException("Unsupported target language: $it. Supported: ${supportedLanguages.joinToString()}")
                    }
                }
                
                // Validate onboarding phase transitions
                val validPhases = listOf("native", "learning", "complete")
                onboardingPhase?.let { phase ->
                    if (phase !in validPhases) {
                        throw IllegalArgumentException("Invalid onboarding phase: $phase. Valid phases: ${validPhases.joinToString()}")
                    }
                }
                
                // Validate that onboarding can only progress if both languages are set
                var finalOnboardingPhase = onboardingPhase
                if (onboardingPhase == "complete") {
                    val finalNative = nativeLanguage ?: (existing?.get(UserSettings.nativeLanguage))
                    val finalTarget = targetLanguage ?: (existing?.get(UserSettings.targetLanguage))
                    
                    if (finalNative.isNullOrBlank() || finalTarget.isNullOrBlank()) {
                        // Don't allow completion if languages are not set
                        finalOnboardingPhase = null
                        println("Warning: Ignoring onboarding completion request - languages not fully configured")
                    }
                }
                
                if (existing != null) {
                    UserSettings.update({ UserSettings.userId eq userUuid }) {
                        nativeLanguage?.let { lang -> it[UserSettings.nativeLanguage] = lang }
                        targetLanguage?.let { lang -> it[UserSettings.targetLanguage] = lang }
                        darkMode?.let { mode -> it[UserSettings.darkMode] = mode }
                        finalOnboardingPhase?.let { phase -> it[UserSettings.onboardingPhase] = phase }
                        it[UserSettings.updatedAt] = kotlinx.datetime.Clock.System.now()
                    } > 0
                } else {
                    // Create default settings with provided updates
                    val finalNative = nativeLanguage ?: "en"
                    val finalTarget = targetLanguage ?: "es"
                    
                    // Final validation for new records
                    if (finalNative == finalTarget) {
                        throw IllegalArgumentException("Native language and target language cannot be the same")
                    }
                    
                    // For new records, set default onboarding phase or validate provided phase
                    var finalOnboardingPhase = onboardingPhase ?: "native"
                    if (onboardingPhase == "complete" && (finalNative.isBlank() || finalTarget.isBlank())) {
                        finalOnboardingPhase = "native"
                        println("Warning: Setting onboarding phase to 'native' for new user - languages not fully configured")
                    }
                    
                    UserSettings.insert {
                        it[UserSettings.userId] = userUuid
                        it[UserSettings.nativeLanguage] = finalNative
                        it[UserSettings.targetLanguage] = finalTarget
                        it[UserSettings.darkMode] = darkMode ?: false
                        it[UserSettings.onboardingPhase] = finalOnboardingPhase
                    }.insertedCount > 0
                }
            }
        } catch (e: IllegalArgumentException) {
            println("Validation error updating user settings: ${e.message}")
            false
        } catch (e: Exception) {
            println("Error updating user settings: ${e.message}")
            false
        }
    }

    override suspend fun updateUserSettingsWithWarnings(userId: String, nativeLanguage: String?, targetLanguage: String?, darkMode: Boolean?, onboardingPhase: String?): Pair<Boolean, List<String>> {
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
                onboardingPhase?.let { phase ->
                    if (phase !in validPhases) {
                        throw IllegalArgumentException("Invalid onboarding phase: $phase. Valid phases: ${validPhases.joinToString()}")
                    }
                }
                
                // Validate that onboarding can only progress to complete if both languages are set
                var finalOnboardingPhase = onboardingPhase
                if (onboardingPhase == "complete") {
                    val finalNative = finalNativeLanguage ?: (existing?.get(UserSettings.nativeLanguage))
                    val finalTarget = finalTargetLanguage ?: (existing?.get(UserSettings.targetLanguage))
                    
                    if (finalNative.isNullOrBlank() || finalTarget.isNullOrBlank()) {
                        // Don't allow completion if languages are not set
                        finalOnboardingPhase = null
                        warnings.add("Onboarding completion ignored - both native and target languages must be selected first")
                    }
                }
                
                val success = if (existing != null) {
                    UserSettings.update({ UserSettings.userId eq userUuid }) {
                        finalNativeLanguage?.let { lang -> it[UserSettings.nativeLanguage] = lang }
                        finalTargetLanguage?.let { lang -> it[UserSettings.targetLanguage] = lang }
                        darkMode?.let { mode -> it[UserSettings.darkMode] = mode }
                        finalOnboardingPhase?.let { phase -> it[UserSettings.onboardingPhase] = phase }
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
                    var newUserOnboardingPhase = finalOnboardingPhase ?: "native"
                    if (onboardingPhase == "complete" && (finalNative.isBlank() || finalTarget.isBlank())) {
                        newUserOnboardingPhase = "native"
                        warnings.add("Onboarding phase set to 'native' for new user - both languages must be selected first")
                    }
                    
                    UserSettings.insert {
                        it[UserSettings.userId] = userUuid
                        it[UserSettings.nativeLanguage] = finalNative
                        it[UserSettings.targetLanguage] = finalTarget
                        it[UserSettings.darkMode] = darkMode ?: false
                        it[UserSettings.onboardingPhase] = newUserOnboardingPhase
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

    override suspend fun createDefaultUserSettings(userId: String): Boolean {
        return try {
            transaction {
                UserSettings.insert {
                    it[UserSettings.userId] = UUID.fromString(userId)
                    it[UserSettings.nativeLanguage] = "en"
                    it[UserSettings.targetLanguage] = "es"
                    it[UserSettings.darkMode] = false
                    it[UserSettings.onboardingPhase] = "native"
                }.insertedCount > 0
            }
        } catch (e: Exception) {
            println("Error creating default user settings: ${e.message}")
            false
        }
    }
}
