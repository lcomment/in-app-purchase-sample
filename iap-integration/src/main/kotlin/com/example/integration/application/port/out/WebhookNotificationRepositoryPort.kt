package com.example.integration.application.port.out

import com.example.integration.domain.WebhookNotification
import com.example.integration.domain.WebhookProcessingStatus
import com.example.integration.domain.Platform

/**
 * 웹훅 알림 Repository (출력 포트)
 */
interface WebhookNotificationRepositoryPort {
    
    /**
     * 웹훅 알림 저장
     */
    fun save(webhookNotification: WebhookNotification): WebhookNotification
    
    /**
     * 웹훅 알림 조회 (ID로)
     */
    fun findById(id: Long): WebhookNotification?
    
    /**
     * 중복 웹훅 확인 (플랫폼 + 구매토큰 + 알림타입으로)
     */
    fun existsByPlatformAndPurchaseTokenAndNotificationType(
        platform: Platform,
        purchaseToken: String,
        notificationType: String
    ): Boolean
    
    /**
     * 처리 상태별 웹훅 조회
     */
    fun findAllByStatus(status: WebhookProcessingStatus): List<WebhookNotification>
    
    /**
     * 실패한 웹훅 중 재시도 가능한 것들 조회
     */
    fun findFailedWebhooksForRetry(maxRetryCount: Int): List<WebhookNotification>
    
    /**
     * 웹훅 삭제
     */
    fun deleteById(id: Long)
}