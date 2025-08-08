package dev.learning

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

// Answer status for exercise submissions
@Serializable
enum class AnswerStatus {
    CORRECT,
    INCORRECT,
    SKIPPED,
    REVEALED
}

// New hierarchical structure: Module → Unit → Exercise

// Module models
@Serializable
data class ModuleResponse(
    val id: String,
    val title: Map<String, String>,
    val description: Map<String, String>,
    val level: String, // A1, A2, B1, etc.
    val totalUnits: Int,
    val completedUnits: Int,
    val status: String // "available", "in_progress", "completed", "locked"
)

@Serializable
data class ModuleDetailResponse(
    val id: String,
    val title: Map<String, String>,
    val description: Map<String, String>,
    val level: String,
    val units: List<UnitSummary>
)

@Serializable
data class ModuleMeta(
    val id: String,
    val title: Map<String, String>,
    val description: Map<String, String>,
    val level: String,
    val order: Int = 0
)

// Unit models
@Serializable
data class UnitSummary(
    val id: String,
    val title: Map<String, String>,
    val description: Map<String, String>? = null,
    val totalExercises: Int,
    val completedExercises: Int,
    val status: String // "available", "in_progress", "completed", "locked"
)

@Serializable
data class UnitDetailResponse(
    val id: String,
    val title: Map<String, String>,
    val description: Map<String, String>? = null,
    val tip: Map<String, String>? = null,
    val exercises: List<ExerciseSummary>
)

// Exercise models
@Serializable
data class ExerciseSummary(
    val id: String,
    val type: String, // "translation", "fill-in-the-blank", "multiple-choice"
    val status: String, // "available", "completed"
    val isCorrect: Boolean? = null // null if not attempted, true/false if attempted
)

@Serializable
data class Exercise(
    val id: String,
    val type: String, // "translation", "fill-in-the-blank", "multiple-choice"
    val prompt: ExercisePrompt,
    val solution: String,
    val options: List<String>? = null, // For multiple-choice
    val tip: Map<String, String>? = null // Educational tip for this exercise
)

@Serializable(with = ExercisePromptSerializer::class)
data class ExercisePrompt(
    val en: String? = null,
    val es: String? = null
) {
    // Constructor for simple string prompts (backward compatibility)
    constructor(text: String) : this(en = text, es = null)
    
    // Get text for a specific language, fallback to first available
    fun getText(language: String = "en"): String {
        return when (language) {
            "en" -> en ?: es ?: ""
            "es" -> es ?: en ?: ""
            else -> en ?: es ?: ""
        }
    }
}

object ExercisePromptSerializer : KSerializer<ExercisePrompt> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ExercisePrompt") {
        element<String>("text", isOptional = true)
        element<String>("en", isOptional = true) 
        element<String>("es", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: ExercisePrompt) {
        val jsonEncoder = encoder as JsonEncoder
        val element = if (value.es != null || value.en != null) {
            buildJsonObject {
                value.en?.let { put("en", it) }
                value.es?.let { put("es", it) }
            }
        } else {
            JsonPrimitive(value.en ?: "")
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): ExercisePrompt {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        
        return when (element) {
            is JsonPrimitive -> ExercisePrompt(text = element.content)
            is JsonObject -> {
                val en = element["en"]?.jsonPrimitive?.content
                val es = element["es"]?.jsonPrimitive?.content
                ExercisePrompt(en = en, es = es)
            }
            else -> throw SerializationException("Invalid prompt format")
        }
    }
}

// Legacy models for backward compatibility (will be removed later)
@Serializable
data class Level(
    val id: String? = null,
    val title: Map<String, String>,
    val description: Map<String, String>? = null,
    val tip: Map<String, String>? = null,
    val level: String? = null,
    val targetLanguage: String? = null,
    val phrases: List<Phrase>? = null,
    val exercises: List<Exercise>? = null
)

@Serializable
data class Phrase(
    val id: String,
    val text: Map<String, String>
)

