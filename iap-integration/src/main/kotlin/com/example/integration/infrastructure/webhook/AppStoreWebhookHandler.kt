package com.example.integration.infrastructure.webhook

import com.example.integration.domain.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

/**
 * App Store Server-to-Server Notifications 핸들러
 */
@Component
class AppStoreWebhookHandler(
    private val objectMapper: ObjectMapper
) : WebhookHandler {
    
    private val logger = LoggerFactory.getLogger(AppStoreWebhookHandler::class.java)
    
    override fun canHandle(platform: Platform): Boolean = platform == Platform.APP_STORE
    
    override fun parseNotification(payload: String): WebhookNotification {
        try {
            val jsonNode = objectMapper.readTree(payload)
            logger.info("Received App Store notification: $payload")
            
            return parseAppStoreNotification(jsonNode, payload)
            
        } catch (e: Exception) {
            logger.error("Failed to parse App Store notification", e)
            throw IllegalArgumentException("Invalid App Store notification format: ${e.message}")
        }
    }
    
    private fun parseAppStoreNotification(data: JsonNode, originalPayload: String): WebhookNotification {
        val notificationType = data.get("notification_type")?.asText()
        val environment = data.get("environment")?.asText() // Sandbox or PROD
        
        // 구독 정보 추출
        val latestReceiptInfo = data.get("latest_receipt_info")
        val latestExpiredReceiptInfo = data.get("latest_expired_receipt_info")
        
        // 가장 최신 정보 우선 사용
        val receiptInfo = latestReceiptInfo ?: latestExpiredReceiptInfo
        
        val productId = receiptInfo?.get("product_id")?.asText()
        val transactionId = receiptInfo?.get("transaction_id")?.asText()
        val originalTransactionId = receiptInfo?.get("original_transaction_id")?.asText()
        val webOrderLineItemId = receiptInfo?.get("web_order_line_item_id")?.asText()
        
        // Auto-renew 상태 정보
        val autoRenewStatusChangeDate = data.get("auto_renew_status_change_date")?.asText()
        val autoRenewStatus = data.get("auto_renew_status")?.asText()
        
        val webhookType = mapAppStoreNotificationType(notificationType)
        
        return WebhookNotification(
            platform = Platform.APP_STORE,
            notificationType = webhookType,
            subscriptionId = originalTransactionId, // App Store에서는 original_transaction_id가 구독 식별자
            productId = productId,
            purchaseToken = transactionId, // App Store에서는 transaction_id가 구매 토큰 역할
            orderId = webOrderLineItemId,
            originalData = originalPayload,
            status = WebhookProcessingStatus.PENDING,
            processedAt = null
        )
    }
    
    /**
     * App Store 알림 타입을 도메인 타입으로 매핑
     */
    private fun mapAppStoreNotificationType(notificationType: String?): WebhookNotificationType {
        return when (notificationType?.uppercase()) {
            "INITIAL_BUY" -> WebhookNotificationType.INITIAL_BUY
            "CANCEL" -> WebhookNotificationType.CANCEL
            "RENEWAL" -> WebhookNotificationType.RENEWAL
            "INTERACTIVE_RENEWAL" -> WebhookNotificationType.INTERACTIVE_RENEWAL
            "DID_CHANGE_RENEWAL_PREF" -> WebhookNotificationType.DID_CHANGE_RENEWAL_PREF
            "DID_CHANGE_RENEWAL_STATUS" -> WebhookNotificationType.DID_CHANGE_RENEWAL_STATUS
            "DID_FAIL_TO_RENEW" -> WebhookNotificationType.DID_FAIL_TO_RENEW
            "DID_RECOVER" -> WebhookNotificationType.DID_RECOVER
            "PRICE_INCREASE_CONSENT" -> WebhookNotificationType.PRICE_INCREASE_CONSENT
            "REFUND" -> WebhookNotificationType.REFUND
            "REVOKE" -> WebhookNotificationType.REVOKE
            else -> WebhookNotificationType.UNKNOWN
        }
    }
    
    /**
     * App Store JWT 토큰을 디코딩하여 실제 데이터 추출
     * 실제 구현에서는 JWT 라이브러리를 사용해야 함
     */
    private fun decodeAppStoreJWT(signedPayload: String): JsonNode? {
        try {
            // JWT는 header.payload.signature 구조
            val parts = signedPayload.split(".")
            if (parts.size != 3) return null
            
            // payload 부분을 Base64 디코딩
            val payload = String(Base64.getUrlDecoder().decode(parts[1]))
            return objectMapper.readTree(payload)
            
        } catch (e: Exception) {
            logger.warn("Failed to decode App Store JWT: ${e.message}")
            return null
        }
    }
}