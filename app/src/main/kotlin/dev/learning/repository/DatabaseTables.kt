package dev.learning.repository

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

object ExerciseProgressTable : UUIDTable("exercise_results") {
    val userId = varchar("user_id", 50)
    val lang = varchar("lang", 2) 
    val moduleId = varchar("module_id", 100)
    val unitId = varchar("unit_id", 100)
    val exerciseId = varchar("exercise_id", 100)
    val userAnswer = text("user_answer")
    val answerStatus = varchar("answer_status", 20) // CORRECT, INCORRECT, SKIPPED, REVEALED
    val attemptedAt = timestamp("attempted_at").clientDefault { Clock.System.now() }
    
    // Allow multiple attempts per exercise
    init {
        index(false, userId, lang, moduleId, unitId, exerciseId)
    }
}

object UserSettings : UUIDTable("user_settings") {
    val userId = varchar("user_id", 50)
    val lang = varchar("lang", 2)
    val settings = text("settings") // JSON string
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
    
    // Unique constraint to ensure only one settings record per user
    init {
        uniqueIndex(userId)
    }
}
