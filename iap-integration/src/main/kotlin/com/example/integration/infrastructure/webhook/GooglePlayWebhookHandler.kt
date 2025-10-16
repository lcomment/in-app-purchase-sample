package com.example.integration.infrastructure.webhook

import com.example.integration.domain.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

/**
 * Google Play Real-time Developer Notifications 핸들러
 */
@Component
class GooglePlayWebhookHandler(
    private val objectMapper: ObjectMapper
) : WebhookHandler {
    
    private val logger = LoggerFactory.getLogger(GooglePlayWebhookHandler::class.java)
    
    override fun canHandle(platform: Platform): Boolean = platform == Platform.GOOGLE_PLAY
    
    override fun parseNotification(payload: String): WebhookNotification {
        try {
            val jsonNode = objectMapper.readTree(payload)
            val message = jsonNode.get("message")
            
            // Pub/Sub 메시지에서 데이터 추출
            val data = message?.get("data")?.asText()
                ?: throw IllegalArgumentException("Missing data in Google Play notification")
            
            // Base64 디코딩
            val decodedData = String(Base64.getDecoder().decode(data))
            val notificationData = objectMapper.readTree(decodedData)
            
            logger.info("Received Google Play notification: $decodedData")
            
            return parseGooglePlayNotification(notificationData, payload)
            
        } catch (e: Exception) {
            logger.error("Failed to parse Google Play notification", e)
            throw IllegalArgumentException("Invalid Google Play notification format: ${e.message}")
        }
    }
    
    private fun parseGooglePlayNotification(data: JsonNode, originalPayload: String): WebhookNotification {
        val version = data.get("version")?.asText()
        val packageName = data.get("packageName")?.asText()
        val eventTimeMillis = data.get("eventTimeMillis")?.asLong()
        
        // 구독 알림인지 확인
        val subscriptionNotification = data.get("subscriptionNotification")
        if (subscriptionNotification != null) {
            return parseSubscriptionNotification(subscriptionNotification, originalPayload)
        }
        
        // 일회성 결제 알림인지 확인
        val oneTimeProductNotification = data.get("oneTimeProductNotification")
        if (oneTimeProductNotification != null) {
            return parseOneTimeProductNotification(oneTimeProductNotification, originalPayload)
        }
        
        throw IllegalArgumentException("Unknown Google Play notification type")
    }
    
    private fun parseSubscriptionNotification(notification: JsonNode, originalPayload: String): WebhookNotification {
        val version = notification.get("version")?.asText()
        val notificationType = notification.get("notificationType")?.asInt()
        val purchaseToken = notification.get("purchaseToken")?.asText()
        val subscriptionId = notification.get("subscriptionId")?.asText()
        
        val webhookType = mapGooglePlayNotificationType(notificationType)
        
        return WebhookNotification(
            platform = Platform.GOOGLE_PLAY,
            notificationType = webhookType,
            subscriptionId = subscriptionId,
            productId = subscriptionId, // Google Play에서는 subscriptionId가 productId 역할
            purchaseToken = purchaseToken,
            orderId = null, // 구독 알림에는 orderId가 없음
            originalData = originalPayload,
            status = WebhookProcessingStatus.PENDING,
            processedAt = null
        )
    }
    
    private fun parseOneTimeProductNotification(notification: JsonNode, originalPayload: String): WebhookNotification {
        val version = notification.get("version")?.asText()
        val notificationType = notification.get("notificationType")?.asInt()
        val purchaseToken = notification.get("purchaseToken")?.asText()
        val sku = notification.get("sku")?.asText()
        
        val webhookType = mapGooglePlayOneTimeNotificationType(notificationType)
        
        return WebhookNotification(
            platform = Platform.GOOGLE_PLAY,
            notificationType = webhookType,
            subscriptionId = null,
            productId = sku,
            purchaseToken = purchaseToken,
            orderId = null,
            originalData = originalPayload,
            status = WebhookProcessingStatus.PENDING,
            processedAt = null
        )
    }
    
    /**
     * Google Play 구독 알림 타입을 도메인 타입으로 매핑
     */
    private fun mapGooglePlayNotificationType(notificationType: Int?): WebhookNotificationType {
        return when (notificationType) {
            1 -> WebhookNotificationType.SUBSCRIPTION_RECOVERED
            2 -> WebhookNotificationType.SUBSCRIPTION_RENEWED
            3 -> WebhookNotificationType.SUBSCRIPTION_CANCELED
            4 -> WebhookNotificationType.SUBSCRIPTION_PURCHASED
            5 -> WebhookNotificationType.SUBSCRIPTION_ON_HOLD
            6 -> WebhookNotificationType.SUBSCRIPTION_IN_GRACE_PERIOD
            7 -> WebhookNotificationType.SUBSCRIPTION_RESTARTED
            8 -> WebhookNotificationType.SUBSCRIPTION_PRICE_CHANGE_CONFIRMED
            9 -> WebhookNotificationType.SUBSCRIPTION_DEFERRED
            10 -> WebhookNotificationType.SUBSCRIPTION_PAUSED
            11 -> WebhookNotificationType.SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED
            12 -> WebhookNotificationType.SUBSCRIPTION_REVOKED
            13 -> WebhookNotificationType.SUBSCRIPTION_EXPIRED
            else -> WebhookNotificationType.UNKNOWN
        }
    }
    
    /**
     * Google Play 일회성 상품 알림 타입을 도메인 타입으로 매핑
     */
    private fun mapGooglePlayOneTimeNotificationType(notificationType: Int?): WebhookNotificationType {
        return when (notificationType) {
            1 -> WebhookNotificationType.SUBSCRIPTION_PURCHASED // 일회성 구매
            2 -> WebhookNotificationType.SUBSCRIPTION_CANCELED // 취소
            else -> WebhookNotificationType.UNKNOWN
        }
    }
}