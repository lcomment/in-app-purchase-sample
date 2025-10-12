package com.example.domain

import java.time.LocalDateTime

/**
 * 지급 완료 처리를 위한 통합 서비스 인터페이스
 * 플랫폼별로 다른 Acknowledge 처리 방식을 추상화
 */
interface PaymentCompletionService {
    
    /**
     * 구독 결제 완료 처리
     */
    fun completeSubscriptionPayment(request: SubscriptionCompletionRequest): PaymentCompletionResult
    
    /**
     * 일반 상품 결제 완료 처리
     */
    fun completeProductPayment(request: ProductCompletionRequest): PaymentCompletionResult
    
    /**
     * 배치 결제 완료 처리
     */
    fun batchCompletePayments(requests: List<PaymentCompletionRequest>): BatchPaymentCompletionResult
    
    /**
     * 결제 완료 상태 확인
     */
    fun checkCompletionStatus(
        platform: Platform,
        transactionId: String,
        originalTransactionId: String? = null
    ): PaymentCompletionStatus
}

data class SubscriptionCompletionRequest(
    val platform: Platform,
    val packageName: String? = null,        // Google Play only
    val subscriptionId: String,
    val purchaseToken: String,
    val originalTransactionId: String? = null, // App Store only
    val userId: String,
    val developerPayload: String? = null
)

data class ProductCompletionRequest(
    val platform: Platform,
    val packageName: String? = null,        // Google Play only
    val productId: String,
    val purchaseToken: String,
    val originalTransactionId: String? = null, // App Store only
    val userId: String,
    val developerPayload: String? = null
)

data class PaymentCompletionRequest(
    val platform: Platform,
    val transactionId: String,
    val originalTransactionId: String? = null,
    val userId: String,
    val productId: String,
    val purchaseToken: String? = null,      // Google Play only
    val packageName: String? = null,        // Google Play only
    val developerPayload: String? = null
)

data class PaymentCompletionResult(
    val success: Boolean,
    val platform: Platform,
    val transactionId: String,
    val originalTransactionId: String? = null,
    val completedAt: LocalDateTime?,
    val message: String,
    val needsClientAction: Boolean = false  // App Store의 경우 클라이언트 액션 필요
)

data class BatchPaymentCompletionResult(
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val pendingClientActionCount: Int,      // App Store에서 클라이언트 액션 대기 중
    val results: List<PaymentCompletionResult>
) {
    val isAllCompleted: Boolean
        get() = failureCount == 0 && pendingClientActionCount == 0
    
    val hasFailures: Boolean
        get() = failureCount > 0
}

data class PaymentCompletionStatus(
    val isCompleted: Boolean,
    val isPending: Boolean,
    val lastCheckedAt: LocalDateTime,
    val message: String
)

enum class CompletionMethod {
    SERVER_ACKNOWLEDGE,     // Google Play - 서버에서 직접 Acknowledge
    CLIENT_FINISH,          // App Store - 클라이언트에서 finishTransaction
    AUTOMATIC              // 자동 처리 (일부 케이스)
}