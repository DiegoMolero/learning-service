package dev.learning

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val environmentName: String,
    val serverPort: Int,
    val tokenExpirationMinutes: Int,
    val jwtSecret: String,
    val realm: String,
    val issuer: String, 
    var domain: String,
    val database: DatabaseConfig
)

@Serializable
data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val dropOnStart: Boolean = false
)
