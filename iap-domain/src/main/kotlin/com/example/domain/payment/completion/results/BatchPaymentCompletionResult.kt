package com.example.domain.payment.completion.results

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