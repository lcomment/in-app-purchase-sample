package com.example.domain.payment.completion.requests

import com.example.domain.Platform

data class SubscriptionCompletionRequest(
    val platform: Platform,
    val packageName: String? = null,        // Google Play only
    val subscriptionId: String,
    val purchaseToken: String,
    val originalTransactionId: String? = null, // App Store only
    val userId: String,
    val developerPayload: String? = null
)