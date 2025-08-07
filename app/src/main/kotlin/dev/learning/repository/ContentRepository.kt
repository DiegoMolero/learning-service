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
