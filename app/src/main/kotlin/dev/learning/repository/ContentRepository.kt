package dev.learning.repository

import dev.learning.DatabaseConfig
import dev.learning.AnswerStatus
// New imports for hierarchical structure
import dev.learning.ModuleResponse
import dev.learning.ModuleDetailResponse
import dev.learning.UnitSummary
import dev.learning.UnitDetailResponse
import dev.learning.ExerciseSummary
import dev.learning.Exercise
import dev.learning.UnitProgress
import dev.learning.SubmitExerciseResponse
import dev.learning.UserSettingsResponse
import dev.learning.content.ContentLibrary
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.*
import dev.learning.repository.ModuleProgressTable
import dev.learning.repository.UnitProgressTable
import dev.learning.repository.ExerciseProgressTable
import dev.learning.repository.UserSettings
import dev.learning.repository.ExerciseAttempts

interface ContentRepository {
    suspend fun getModules(userId: String, lang: String): List<ModuleResponse>
    suspend fun getModule(userId: String, lang: String, moduleId: String): ModuleDetailResponse?
    suspend fun getUnits(userId: String, lang: String, moduleId: String): List<UnitSummary>
    suspend fun getUnit(userId: String, lang: String, moduleId: String, unitId: String): UnitDetailResponse?
    suspend fun getExercises(userId: String, lang: String, moduleId: String, unitId: String): List<ExerciseSummary>
    suspend fun getExerciseDetails(userId: String, lang: String, moduleId: String, unitId: String, exerciseId: String): Exercise?
    suspend fun submitExercise(userId: String, lang: String, moduleId: String, unitId: String, exerciseId: String, userAnswer: String, answerStatus: AnswerStatus): SubmitExerciseResponse
}

