package com.example.service

import com.example.domain.*
import com.example.domain.payment.completion.*
import com.example.domain.payment.completion.requests.*
import com.example.domain.payment.completion.results.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class GooglePlayPaymentCompletionService(
    private val googlePlayAcknowledgeService: GooglePlayAcknowledgeService
) : PaymentCompletionService {
    
    override fun completeSubscriptionPayment(request: SubscriptionCompletionRequest): PaymentCompletionResult {
        if (request.platform != Platform.AOS) {
            return PaymentCompletionResult(
                success = false,
                platform = request.platform,
                transactionId = request.purchaseToken,
                completedAt = null,
                message = "Invalid platform for Google Play service"
            )
        }
        
        val packageName = request.packageName ?: "com.example.app" // 기본값 또는 설정에서 가져오기
        
        val acknowledged = googlePlayAcknowledgeService.acknowledgeSubscription(
            packageName = packageName,
            subscriptionId = request.subscriptionId,
            purchaseToken = request.purchaseToken,
            developerPayload = request.developerPayload
        )
        
        return PaymentCompletionResult(
            success = acknowledged,
            platform = Platform.AOS,
            transactionId = request.purchaseToken,
            completedAt = if (acknowledged) LocalDateTime.now() else null,
            message = if (acknowledged) "Subscription acknowledged successfully" else "Failed to acknowledge subscription",
            needsClientAction = false
        )
    }
    
    override fun completeProductPayment(request: ProductCompletionRequest): PaymentCompletionResult {
        if (request.platform != Platform.AOS) {
            return PaymentCompletionResult(
                success = false,
                platform = request.platform,
                transactionId = request.purchaseToken,
                completedAt = null,
                message = "Invalid platform for Google Play service"
            )
        }
        
        val packageName = request.packageName ?: "com.example.app"
        
        val acknowledged = googlePlayAcknowledgeService.acknowledgeProduct(
            packageName = packageName,
            productId = request.productId,
            purchaseToken = request.purchaseToken,
            developerPayload = request.developerPayload
        )
        
        return PaymentCompletionResult(
            success = acknowledged,
            platform = Platform.AOS,
            transactionId = request.purchaseToken,
            completedAt = if (acknowledged) LocalDateTime.now() else null,
            message = if (acknowledged) "Product acknowledged successfully" else "Failed to acknowledge product",
            needsClientAction = false
        )
    }
    
    override fun batchCompletePayments(requests: List<PaymentCompletionRequest>): BatchPaymentCompletionResult {
        val googlePlayRequests = requests.filter { it.platform == Platform.AOS }
        
        if (googlePlayRequests.isEmpty()) {
            return BatchPaymentCompletionResult(
                totalCount = 0,
                successCount = 0,
                failureCount = 0,
                pendingClientActionCount = 0,
                results = emptyList()
            )
        }
        
        val subscriptionRequests = googlePlayRequests.mapNotNull { request ->
            request.purchaseToken?.let { token ->
                SubscriptionAcknowledgeRequest(
                    subscriptionId = request.productId,
                    purchaseToken = token,
                    developerPayload = request.developerPayload
                )
            }
        }
        
        val packageName = googlePlayRequests.first().packageName ?: "com.example.app"
        val batchResult = googlePlayAcknowledgeService.batchAcknowledgeSubscriptions(
            packageName = packageName,
            acknowledgeRequests = subscriptionRequests
        )
        
        val results = batchResult.results.map { result ->
            PaymentCompletionResult(
                success = result.success,
                platform = Platform.AOS,
                transactionId = result.purchaseToken,
                completedAt = if (result.success) LocalDateTime.now() else null,
                message = if (result.success) "Acknowledged successfully" else "Failed to acknowledge",
                needsClientAction = false
            )
        }
        
        return BatchPaymentCompletionResult(
            totalCount = batchResult.totalCount,
            successCount = batchResult.successCount,
            failureCount = batchResult.failureCount,
            pendingClientActionCount = 0,
            results = results
        )
    }
    
    override fun checkCompletionStatus(
        platform: Platform,
        transactionId: String,
        originalTransactionId: String?
    ): PaymentCompletionStatus {
        if (platform != Platform.AOS) {
            return PaymentCompletionStatus(
                isCompleted = false,
                isPending = false,
                lastCheckedAt = LocalDateTime.now(),
                message = "Invalid platform for Google Play service"
            )
        }
        
        // Google Play에서는 purchaseToken을 transactionId로 사용
        // 실제로는 구독 ID도 필요하므로 데이터베이스에서 조회해야 함
        val packageName = "com.example.app" // 설정에서 가져오기
        val subscriptionId = "premium_monthly" // 실제로는 데이터베이스에서 조회
        
        val isAcknowledged = googlePlayAcknowledgeService.checkAcknowledgeStatus(
            packageName = packageName,
            subscriptionId = subscriptionId,
            purchaseToken = transactionId
        )
        
        return PaymentCompletionStatus(
            isCompleted = isAcknowledged,
            isPending = !isAcknowledged,
            lastCheckedAt = LocalDateTime.now(),
            message = if (isAcknowledged) "Payment acknowledged" else "Payment not yet acknowledged"
        )
    }
}