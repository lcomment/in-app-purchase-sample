package com.example.service

import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest
import org.springframework.stereotype.Service

@Service
class GooglePlayAcknowledgeService(
    private val androidPublisher: AndroidPublisher
) {
    
    /**
     * Google Play 구독 결제에 대한 Acknowledge 처리
     * 이 처리를 하지 않으면 Google에서 자동으로 환불 처리함
     */
    fun acknowledgeSubscription(
        packageName: String,
        subscriptionId: String,
        purchaseToken: String,
        developerPayload: String? = null
    ): Boolean {
        return try {
            val acknowledgeRequest = SubscriptionPurchasesAcknowledgeRequest()
                .setDeveloperPayload(developerPayload)
            
            androidPublisher.purchases()
                .subscriptions()
                .acknowledge(packageName, subscriptionId, purchaseToken, acknowledgeRequest)
                .execute()
            
            println("Successfully acknowledged subscription: $subscriptionId with token: $purchaseToken")
            true
        } catch (e: Exception) {
            println("Failed to acknowledge subscription: $subscriptionId with token: $purchaseToken - ${e.message}")
            false
        }
    }
    
    /**
     * 일반 인앱 상품 결제에 대한 Acknowledge 처리
     */
    fun acknowledgeProduct(
        packageName: String,
        productId: String,
        purchaseToken: String,
        developerPayload: String? = null
    ): Boolean {
        return try {
            val acknowledgeRequest = com.google.api.services.androidpublisher.model.ProductPurchasesAcknowledgeRequest()
                .setDeveloperPayload(developerPayload)
            
            androidPublisher.purchases()
                .products()
                .acknowledge(packageName, productId, purchaseToken, acknowledgeRequest)
                .execute()
            
            println("Successfully acknowledged product: $productId with token: $purchaseToken")
            true
        } catch (e: Exception) {
            println("Failed to acknowledge product: $productId with token: $purchaseToken - ${e.message}")
            false
        }
    }
    
    /**
     * 배치로 여러 구독 Acknowledge 처리
     */
    fun batchAcknowledgeSubscriptions(
        packageName: String,
        acknowledgeRequests: List<SubscriptionAcknowledgeRequest>
    ): BatchAcknowledgeResult {
        val results = mutableListOf<AcknowledgeResult>()
        var successCount = 0
        var failureCount = 0
        
        acknowledgeRequests.forEach { request ->
            val success = acknowledgeSubscription(
                packageName = packageName,
                subscriptionId = request.subscriptionId,
                purchaseToken = request.purchaseToken,
                developerPayload = request.developerPayload
            )
            
            results.add(
                AcknowledgeResult(
                    subscriptionId = request.subscriptionId,
                    purchaseToken = request.purchaseToken,
                    success = success
                )
            )
            
            if (success) successCount++ else failureCount++
        }
        
        return BatchAcknowledgeResult(
            totalCount = acknowledgeRequests.size,
            successCount = successCount,
            failureCount = failureCount,
            results = results
        )
    }
    
    /**
     * Acknowledge 상태 확인
     * Google Play API로 구독 정보를 조회하여 acknowledgmentState 확인
     */
    fun checkAcknowledgeStatus(
        packageName: String,
        subscriptionId: String,
        purchaseToken: String
    ): Boolean {
        return try {
            val subscription = androidPublisher.purchases()
                .subscriptionsv2()
                .get(packageName, purchaseToken)
                .execute()
            
            // SubscriptionPurchaseV2에서는 acknowledgmentState 필드가 없음
            // 대신 구독 상태로 확인
            subscription.subscriptionState == "SUBSCRIPTION_STATE_ACTIVE"
        } catch (e: Exception) {
            println("Failed to check acknowledge status: ${e.message}")
            false
        }
    }
}

data class SubscriptionAcknowledgeRequest(
    val subscriptionId: String,
    val purchaseToken: String,
    val developerPayload: String? = null
)

data class AcknowledgeResult(
    val subscriptionId: String,
    val purchaseToken: String,
    val success: Boolean,
    val errorMessage: String? = null
)

data class BatchAcknowledgeResult(
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val results: List<AcknowledgeResult>
) {
    val isAllSuccess: Boolean
        get() = failureCount == 0
    
    val hasFailures: Boolean
        get() = failureCount > 0
}