class DatabaseContentRepository(
    private val databaseConfig: DatabaseConfig,
    private val environment: String
) : ContentRepository {
    
    private val contentLibrary = ContentLibrary(if (environment == "test") "/test/content" else environment)
    private lateinit var dataSource: HikariDataSource

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
                SchemaUtils.drop(
                    ModuleProgressTable,
                    UnitProgressTable,
                    ExerciseProgressTable,
                    UserSettings,
                    ExerciseAttempts
                )
            }
            SchemaUtils.createMissingTablesAndColumns(
                ModuleProgressTable,
                UnitProgressTable,
                ExerciseProgressTable,
                UserSettings,
                ExerciseAttempts
            )
        }
    }

    override suspend fun getModules(userId: String, lang: String): List<ModuleResponse> {
        val modules = contentLibrary.getModules(lang)
        
        return transaction {
            modules.map { module ->
                val progress = ModuleProgressTable.select {
                    (ModuleProgressTable.userId eq userId) and
                    (ModuleProgressTable.moduleId eq module.moduleId)
                }.singleOrNull()

                ModuleResponse(
                    id = module.moduleId,
                    title = module.title,
                    description = module.description,
                    level = module.difficulty.firstOrNull() ?: "A1",
                    totalUnits = 0, // Will be calculated
                    completedUnits = 0, // Will be calculated from progress
                    status = if (progress?.get(ModuleProgressTable.isCompleted) == true) "completed" else "available"
                )
            }
        }
    }

    override suspend fun getModule(userId: String, lang: String, moduleId: String): ModuleDetailResponse? {
        val modules = contentLibrary.getModules(lang)
        val module = modules.find { it.moduleId == moduleId } ?: return null
        val units = contentLibrary.getUnits(lang, moduleId)
        
        return transaction {
            val unitsWithProgress = units.map { unit ->
                val unitProgress = UnitProgressTable.select {
                    (UnitProgressTable.userId eq userId) and
                    (UnitProgressTable.unitId eq unit.unitId)
                }.singleOrNull()

                // Count exercises in this unit
                val unitContent = contentLibrary.getUnitContent(lang, moduleId, unit.unitId)
                val totalExercises = unitContent?.exercises?.size ?: 0
                
                // Count completed exercises
                val completedExercises = ExerciseProgressTable.select {
                    (ExerciseProgressTable.userId eq userId) and
                    (ExerciseProgressTable.unitId eq unit.unitId) and
                    (ExerciseProgressTable.isCompleted eq true)
                }.count().toInt()

                UnitSummary(
                    id = unit.unitId,
                    title = unit.title,
                    description = unit.description,
                    totalExercises = totalExercises,
                    completedExercises = completedExercises,
                    status = if (unitProgress?.get(UnitProgressTable.isCompleted) == true) "completed" else "available"
                )
            }

            ModuleDetailResponse(
                id = module.moduleId,
                title = module.title,
                description = module.description,
                level = module.difficulty.firstOrNull() ?: "A1",
                units = unitsWithProgress
            )
        }
    }

    override suspend fun getUnits(userId: String, lang: String, moduleId: String): List<UnitSummary> {
        val units = contentLibrary.getUnits(lang, moduleId)
        
        return transaction {
            units.map { unit ->
                val unitProgress = UnitProgressTable.select {
                    (UnitProgressTable.userId eq userId) and
                    (UnitProgressTable.unitId eq unit.unitId)
                }.singleOrNull()

                // Count exercises in this unit
                val unitContent = contentLibrary.getUnitContent(lang, moduleId, unit.unitId)
                val totalExercises = unitContent?.exercises?.size ?: 0
                
                // Count completed exercises
                val completedExercises = ExerciseProgressTable.select {
                    (ExerciseProgressTable.userId eq userId) and
                    (ExerciseProgressTable.unitId eq unit.unitId) and
                    (ExerciseProgressTable.isCompleted eq true)
                }.count().toInt()

                UnitSummary(
                    id = unit.unitId,
                    title = unit.title,
                    description = unit.description,
                    totalExercises = totalExercises,
                    completedExercises = completedExercises,
                    status = if (unitProgress?.get(UnitProgressTable.isCompleted) == true) "completed" else "available"
                )
            }
        }
    }

    override suspend fun getUnit(userId: String, lang: String, moduleId: String, unitId: String): UnitDetailResponse? {
        val unitContent = contentLibrary.getUnitContent(lang, moduleId, unitId) ?: return null
        
        return transaction {
            val exercisesWithProgress = unitContent.exercises.map { exercise ->
                val exerciseProgress = ExerciseProgressTable.select {
                    (ExerciseProgressTable.userId eq userId) and
                    (ExerciseProgressTable.exerciseId eq exercise.id)
                }.singleOrNull()

                ExerciseSummary(
                    id = exercise.id,
                    type = exercise.type,
                    status = if (exerciseProgress?.get(ExerciseProgressTable.isCompleted) == true) "completed" else "available",
                    isCorrect = exerciseProgress?.get(ExerciseProgressTable.isCompleted)
                )
            }

            UnitDetailResponse(
                id = unitContent.unitId,
                title = unitContent.title,
                description = unitContent.description,
                tip = unitContent.tip,
                exercises = exercisesWithProgress
            )
        }
    }

    override suspend fun getExercises(userId: String, lang: String, moduleId: String, unitId: String): List<ExerciseSummary> {
        val unitContent = contentLibrary.getUnitContent(lang, moduleId, unitId) ?: return emptyList()
        
        return transaction {
            unitContent.exercises.map { exercise ->
                val progress = ExerciseProgressTable.select {
                    (ExerciseProgressTable.userId eq userId) and
                    (ExerciseProgressTable.exerciseId eq exercise.id)
                }.singleOrNull()

                ExerciseSummary(
                    id = exercise.id,
                    type = exercise.type,
                    status = if (progress?.get(ExerciseProgressTable.isCompleted) == true) "completed" else "available",
                    isCorrect = progress?.get(ExerciseProgressTable.isCompleted)
                )
            }
        }
    }

    override suspend fun getExerciseDetails(userId: String, lang: String, moduleId: String, unitId: String, exerciseId: String): Exercise? {
        val unitContent = contentLibrary.getUnitContent(lang, moduleId, unitId) ?: return null
        return unitContent.exercises.find { it.id == exerciseId }
    }

    override suspend fun submitExercise(userId: String, lang: String, moduleId: String, unitId: String, exerciseId: String, userAnswer: String, answerStatus: AnswerStatus): SubmitExerciseResponse {
        return transaction {
            // Record the exercise attempt
            ExerciseAttempts.insert {
                it[this.userId] = userId
                it[this.lang] = lang
                it[this.exerciseId] = exerciseId
                it[this.moduleId] = moduleId
                it[this.unitId] = unitId
                it[this.userAnswer] = userAnswer
                it[this.isCorrect] = answerStatus == AnswerStatus.CORRECT
                it[this.attemptedAt] = kotlinx.datetime.Clock.System.now()
            }

            // Update exercise progress
            val existingProgress = ExerciseProgressTable.select {
                (ExerciseProgressTable.userId eq userId) and
                (ExerciseProgressTable.exerciseId eq exerciseId)
            }.singleOrNull()

            if (existingProgress == null) {
                ExerciseProgressTable.insert {
                    it[this.userId] = userId
                    it[this.lang] = lang
                    it[this.moduleId] = moduleId
                    it[this.unitId] = unitId
                    it[this.exerciseId] = exerciseId
                    it[this.isCompleted] = answerStatus == AnswerStatus.CORRECT
                    it[this.createdAt] = kotlinx.datetime.Clock.System.now()
                    it[this.updatedAt] = kotlinx.datetime.Clock.System.now()
                }
            } else if (answerStatus == AnswerStatus.CORRECT && !existingProgress[ExerciseProgressTable.isCompleted]) {
                ExerciseProgressTable.update({
                    (ExerciseProgressTable.userId eq userId) and
                    (ExerciseProgressTable.exerciseId eq exerciseId)
                }) {
                    it[this.isCompleted] = true
                    it[this.updatedAt] = kotlinx.datetime.Clock.System.now()
                }
            }

            // Update unit progress if exercise was completed
            if (answerStatus == AnswerStatus.CORRECT) {
                updateUnitProgress(userId, lang, moduleId, unitId)
                updateModuleProgress(userId, lang, moduleId)
            }

            SubmitExerciseResponse(
                success = true,
                answerStatus = answerStatus,
                correctAnswer = "", // We could fetch this from the exercise data if needed
                explanation = null
            )
        }
    }

    private fun updateUnitProgress(userId: String, lang: String, moduleId: String, unitId: String) {
        // Implementation would calculate unit progress based on completed exercises
        // This is a simplified version
    }

    private fun updateModuleProgress(userId: String, lang: String, moduleId: String) {
        // Implementation would calculate module progress based on completed units
        // This is a simplified version
    }
}
