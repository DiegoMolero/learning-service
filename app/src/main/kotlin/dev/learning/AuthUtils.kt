package dev.learning

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import java.util.*

object AuthUtils {

    /**
     * Decode and verify a JWT token. Returns null if invalid or expired.
     */
    fun verifyToken(secret: String, issuer: String, token: String): DecodedJWT? {
        return try {
            val algorithm = Algorithm.HMAC256(secret)
            val verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .build()
            
            verifier.verify(token)
        } catch (e: JWTVerificationException) {
            println("Token verification failed: ${e.message}")
            null
        }
    }

    /**
     * Extract user ID from JWT token subject
     */
    fun getUserIdFromToken(token: String): String? {
        return try {
            val decodedJWT = JWT.decode(token)
            decodedJWT.subject
        } catch (e: Exception) {
            null
        }
    }
}
