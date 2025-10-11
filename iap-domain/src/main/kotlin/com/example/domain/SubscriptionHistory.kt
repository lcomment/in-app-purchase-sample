package com.example.domain

import java.time.LocalDateTime

data class SubscriptionHistory(
    val id: String,
    val subscriptionId: String,
    val previousStatus: SubscriptionStatus,
    val newStatus: SubscriptionStatus,
    val reason: String,
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: LocalDateTime = LocalDateTime.now()
)