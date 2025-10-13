package com.example.domain.payment.completion

import com.example.domain.Platform
import com.example.domain.payment.completion.requests.*
import com.example.domain.payment.completion.results.*

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