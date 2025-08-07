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
        
        // Validate target language change
        targetLanguage?.let { newTargetLang ->
            if (existingSettings?.targetLanguage != null && 
                existingSettings.targetLanguage != newTargetLang) {
                warnings.add("Changing target language will reset your progress")
            }
        }
        
        // Validate native language change  
        nativeLanguage?.let { newNativeLang ->
            if (existingSettings?.nativeLanguage != null && 
                existingSettings.nativeLanguage != newNativeLang) {
                warnings.add("Changing native language may affect your learning experience")
            }
        }

        return transaction {
            // Create updated settings object
            val updatedSettings = UserSettingsResponse(
                userId = userId,
                nativeLanguage = nativeLanguage ?: existingSettings?.nativeLanguage ?: "en",
                targetLanguage = targetLanguage ?: existingSettings?.targetLanguage ?: "es", 
                darkMode = darkMode ?: existingSettings?.darkMode ?: false,
                onboardingStep = onboardingStep ?: existingSettings?.onboardingStep ?: "native",
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