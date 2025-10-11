package com.example.domain

import java.time.LocalDateTime

enum class PaymentEventType {
    PURCHASE,
    RENEWAL,
    CANCELLATION,
    REFUND,
    EXPIRATION,
    GRACE_PERIOD_START,
    GRACE_PERIOD_END,
    PAUSE,
    RESUME
}

data class PaymentEvent(
    val id: String,
    val subscriptionId: String,
    val paymentId: String? = null,
    val eventType: PaymentEventType,
    val platform: Platform,
    val eventData: Map<String, Any> = emptyMap(),
    val processedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun markAsProcessed(): PaymentEvent = copy(processedAt = LocalDateTime.now())
    
    fun isProcessed(): Boolean = processedAt != null
}