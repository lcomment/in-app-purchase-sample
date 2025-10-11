package com.example.dto

data class IOSSubscriptionVerificationRequest(
    val bundleId: String,
    val transactionId: String,
    val originalTransactionId: String,
    val productId: String,
    val userId: String,
    val receiptData: String? = null // Base64 encoded receipt data (legacy)
)