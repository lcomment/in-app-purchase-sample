package com.example.integration.domain

import java.time.LocalDateTime

data class WebhookNotification(
    val id: Long = 0,
    val platform: Platform,
    val notificationType: WebhookNotificationType,
    val subscriptionId: String?,
    val productId: String?,
    val purchaseToken: String?,
    val orderId: String?,
    val originalData: String, // JSON 원본 데이터
    val status: WebhookProcessingStatus,
    val processedAt: LocalDateTime?,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun markAsProcessed(): WebhookNotification = copy(
        status = WebhookProcessingStatus.PROCESSED,
        processedAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )
    
    fun markAsFailed(error: String): WebhookNotification = copy(
        status = WebhookProcessingStatus.FAILED,
        errorMessage = error,
        updatedAt = LocalDateTime.now()
    )
    
    fun incrementRetryCount(): WebhookNotification = copy(
        retryCount = retryCount + 1,
        status = WebhookProcessingStatus.PENDING,
        updatedAt = LocalDateTime.now()
    )
}