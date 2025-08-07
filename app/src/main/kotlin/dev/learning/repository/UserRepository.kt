package dev.learning.repository

import dev.learning.DatabaseConfig
import dev.learning.UserSettingsResponse
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
}

class DatabaseUserRepository(
    private val databaseConfig: DatabaseConfig) : UserRepository {
    
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
            SchemaUtils.createMissingTablesAndColumns(
                UserSettings,
                UserProgress,
                TopicProgressTable,
                ExerciseAttempts
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
                UserProgress.deleteWhere { UserProgress.userId eq userId }
                TopicProgressTable.deleteWhere { TopicProgressTable.userId eq userId }
                ExerciseAttempts.deleteWhere { ExerciseAttempts.userId eq userId }
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}