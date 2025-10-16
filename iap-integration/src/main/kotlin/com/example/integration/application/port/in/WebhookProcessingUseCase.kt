package com.example.integration.application.port.`in`

import com.example.integration.domain.Platform
import com.example.integration.domain.WebhookNotification

/**
 * 웹훅 처리 Use Case (입력 포트)
 */
interface WebhookProcessingUseCase {
    
    /**
     * 웹훅 알림 처리
     */
    fun processWebhook(platform: Platform, payload: String): WebhookProcessingResult
    
    /**
     * 실패한 웹훅 재처리
     */
    fun retryFailedWebhooks(): WebhookRetryResult
}

/**
 * 웹훅 처리 결과
 */
data class WebhookProcessingResult(
    val success: Boolean,
    val webhookNotification: WebhookNotification?,
    val message: String? = null,
    val errorMessage: String? = null
)

/**
 * 웹훅 재처리 결과
 */
data class WebhookRetryResult(
    val processedCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int
)