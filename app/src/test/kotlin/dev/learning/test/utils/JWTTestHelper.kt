package dev.learning.test.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.learning.Config
import java.util.*

/**
 * Utility class for creating JWT tokens in tests
 */
object JWTTestHelper {
    
    /**
     * Creates a valid JWT token for testing purposes
     * @param config The application configuration containing JWT settings
     * @param email The email to include in the token claims
     * @param userId The user ID to use as the subject (must be a valid UUID)
     * @return A Bearer token string ready to use in Authorization headers
     */
    fun createTestJWT(
        config: Config,
        email: String = "test@example.com", 
        userId: String = "550e8400-e29b-41d4-a716-446655440000"
    ): String {
        val algorithm = Algorithm.HMAC256(config.jwtSecret)
        val token = JWT.create()
            .withIssuer(config.issuer)
            .withSubject(userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + (config.tokenExpirationMinutes * 60 * 1000)))
            .withIssuedAt(Date())
            .sign(algorithm)
        
        return "Bearer $token"
    }
    
    /**
     * Creates a JWT token with custom expiration time
     * @param config The application configuration containing JWT settings
     * @param email The email to include in the token claims
     * @param userId The user ID to use as the subject (must be a valid UUID)
     * @param expirationMinutes Custom expiration time in minutes
     * @return A Bearer token string ready to use in Authorization headers
     */
    fun createTestJWTWithExpiration(
        config: Config,
        email: String = "test@example.com",
        userId: String = "550e8400-e29b-41d4-a716-446655440000",
        expirationMinutes: Int
    ): String {
        val algorithm = Algorithm.HMAC256(config.jwtSecret)
        val token = JWT.create()
            .withIssuer(config.issuer)
            .withSubject(userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + (expirationMinutes * 60 * 1000)))
            .withIssuedAt(Date())
            .sign(algorithm)
        
        return "Bearer $token"
    }
    
    /**
     * Creates an expired JWT token for testing authentication failures
     * @param config The application configuration containing JWT settings
     * @param email The email to include in the token claims
     * @param userId The user ID to use as the subject (must be a valid UUID)
     * @return An expired Bearer token string
     */
    fun createExpiredTestJWT(
        config: Config,
        email: String = "test@example.com",
        userId: String = "550e8400-e29b-41d4-a716-446655440000"
    ): String {
        val algorithm = Algorithm.HMAC256(config.jwtSecret)
        val token = JWT.create()
            .withIssuer(config.issuer)
            .withSubject(userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() - 3600000)) // Expired 1 hour ago
            .withIssuedAt(Date(System.currentTimeMillis() - 7200000)) // Issued 2 hours ago
            .sign(algorithm)
        
        return "Bearer $token"
    }
    
    /**
     * Verifies and decodes a JWT token
     * @param config The application configuration containing JWT settings
     * @param bearerToken The Bearer token to verify (with "Bearer " prefix)
     * @return The decoded JWT
     */
    fun verifyTestJWT(config: Config, bearerToken: String): com.auth0.jwt.interfaces.DecodedJWT {
        val token = bearerToken.removePrefix("Bearer ")
        val algorithm = Algorithm.HMAC256(config.jwtSecret)
        val verifier = JWT.require(algorithm)
            .withIssuer(config.issuer)
            .build()
        
        return verifier.verify(token)
    }
}
