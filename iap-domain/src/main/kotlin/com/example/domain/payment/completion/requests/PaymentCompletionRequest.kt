package com.example.domain.payment.completion.requests

import com.example.domain.Platform

data class PaymentCompletionRequest(
    val platform: Platform,
    val transactionId: String,
    val originalTransactionId: String? = null,
    val userId: String,
    val productId: String,
    val purchaseToken: String? = null,      // Google Play only
    val packageName: String? = null,        // Google Play only
    val developerPayload: String? = null
)