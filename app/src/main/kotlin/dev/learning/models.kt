package dev.learning

import kotlinx.serialization.Serializable

// Level models
@Serializable
data class Level(
    val id: String,
    val title: Map<String, String>,
    val phrases: List<Phrase>
)

@Serializable
data class Phrase(
    val id: String,
    val text: Map<String, String>
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
    val onboardingPhase: String
)

@Serializable
data class UpdateUserSettingsRequest(
    val nativeLanguage: String? = null,
    val targetLanguage: String? = null,
    val darkMode: Boolean? = null,
    val onboardingPhase: String? = null
)

// Response for settings update that includes warnings
@Serializable
data class UpdateUserSettingsResponse(
    val settings: UserSettingsResponse,
    val warnings: List<String> = emptyList()
)
