package com.example.integration.infrastructure.scheduler

import com.example.integration.application.port.`in`.WebhookProcessingUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 실패한 웹훅 자동 재처리 스케줄러 (Infrastructure Layer)
 */
@Component
class WebhookRetryScheduler(
    private val webhookProcessingUseCase: WebhookProcessingUseCase
) {
    
    private val logger = LoggerFactory.getLogger(WebhookRetryScheduler::class.java)
    
    /**
     * 실패한 웹훅을 5분마다 자동으로 재처리
     */
    @Scheduled(fixedDelay = 300000) // 5분 = 300,000ms
    fun retryFailedWebhooks() {
        try {
            logger.info("Starting scheduled webhook retry process")
            
            val result = webhookProcessingUseCase.retryFailedWebhooks()
            
            if (result.processedCount > 0) {
                logger.info("Webhook retry completed: processed=${result.processedCount}, " +
                           "success=${result.successCount}, failed=${result.failedCount}")
            }
            
        } catch (e: Exception) {
            logger.error("Failed to execute scheduled webhook retry", e)
        }
    }
}