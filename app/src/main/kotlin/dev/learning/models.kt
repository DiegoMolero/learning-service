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

// Level models
@Serializable
data class Level(
    val id: String? = null, // Optional for backward compatibility
    val title: Map<String, String>,
    val description: Map<String, String>? = null,
    val tip: Map<String, String>? = null, // Optional tip for the topic
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
    val description: Map<String, String>? = null,
    val tip: Map<String, String>? = null,
    val status: String, // "locked", "in_progress", "completed"
    val progress: TopicProgress? = null,
    val lockedReason: Map<String, String>? = null
)

@Serializable
data class TopicProgress(
    val completedExercises: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val totalExercises: Int,
    val lastAttempted: String? = null // ISO 8601 timestamp
)

// Exercise detail response
@Serializable
data class ExerciseResponse(
    val id: String,
    val topicId: String,
    val type: String,
    val prompt: ExercisePrompt,
    val solution: String? = null, // Only include in practice mode
    val options: List<String>? = null, // For multiple-choice
    val previousAttempts: Int = 0,
    val isCompleted: Boolean = false,
    val tip: Map<String, String>? = null // Educational tip for this exercise
)

@Serializable
data class NextExerciseResponse(
    val exercise: ExerciseResponse? = null,
    val hasMoreExercises: Boolean,
    val message: String? = null
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

// Exercise answer submission models
@Serializable
enum class AnswerStatus {
    CORRECT,     // Usuario respondió correctamente
    INCORRECT,   // Usuario respondió incorrectamente
    SKIPPED,     // Usuario saltó la pregunta
    REVEALED     // Usuario pidió ver la respuesta sin intentar
}

@Serializable
data class SubmitAnswerRequest(
    val targetLanguage: String,
    val level: String,
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
    val explanation: Map<String, String>? = null, // Educational tip
    val progress: TopicProgress
)
