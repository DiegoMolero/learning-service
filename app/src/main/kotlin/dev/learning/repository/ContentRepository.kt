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
import dev.learning.repository.ExerciseProgressTable
import dev.learning.repository.UserSettings

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
    
    private val contentLibrary = ContentLibrary()
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
                    ExerciseProgressTable,
                    UserSettings
                )
            }
            SchemaUtils.createMissingTablesAndColumns(
                ExerciseProgressTable,
                UserSettings
            )
        }
    }

    override suspend fun getModules(userId: String, lang: String): List<ModuleResponse> {
        val modules = contentLibrary.getModules(lang)
        
        return transaction {
            modules.map { module ->
                // Get all units for this module to calculate totals
                val units = contentLibrary.getUnits(lang, module.moduleId)
                val totalUnits = units.size
                
                // Count completed units for this module
                var completedUnits = 0
                units.forEach { unit ->
                    val unitContent = contentLibrary.getUnitContent(lang, module.moduleId, unit.unitId)
                    if (unitContent != null) {
                        val totalExercises = unitContent.exercises.size
                        
                        // Get correct answers for this unit
                        val correctAnswers = ExerciseProgressTable.select {
                            (ExerciseProgressTable.userId eq userId) and
                            (ExerciseProgressTable.lang eq lang) and
                            (ExerciseProgressTable.moduleId eq module.moduleId) and
                            (ExerciseProgressTable.unitId eq unit.unitId) and
                            (ExerciseProgressTable.answerStatus eq "CORRECT")
                        }.withDistinct().count()
                        
                        // Unit is completed if all exercises are correct
                        if (correctAnswers >= totalExercises) {
                            completedUnits++
                        }
                    }
                }

                ModuleResponse(
                    id = module.moduleId,
                    title = module.title,
                    description = module.description,
                    level = module.difficulty.firstOrNull() ?: "A1",
                    totalUnits = totalUnits,
                    completedUnits = completedUnits,
                    status = if (completedUnits >= totalUnits) "completed" else "available"
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
                // Get real exercise count from content
                val unitContent = contentLibrary.getUnitContent(lang, moduleId, unit.unitId)
                val totalExercises = unitContent?.exercises?.size ?: 0
                
                // Get all distinct correct answers for this unit
                val correctAnswers = ExerciseProgressTable.select {
                    (ExerciseProgressTable.userId eq userId) and
                    (ExerciseProgressTable.lang eq lang) and
                    (ExerciseProgressTable.moduleId eq moduleId) and
                    (ExerciseProgressTable.unitId eq unit.unitId) and
                    (ExerciseProgressTable.answerStatus eq "CORRECT")
                }.withDistinct().count().toInt()

                UnitSummary(
                    id = unit.unitId,
                    title = unit.title,
                    description = unit.description,
                    totalExercises = totalExercises,
                    completedExercises = correctAnswers,
                    status = if (correctAnswers >= totalExercises && totalExercises > 0) "completed" else "available"
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
                // Get real exercise count from content
                val unitContent = contentLibrary.getUnitContent(lang, moduleId, unit.unitId)
                val totalExercises = unitContent?.exercises?.size ?: 0
                
                // Get all distinct correct answers for this unit
                val correctAnswers = ExerciseProgressTable.select {
                    (ExerciseProgressTable.userId eq userId) and
                    (ExerciseProgressTable.lang eq lang) and
                    (ExerciseProgressTable.moduleId eq moduleId) and
                    (ExerciseProgressTable.unitId eq unit.unitId) and
                    (ExerciseProgressTable.answerStatus eq "CORRECT")
                }.withDistinct().count().toInt()

                UnitSummary(
                    id = unit.unitId,
                    title = unit.title,
                    description = unit.description,
                    totalExercises = totalExercises,
                    completedExercises = correctAnswers,
                    status = if (correctAnswers >= totalExercises && totalExercises > 0) "completed" else "available"
                )
            }
        }
    }

    override suspend fun getUnit(userId: String, lang: String, moduleId: String, unitId: String): UnitDetailResponse? {
        val unitContent = contentLibrary.getUnitContent(lang, moduleId, unitId) ?: return null
        
        return transaction {
            val exercisesWithProgress = unitContent.exercises.map { exercise ->
                ExerciseSummary(
                    id = exercise.id,
                    type = exercise.type,
                    status = "available", // Simplified - no individual exercise tracking
                    isCorrect = null
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
                ExerciseSummary(
                    id = exercise.id,
                    type = exercise.type,
                    status = "available", // Simplified - no individual exercise tracking
                    isCorrect = null
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
            // Simply record the exercise result
            ExerciseProgressTable.insert {
                it[this.userId] = userId
                it[this.lang] = lang
                it[this.moduleId] = moduleId
                it[this.unitId] = unitId
                it[this.exerciseId] = exerciseId
                it[this.userAnswer] = userAnswer
                it[this.answerStatus] = answerStatus.name
                it[this.attemptedAt] = kotlinx.datetime.Clock.System.now()
            }

            // Get the correct answer from the exercise if available
            val unitContent = contentLibrary.getUnitContent(lang, moduleId, unitId)
            val exercise = unitContent?.exercises?.find { it.id == exerciseId }
            val correctAnswer = exercise?.solution ?: ""

            SubmitExerciseResponse(
                success = true,
                answerStatus = answerStatus,
                correctAnswer = correctAnswer,
                explanation = exercise?.tip
            )
        }
    }
}