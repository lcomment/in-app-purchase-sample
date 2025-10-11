package com.example.config

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream

@Configuration
class GooglePlayConfig {

    @Value("\${google.play.service-account-key}")
    private lateinit var serviceAccountKey: String

    @Value("\${google.play.application-name}")
    private lateinit var applicationName: String

    @Bean
    fun androidPublisher(): AndroidPublisher {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()

        val credentials = ServiceAccountCredentials
            .fromStream(ByteArrayInputStream(serviceAccountKey.toByteArray()))
            .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))

        val credentialsAdapter = HttpCredentialsAdapter(credentials)

        return AndroidPublisher.Builder(httpTransport, jsonFactory, credentialsAdapter)
            .setApplicationName(applicationName)
            .build()
    }
}