// User progress models
@Serializable
data class UnitProgress(
    val unitId: String,
    val completedExercises: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val totalExercises: Int,
    val lastAttempted: String? = null // ISO 8601 timestamp
)

@Serializable
data class ExerciseProgress(
    val exerciseId: String,
    val isCompleted: Boolean,
    val isCorrect: Boolean? = null,
    val attempts: Int = 0,
    val lastAttempted: String? = null
)

@Serializable
data class SubmitExerciseRequest(
    val userAnswer: String,
    val answerStatus: AnswerStatus
)

@Serializable
data class SubmitExerciseResponse(
    val success: Boolean,
    val answerStatus: AnswerStatus,
    val correctAnswer: String,
    val explanation: Map<String, String>? = null,
    val progress: UnitProgress? = null
)

@Serializable
data class SubmitResult(
    val success: Boolean,
    val progress: UnitProgress? = null
)

// User settings models
@Serializable
data class UserSettingsResponse(
    val userId: String,
    val nativeLanguage: String,
    val targetLanguage: String,
    val darkMode: Boolean,
    val onboardingStep: String,
    val userLevel: String? = null // A1, A2, B1, B2, C1, C2
)

@Serializable
data class UpdateUserSettingsRequest(
    val nativeLanguage: String? = null,
    val targetLanguage: String? = null,
    val darkMode: Boolean? = null,
    val onboardingStep: String? = null,
    val userLevel: String? = null // A1, A2, B1, B2, C1, C2
)

@Serializable
data class UpdateUserSettingsResponse(
    val settings: UserSettingsResponse,
    val warnings: List<String> = emptyList()
)

// User management models
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

// Onboarding steps enum
@Serializable
enum class OnboardingStep {
    NATIVE,      // User selects native language
    LEARNING,    // User selects target language  
    LEVEL,       // User selects initial level
    COMPLETE     // Onboarding completed
}

// Legacy models that will be removed
@Serializable
data class LevelOverviewResponse(
    val levels: List<LevelSummary>
)

@Serializable
data class LevelSummary(
    val level: String,
    val title: Map<String, String>,
    val description: Map<String, String>? = null,
    val progress: LevelProgress,
    val status: String,
    val difficulty: String? = null
)

@Serializable
data class CategoryConfig(
    val id: String,
    val path: String,
    val difficulty: List<String>,
    val title: Map<String, String>,
    val description: Map<String, String>
)

@Serializable
data class LevelProgress(
    val completedTopics: Int,
    val totalTopics: Int
)

@Serializable
data class LevelTopicsResponse(
    val level: String,
    val topics: List<TopicSummary>
)

@Serializable
data class TopicSummary(
    val id: String,
    val title: Map<String, String>,
    val description: Map<String, String>? = null,
    val tip: Map<String, String>? = null,
    val status: String,
    val progress: TopicProgress? = null,
    val lockedReason: Map<String, String>? = null
)

@Serializable
data class TopicProgress(
    val completedExercises: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val totalExercises: Int,
    val lastAttempted: String? = null
)

@Serializable
data class ExerciseResponse(
    val id: String,
    val topicId: String,
    val type: String,
    val prompt: ExercisePrompt,
    val solution: String? = null,
    val options: List<String>? = null,
    val previousAttempts: Int = 0,
    val isCompleted: Boolean = false,
    val tip: Map<String, String>? = null
)

@Serializable
data class NextExerciseResponse(
    val exercise: ExerciseResponse? = null,
    val hasMoreExercises: Boolean,
    val message: String? = null
)

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

@Serializable
data class SubmitAnswerRequest(
    val targetLanguage: String,
    val levelId: String,
    val topicId: String,
    val exerciseId: String,
    val userAnswer: String,
    val answerStatus: AnswerStatus
)

@Serializable
data class SubmitAnswerResponse(
    val success: Boolean,
    val answerStatus: AnswerStatus,
    val correctAnswer: String,
    val explanation: Map<String, String>? = null,
    val progress: TopicProgress
)
