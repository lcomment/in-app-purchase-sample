package com.example.dto

import com.example.domain.SubscriptionStatus
import java.time.LocalDateTime

data class SubscriptionVerificationResponse(
    val subscriptionId: String,
    val userId: String,
    val status: SubscriptionStatus,
    val isValid: Boolean,
    val startTime: LocalDateTime?,
    val expiryTime: LocalDateTime?,
    val autoRenewing: Boolean,
    val orderId: String?,
    val message: String
)