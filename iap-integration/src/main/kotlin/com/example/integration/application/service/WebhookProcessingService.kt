package com.example.integration.application.service

import com.example.integration.application.port.`in`.WebhookProcessingUseCase
import com.example.integration.application.port.`in`.WebhookProcessingResult
import com.example.integration.application.port.`in`.WebhookRetryResult
import com.example.integration.application.port.out.WebhookNotificationRepositoryPort
import com.example.integration.application.port.out.MemberSubscriptionRepositoryPort
import com.example.integration.domain.*
import com.example.integration.infrastructure.webhook.WebhookHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 웹훅 처리 서비스 (Application Layer)
 */
@Service
@Transactional
class WebhookProcessingService(
    private val webhookHandlers: List<WebhookHandler>,
    private val webhookNotificationRepository: WebhookNotificationRepositoryPort,
    private val memberSubscriptionRepository: MemberSubscriptionRepositoryPort
) : WebhookProcessingUseCase {
    
    private val logger = LoggerFactory.getLogger(WebhookProcessingService::class.java)
    private val maxRetryCount = 3
    
    override fun processWebhook(platform: Platform, payload: String): WebhookProcessingResult {
        logger.info("Processing webhook for platform: $platform")
        
        try {
            // 적절한 핸들러 찾기
            val handler = webhookHandlers.find { it.canHandle(platform) }
                ?: return WebhookProcessingResult(
                    success = false,
                    webhookNotification = null,
                    errorMessage = "No handler found for platform: $platform"
                )
            
            // 웹훅 파싱
            val notification = handler.parseNotification(payload)
            
            // 중복 체크
            if (isDuplicateNotification(notification)) {
                logger.info("Duplicate webhook notification detected, skipping")
                val skippedNotification = notification.copy(status = WebhookProcessingStatus.SKIPPED)
                val savedNotification = webhookNotificationRepository.save(skippedNotification)
                
                return WebhookProcessingResult(
                    success = true,
                    webhookNotification = savedNotification,
                    message = "Duplicate notification skipped"
                )
            }
            
            // 웹훅 저장 (처리 중 상태로)
            val processingNotification = notification.copy(status = WebhookProcessingStatus.PROCESSING)
            val savedNotification = webhookNotificationRepository.save(processingNotification)
            
            // 실제 비즈니스 로직 처리
            val result = processNotificationLogic(savedNotification)
            
            return result
            
        } catch (e: Exception) {
            logger.error("Failed to process webhook", e)
            return WebhookProcessingResult(
                success = false,
                webhookNotification = null,
                errorMessage = "Webhook processing failed: ${e.message}"
            )
        }
    }
    
    override fun retryFailedWebhooks(): WebhookRetryResult {
        logger.info("Starting retry process for failed webhooks")
        
        val failedWebhooks = webhookNotificationRepository.findFailedWebhooksForRetry(maxRetryCount)
        var successCount = 0
        var failedCount = 0
        var skippedCount = 0
        
        failedWebhooks.forEach { webhook ->
            try {
                val result = processNotificationLogic(webhook.incrementRetryCount())
                if (result.success) {
                    successCount++
                } else {
                    failedCount++
                }
            } catch (e: Exception) {
                logger.error("Failed to retry webhook ${webhook.id}", e)
                val failedWebhook = webhook.markAsFailed("Retry failed: ${e.message}")
                webhookNotificationRepository.save(failedWebhook)
                failedCount++
            }
        }
        
        logger.info("Webhook retry completed: success=$successCount, failed=$failedCount, skipped=$skippedCount")
        
        return WebhookRetryResult(
            processedCount = failedWebhooks.size,
            successCount = successCount,
            failedCount = failedCount,
            skippedCount = skippedCount
        )
    }
    
    private fun isDuplicateNotification(notification: WebhookNotification): Boolean {
        return notification.purchaseToken?.let { token ->
            webhookNotificationRepository.existsByPlatformAndPurchaseTokenAndNotificationType(
                notification.platform,
                token,
                notification.notificationType.name
            )
        } ?: false
    }
    
    /**
     * 웹훅 알림에 따른 실제 비즈니스 로직 처리
     */
    private fun processNotificationLogic(notification: WebhookNotification): WebhookProcessingResult {
        try {
            when (notification.notificationType) {
                WebhookNotificationType.SUBSCRIPTION_RENEWED,
                WebhookNotificationType.RENEWAL -> {
                    handleSubscriptionRenewal(notification)
                }
                
                WebhookNotificationType.SUBSCRIPTION_CANCELED,
                WebhookNotificationType.CANCEL -> {
                    handleSubscriptionCancellation(notification)
                }
                
                WebhookNotificationType.SUBSCRIPTION_EXPIRED -> {
                    handleSubscriptionExpiry(notification)
                }
                
                WebhookNotificationType.SUBSCRIPTION_RECOVERED,
                WebhookNotificationType.DID_RECOVER -> {
                    handleSubscriptionRecovery(notification)
                }
                
                WebhookNotificationType.REFUND -> {
                    handleRefund(notification)
                }
                
                else -> {
                    logger.info("Notification type ${notification.notificationType} does not require processing")
                }
            }
            
            val processedNotification = notification.markAsProcessed()
            val savedNotification = webhookNotificationRepository.save(processedNotification)
            
            return WebhookProcessingResult(
                success = true,
                webhookNotification = savedNotification,
                message = "Webhook processed successfully"
            )
            
        } catch (e: Exception) {
            logger.error("Failed to process notification logic", e)
            val failedNotification = notification.markAsFailed("Processing failed: ${e.message}")
            val savedNotification = webhookNotificationRepository.save(failedNotification)
            
            return WebhookProcessingResult(
                success = false,
                webhookNotification = savedNotification,
                errorMessage = e.message
            )
        }
    }
    
    private fun handleSubscriptionRenewal(notification: WebhookNotification) {
        logger.info("Handling subscription renewal for token: ${notification.purchaseToken}")
        
        notification.purchaseToken?.let { token ->
            val memberSubscription = memberSubscriptionRepository.findByPurchaseToken(token)
            if (memberSubscription != null) {
                // 구독 기간 연장 로직
                val renewedSubscription = memberSubscription.copy(
                    endDateTime = memberSubscription.endDateTime.plusDays(memberSubscription.subscription.subscriptionPeriod),
                    status = MemberSubscriptionStatus.ACTIVE
                )
                memberSubscriptionRepository.save(renewedSubscription)
                logger.info("Subscription renewed for member: ${memberSubscription.member.id}")
            } else {
                logger.warn("MemberSubscription not found for token: $token")
            }
        }
    }
    
    private fun handleSubscriptionCancellation(notification: WebhookNotification) {
        logger.info("Handling subscription cancellation for token: ${notification.purchaseToken}")
        
        notification.purchaseToken?.let { token ->
            val memberSubscription = memberSubscriptionRepository.findByPurchaseToken(token)
            if (memberSubscription != null) {
                val cancelledSubscription = memberSubscription.copy(
                    status = MemberSubscriptionStatus.CANCELLED
                )
                memberSubscriptionRepository.save(cancelledSubscription)
                logger.info("Subscription cancelled for member: ${memberSubscription.member.id}")
            } else {
                logger.warn("MemberSubscription not found for token: $token")
            }
        }
    }
    
    private fun handleSubscriptionExpiry(notification: WebhookNotification) {
        logger.info("Handling subscription expiry for token: ${notification.purchaseToken}")
        
        notification.purchaseToken?.let { token ->
            val memberSubscription = memberSubscriptionRepository.findByPurchaseToken(token)
            if (memberSubscription != null) {
                val expiredSubscription = memberSubscription.copy(
                    status = MemberSubscriptionStatus.EXPIRED
                )
                memberSubscriptionRepository.save(expiredSubscription)
                logger.info("Subscription expired for member: ${memberSubscription.member.id}")
            } else {
                logger.warn("MemberSubscription not found for token: $token")
            }
        }
    }
    
    private fun handleSubscriptionRecovery(notification: WebhookNotification) {
        logger.info("Handling subscription recovery for token: ${notification.purchaseToken}")
        
        notification.purchaseToken?.let { token ->
            val memberSubscription = memberSubscriptionRepository.findByPurchaseToken(token)
            if (memberSubscription != null) {
                val recoveredSubscription = memberSubscription.copy(
                    status = MemberSubscriptionStatus.ACTIVE
                )
                memberSubscriptionRepository.save(recoveredSubscription)
                logger.info("Subscription recovered for member: ${memberSubscription.member.id}")
            } else {
                logger.warn("MemberSubscription not found for token: $token")
            }
        }
    }
    
    private fun handleRefund(notification: WebhookNotification) {
        logger.info("Handling refund for token: ${notification.purchaseToken}")
        
        notification.purchaseToken?.let { token ->
            val memberSubscription = memberSubscriptionRepository.findByPurchaseToken(token)
            if (memberSubscription != null) {
                val refundedSubscription = memberSubscription.copy(
                    status = MemberSubscriptionStatus.CANCELLED
                )
                memberSubscriptionRepository.save(refundedSubscription)
                logger.info("Subscription refunded for member: ${memberSubscription.member.id}")
            } else {
                logger.warn("MemberSubscription not found for token: $token")
            }
        }
    }
}