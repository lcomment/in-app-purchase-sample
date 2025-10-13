package com.example.domain.payment.event

import com.example.domain.Platform
import java.time.LocalDateTime

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