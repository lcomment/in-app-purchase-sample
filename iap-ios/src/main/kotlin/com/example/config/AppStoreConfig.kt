package com.example.config

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.*

@Configuration
class AppStoreConfig {

    @Value("\${apple.app-store.key-id}")
    private lateinit var keyId: String

    @Value("\${apple.app-store.issuer-id}")
    private lateinit var issuerId: String

    @Value("\${apple.app-store.private-key}")
    private lateinit var privateKey: String

    @Value("\${apple.app-store.bundle-id}")
    private lateinit var bundleId: String

    @Bean
    fun appStoreJwtToken(): String {
        return generateJWT()
    }

    private fun generateJWT(): String {
        val privateKeyBytes = Base64.getDecoder().decode(
            privateKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
        )

        val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        val ecPrivateKey = keyFactory.generatePrivate(keySpec) as ECPrivateKey

        val now = Instant.now()
        val expiresAt = now.plusSeconds(3600) // 1 hour

        return Jwts.builder()
            .setHeaderParam("kid", keyId)
            .setIssuer(issuerId)
            .setAudience("appstoreconnect-v1")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiresAt))
            .signWith(ecPrivateKey, SignatureAlgorithm.ES256)
            .compact()
    }

    fun getBundleId(): String = bundleId
}