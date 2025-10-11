package com.example.dto

import com.example.domain.SubscriptionStatus
import java.time.LocalDateTime

data class IOSSubscriptionVerificationResponse(
    val subscriptionId: String,
    val userId: String,
    val status: SubscriptionStatus,
    val isValid: Boolean,
    val startTime: LocalDateTime?,
    val expiryTime: LocalDateTime?,
    val autoRenewing: Boolean,
    val transactionId: String?,
    val originalTransactionId: String?,
    val message: String
)