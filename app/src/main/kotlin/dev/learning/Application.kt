package dev.learning

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.callloging.CallLogging
import org.slf4j.event.Level
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.JWT
import dev.learning.routes.*
import dev.learning.repository.DatabaseUserRepository
import dev.learning.repository.UserRepository
import dev.learning.repository.DatabaseContentRepository
import dev.learning.ErrorTypes
import java.io.File
import java.io.FileNotFoundException
// Load cors plugin
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders


fun main() {
    val config = loadConfig()
    println("Load Config ${config.environmentName} from ${config.serverPort}")
    embeddedServer(Netty, port = config.serverPort) {
        module(config)
    }.start(wait = true)
}

fun loadConfig(env: String? = null): Config {
    val envName = env ?: System.getenv("APP_ENV") ?: "dev"
    println("Loading configuration for environment: $envName")

    // Load default config from resources
    val defaultStream = {}::class.java.getResource("/config.default.json")
        ?: throw FileNotFoundException("config.default.json not found in resources")
    val defaultJson = Json.parseToJsonElement(defaultStream.readText()) as JsonObject
    println("Loaded default config")

    // Load environment-specific config if it exists
    val envStream = {}::class.java.getResource("/config.$envName.json")
    val envJson = if (envStream != null) {
        Json.parseToJsonElement(envStream.readText()) as JsonObject
    } else {
        JsonObject(emptyMap())
    }

    // Merge configs
    val mergedJson = mergeJsonObjects(defaultJson, envJson)
    
    // Override with environment variables if they exist
    val finalJson = overrideWithEnvVars(mergedJson)

    return Json.decodeFromJsonElement(Config.serializer(), finalJson)
}

fun overrideWithEnvVars(configJson: JsonObject): JsonObject {
    val mutableConfig = configJson.toMutableMap()
    
    // Override server port if provided
    System.getenv("SERVER_PORT")?.let { 
        mutableConfig["serverPort"] = JsonPrimitive(it.toInt())
    }
    
    // Override JWT secret
    System.getenv("JWT_SECRET")?.let { 
        mutableConfig["jwtSecret"] = JsonPrimitive(it)
    }
    
    // Override database config
    val database = mutableMapOf<String, JsonElement>()
    System.getenv("DATABASE_URL")?.let { database["url"] = JsonPrimitive(it) }
    System.getenv("DATABASE_USER")?.let { database["user"] = JsonPrimitive(it) }
    System.getenv("DATABASE_PASSWORD")?.let { database["password"] = JsonPrimitive(it) }
    System.getenv("DATABASE_DROP_ON_START")?.let { 
        database["dropOnStart"] = JsonPrimitive(it.toBoolean()) 
    }
    
    // If any database env vars were found, update the database config
    if (database.isNotEmpty()) {
        mutableConfig["database"] = JsonObject(database)
    }
    
    return JsonObject(mutableConfig)
}

fun mergeJsonObjects(default: JsonObject, override: JsonObject): JsonObject {
    val merged = mutableMapOf<String, JsonElement>()
    
    // Start with default values
    for ((key, value) in default) {
        merged[key] = value
    }
    
    // Override with environment values
    for ((key, value) in override) {
        when {
            value is JsonObject && merged[key] is JsonObject -> {
                // Recursively merge nested objects
                merged[key] = mergeJsonObjects(merged[key] as JsonObject, value)
            }
            else -> {
                // Override primitive values and arrays
                merged[key] = value
            }
        }
    }
    
    return JsonObject(merged)
}

fun Application.configureCors(config: Config) {
    install(CORS) {
        if (config.environmentName == "dev") {
            anyHost() // only for development, allows all hosts
        } else {
            allowHost(config.domain, schemes = listOf("https"))
        }

        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)

        allowCredentials = true // Si usas cookies/token con credenciales
    }
}

fun Application.module(config: Config) {
    configureCors(config)

    // Use user repository for user management
    val userRepository: UserRepository = DatabaseUserRepository(config.database, config.environmentName)
    
    // Create dedicated content repository for new content system
    val contentRepository = DatabaseContentRepository(config.database, config.environmentName)

    if (config.environmentName == "dev") {
        install(CallLogging) {
            level = Level.INFO  // Puedes usar DEBUG para más detalles
            filter { call -> true } // Loguea todas las llamadas
        }
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.realm

            verifier(
                JWT
                    .require(Algorithm.HMAC256(config.jwtSecret))
                    .withIssuer(config.issuer)
                    .build()
            )

            validate { credential ->
                val subject = credential.payload.subject
                if (!subject.isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null // Reject the token
                }
            }

            challenge { _, _ ->
                val errorResponse = createErrorResponse(
                    type = ErrorTypes.INVALID_CREDENTIALS,
                    message = "Token is not valid or has expired. Please log in again.",
                    status = io.ktor.http.HttpStatusCode.Unauthorized.value,
                    path = call.request.uri
                )
                call.respond(
                    io.ktor.http.HttpStatusCode.Unauthorized,
                    errorResponse
                )
            }
        }
    }

    routing {
        healthRoute()
        contentRoute(contentRepository)
        
        settingsRoute(userRepository)
        
        // User management routes for auth service (X-Internal-Secret authentication required)
        userManagementRoute(userRepository, config)
    }

}
