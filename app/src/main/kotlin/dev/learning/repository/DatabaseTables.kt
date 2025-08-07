package dev.learning.repository

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// Legacy tables for the old category/level system
object UserProgress : UUIDTable("user_progress") {
    val userId = varchar("user_id", 50)
    val lang = varchar("lang", 2)
    val category = varchar("category", 50)
    val level = integer("level")
    val exerciseIndex = integer("exercise_index")
    val isCompleted = bool("is_completed").default(false)
    val completedAt = timestamp("completed_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
}

object TopicProgressTable : UUIDTable("topic_progress") {
    val userId = varchar("user_id", 50)
    val lang = varchar("lang", 2)
    val category = varchar("category", 50)
    val level = integer("level")
    val topic = varchar("topic", 100)
    val isCompleted = bool("is_completed").default(false)
    val completedAt = timestamp("completed_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
}

// New tables for the Module → Unit → Exercise hierarchy
object ModuleProgressTable : UUIDTable("module_progress") {
    val userId = varchar("user_id", 50)
    val lang = varchar("lang", 2)
    val moduleId = varchar("module_id", 100)
    val isCompleted = bool("is_completed").default(false)
    val progressPercentage = integer("progress_percentage").default(0)
    val completedAt = timestamp("completed_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
}

object UnitProgressTable : UUIDTable("unit_progress") {
    val userId = varchar("user_id", 50)
    val lang = varchar("lang", 2) 
    val moduleId = varchar("module_id", 100)
    val unitId = varchar("unit_id", 100)
    val isCompleted = bool("is_completed").default(false)
    val progressPercentage = integer("progress_percentage").default(0)
    val completedAt = timestamp("completed_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
    
    // Unique constraint to prevent duplicate entries
    init {
        uniqueIndex(userId, lang, moduleId, unitId)
    }
}

object ExerciseProgressTable : UUIDTable("exercise_progress") {
    val userId = varchar("user_id", 50)
    val lang = varchar("lang", 2)
    val moduleId = varchar("module_id", 100)
    val unitId = varchar("unit_id", 100)
    val exerciseId = varchar("exercise_id", 100)
    val isCompleted = bool("is_completed").default(false)
    val completedAt = timestamp("completed_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
    
    // Unique constraint to prevent duplicate entries
    init {
        uniqueIndex(userId, lang, moduleId, unitId, exerciseId)
    }
}

// Shared tables used by both old and new systems
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

object ExerciseAttempts : UUIDTable("exercise_attempts") {
    val userId = varchar("user_id", 50)
    val lang = varchar("lang", 2)
    val exerciseId = varchar("exercise_id", 100) // Can be legacy or new format
    val moduleId = varchar("module_id", 100).nullable() // Only for new system
    val unitId = varchar("unit_id", 100).nullable() // Only for new system
    val userAnswer = text("user_answer")
    val isCorrect = bool("is_correct")
    val attemptedAt = timestamp("attempted_at").clientDefault { Clock.System.now() }
}
