package com.example.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class GooglePlayRTDNRequest(
    @JsonProperty("message")
    val message: PubSubMessage,
    
    @JsonProperty("subscription")
    val subscription: String
)

data class PubSubMessage(
    @JsonProperty("data")
    val data: String,  // Base64 encoded JSON
    
    @JsonProperty("messageId")
    val messageId: String,
    
    @JsonProperty("publishTime")
    val publishTime: String,
    
    @JsonProperty("attributes")
    val attributes: Map<String, String> = emptyMap()
)

data class RTDNNotification(
    @JsonProperty("version")
    val version: String,
    
    @JsonProperty("packageName")
    val packageName: String,
    
    @JsonProperty("eventTimeMillis")
    val eventTimeMillis: Long,
    
    @JsonProperty("subscriptionNotification")
    val subscriptionNotification: SubscriptionNotification? = null,
    
    @JsonProperty("oneTimeProductNotification")
    val oneTimeProductNotification: OneTimeProductNotification? = null,
    
    @JsonProperty("testNotification")
    val testNotification: TestNotification? = null
)

data class SubscriptionNotification(
    @JsonProperty("version")
    val version: String,
    
    @JsonProperty("notificationType")
    val notificationType: Int,  // 1=RECOVERED, 2=RENEWED, 3=CANCELED, 4=PURCHASED, etc.
    
    @JsonProperty("purchaseToken")
    val purchaseToken: String,
    
    @JsonProperty("subscriptionId")
    val subscriptionId: String
)

data class OneTimeProductNotification(
    @JsonProperty("version")
    val version: String,
    
    @JsonProperty("notificationType")
    val notificationType: Int,
    
    @JsonProperty("purchaseToken")
    val purchaseToken: String,
    
    @JsonProperty("sku")
    val sku: String
)

data class TestNotification(
    @JsonProperty("version")
    val version: String
)