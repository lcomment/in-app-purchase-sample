package com.example.integration.infrastructure.persistence

import com.example.integration.application.port.out.WebhookNotificationRepositoryPort
import com.example.integration.domain.WebhookNotification
import com.example.integration.domain.WebhookProcessingStatus
import com.example.integration.domain.Platform
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 웹훅 알림 In-Memory Repository 구현체 (Infrastructure Layer)
 */
@Repository
class InMemoryWebhookNotificationRepository : WebhookNotificationRepositoryPort {
    
    private val webhookNotifications = ConcurrentHashMap<Long, WebhookNotification>()
    private val idGenerator = AtomicLong(1)
    
    override fun save(webhookNotification: WebhookNotification): WebhookNotification {
        val id = if (webhookNotification.id == 0L) {
            idGenerator.getAndIncrement()
        } else {
            webhookNotification.id
        }
        
        val savedNotification = if (webhookNotification.id == 0L) {
            webhookNotification.copy(id = id)
        } else {
            webhookNotification
        }
        
        webhookNotifications[id] = savedNotification
        return savedNotification
    }
    
    override fun findById(id: Long): WebhookNotification? {
        return webhookNotifications[id]
    }
    
    override fun existsByPlatformAndPurchaseTokenAndNotificationType(
        platform: Platform,
        purchaseToken: String,
        notificationType: String
    ): Boolean {
        return webhookNotifications.values.any { notification ->
            notification.platform == platform &&
            notification.purchaseToken == purchaseToken &&
            notification.notificationType.name == notificationType &&
            notification.status != WebhookProcessingStatus.FAILED
        }
    }
    
    override fun findAllByStatus(status: WebhookProcessingStatus): List<WebhookNotification> {
        return webhookNotifications.values
            .filter { it.status == status }
            .sortedBy { it.createdAt }
    }
    
    override fun findFailedWebhooksForRetry(maxRetryCount: Int): List<WebhookNotification> {
        return webhookNotifications.values
            .filter { 
                it.status == WebhookProcessingStatus.FAILED && 
                it.retryCount < maxRetryCount 
            }
            .sortedBy { it.createdAt }
    }
    
    override fun deleteById(id: Long) {
        webhookNotifications.remove(id)
    }
}