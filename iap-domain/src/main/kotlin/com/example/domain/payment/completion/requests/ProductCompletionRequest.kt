package com.example.domain.payment.completion.requests

import com.example.domain.Platform

data class ProductCompletionRequest(
    val platform: Platform,
    val packageName: String? = null,        // Google Play only
    val productId: String,
    val purchaseToken: String,
    val originalTransactionId: String? = null, // App Store only
    val userId: String,
    val developerPayload: String? = null
)