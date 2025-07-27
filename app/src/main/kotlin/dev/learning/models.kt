package dev.learning

import kotlinx.serialization.Serializable

// Level models
@Serializable
data class Level(
    val id: String? = null, // Optional for backward compatibility
    val title: Map<String, String>,
    val description: Map<String, String>? = null,
    val level: String? = null, // A1, A2, B1, etc.
    val targetLanguage: String? = null,
    val phrases: List<Phrase>? = null, // For backward compatibility
    val exercises: List<Exercise>? = null // New structure
)

@Serializable
data class Phrase(
    val id: String,
    val text: Map<String, String>
)

@Serializable
data class Exercise(
    val type: String, // "translation", "fill-in-the-blank", "multiple-choice"
    val prompt: String,
    val solution: String,
    val options: List<String>? = null // For multiple-choice
)

// Dashboard level overview models
@Serializable
data class LevelOverviewResponse(
    val levels: List<LevelSummary>
)

@Serializable
data class LevelSummary(
    val level: String, // A1, A2, B1, etc.
    val title: Map<String, String>,
    val progress: LevelProgress,
    val status: String // "locked", "in_progress", "completed"
)

@Serializable
data class LevelProgress(
    val completedTopics: Int,
    val totalTopics: Int
)

// Topic models for level detail view
@Serializable
data class LevelTopicsResponse(
    val level: String,
    val topics: List<TopicSummary>
)

@Serializable
data class TopicSummary(
    val id: String,
    val title: Map<String, String>,
    val status: String, // "locked", "in_progress", "completed"
    val progress: TopicProgress? = null,
    val lockedReason: Map<String, String>? = null
)

@Serializable
data class TopicProgress(
    val attemptedExercises: Int,
    val completedExercises: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val lastAttempted: String? = null // ISO 8601 timestamp
)

// User progress models
@Serializable
data class UserProgressResponse(
    val userId: String,
    val levelId: String,
    val completedPhraseIds: List<String>
)

@Serializable
data class UpdateProgressRequest(
    val levelId: String,
    val completedPhraseIds: List<String>
)

// User settings models
@Serializable
data class UserSettingsResponse(
    val userId: String,
    val nativeLanguage: String,
    val targetLanguage: String,
    val darkMode: Boolean,
    val onboardingStep: String
)

@Serializable
data class UpdateUserSettingsRequest(
    val nativeLanguage: String? = null,
    val targetLanguage: String? = null,
    val darkMode: Boolean? = null,
    val onboardingStep: String? = null
)

// Response for settings update that includes warnings
@Serializable
data class UpdateUserSettingsResponse(
    val settings: UserSettingsResponse,
    val warnings: List<String> = emptyList()
)

// User management models for auth service
@Serializable
data class CreateUserRequest(
    val userId: String
)

@Serializable
data class UserManagementResponse(
    val success: Boolean,
    val message: String,
    val userId: String? = null
)
