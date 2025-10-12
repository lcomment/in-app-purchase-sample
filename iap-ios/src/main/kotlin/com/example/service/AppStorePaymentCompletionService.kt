package com.example.service

import com.example.domain.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AppStorePaymentCompletionService(
    private val appStoreAcknowledgeService: AppStoreAcknowledgeService
) : PaymentCompletionService {
    
    override fun completeSubscriptionPayment(request: SubscriptionCompletionRequest): PaymentCompletionResult {
        if (request.platform != Platform.IOS) {
            return PaymentCompletionResult(
                success = false,
                platform = request.platform,
                transactionId = request.purchaseToken,
                completedAt = null,
                message = "Invalid platform for App Store service"
            )
        }
        
        val originalTransactionId = request.originalTransactionId
            ?: return PaymentCompletionResult(
                success = false,
                platform = Platform.IOS,
                transactionId = request.purchaseToken,
                completedAt = null,
                message = "originalTransactionId is required for App Store"
            )
        
        // App Store에서는 서버 측에서 완료 상태만 기록
        // 실제 finishTransaction은 클라이언트에서 수행해야 함
        val result = appStoreAcknowledgeService.markTransactionAsFinished(
            originalTransactionId = originalTransactionId,
            transactionId = request.purchaseToken, // 실제로는 transactionId
            userId = request.userId,
            productId = request.subscriptionId
        )
        
        return PaymentCompletionResult(
            success = result.success,
            platform = Platform.IOS,
            transactionId = request.purchaseToken,
            originalTransactionId = originalTransactionId,
            completedAt = result.finishedAt,
            message = result.message + " (Client should call finishTransaction)",
            needsClientAction = true  // 클라이언트에서 finishTransaction 호출 필요
        )
    }
    
    override fun completeProductPayment(request: ProductCompletionRequest): PaymentCompletionResult {
        if (request.platform != Platform.IOS) {
            return PaymentCompletionResult(
                success = false,
                platform = request.platform,
                transactionId = request.purchaseToken,
                completedAt = null,
                message = "Invalid platform for App Store service"
            )
        }
        
        val originalTransactionId = request.originalTransactionId
            ?: return PaymentCompletionResult(
                success = false,
                platform = Platform.IOS,
                transactionId = request.purchaseToken,
                completedAt = null,
                message = "originalTransactionId is required for App Store"
            )
        
        val result = appStoreAcknowledgeService.markTransactionAsFinished(
            originalTransactionId = originalTransactionId,
            transactionId = request.purchaseToken,
            userId = request.userId,
            productId = request.productId
        )
        
        return PaymentCompletionResult(
            success = result.success,
            platform = Platform.IOS,
            transactionId = request.purchaseToken,
            originalTransactionId = originalTransactionId,
            completedAt = result.finishedAt,
            message = result.message + " (Client should call finishTransaction)",
            needsClientAction = true
        )
    }
    
    override fun batchCompletePayments(requests: List<PaymentCompletionRequest>): BatchPaymentCompletionResult {
        val appStoreRequests = requests.filter { it.platform == Platform.IOS }
        
        if (appStoreRequests.isEmpty()) {
            return BatchPaymentCompletionResult(
                totalCount = 0,
                successCount = 0,
                failureCount = 0,
                pendingClientActionCount = 0,
                results = emptyList()
            )
        }
        
        val transactionRequests = appStoreRequests.mapNotNull { request ->
            request.originalTransactionId?.let { originalTxId ->
                TransactionFinishRequest(
                    originalTransactionId = originalTxId,
                    transactionId = request.transactionId,
                    userId = request.userId,
                    productId = request.productId
                )
            }
        }
        
        val batchResult = appStoreAcknowledgeService.batchMarkTransactionsAsFinished(transactionRequests)
        
        val results = batchResult.results.map { result ->
            PaymentCompletionResult(
                success = result.success,
                platform = Platform.IOS,
                transactionId = result.transactionId,
                originalTransactionId = result.originalTransactionId,
                completedAt = result.finishedAt,
                message = result.message + " (Client should call finishTransaction)",
                needsClientAction = true
            )
        }
        
        return BatchPaymentCompletionResult(
            totalCount = batchResult.totalCount,
            successCount = batchResult.successCount,
            failureCount = batchResult.failureCount,
            pendingClientActionCount = batchResult.successCount, // 모든 성공 케이스가 클라이언트 액션 대기
            results = results
        )
    }
    
    override fun checkCompletionStatus(
        platform: Platform,
        transactionId: String,
        originalTransactionId: String?
    ): PaymentCompletionStatus {
        if (platform != Platform.IOS) {
            return PaymentCompletionStatus(
                isCompleted = false,
                isPending = false,
                lastCheckedAt = LocalDateTime.now(),
                message = "Invalid platform for App Store service"
            )
        }
        
        val originalTxId = originalTransactionId
            ?: return PaymentCompletionStatus(
                isCompleted = false,
                isPending = false,
                lastCheckedAt = LocalDateTime.now(),
                message = "originalTransactionId is required for App Store"
            )
        
        val statusResult = appStoreAcknowledgeService.checkTransactionStatus(originalTxId)
        
        return PaymentCompletionStatus(
            isCompleted = statusResult.isFinished,
            isPending = !statusResult.isFinished,
            lastCheckedAt = LocalDateTime.now(),
            message = statusResult.message
        )
    }
}