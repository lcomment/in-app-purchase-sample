package com.example.domain.refund

import com.example.domain.Platform
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 환불 요청 도메인 모델
 * 
 * 환불 프로세스의 시작점이 되는 요청 정보를 관리합니다.
 */
data class RefundRequest(
    val id: String,                           // 요청 ID
    val platform: Platform,                   // 플랫폼
    val transactionId: String,                // 거래 ID
    val originalTransactionId: String?,       // 원본 거래 ID (App Store용)
    val reason: RefundReason,                 // 환불 사유
    val requestedAmount: BigDecimal?,         // 요청 환불 금액 (null이면 전액)
    val currency: String,                     // 통화
    val requestedBy: RefundRequestor,         // 요청자
    val customerNote: String?,                // 고객 메모
    val urgencyLevel: RefundUrgency,          // 긴급도
    val autoApprovalEligible: Boolean,        // 자동 승인 대상 여부
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * 전액 환불 요청인지 확인
     */
    val isFullRefund: Boolean
        get() = requestedAmount == null

    /**
     * 부분 환불 요청인지 확인
     */
    val isPartialRefund: Boolean
        get() = requestedAmount != null

    /**
     * 즉시 처리 필요 여부
     */
    val requiresImmediateProcessing: Boolean
        get() = urgencyLevel == RefundUrgency.CRITICAL || 
                reason == RefundReason.FRAUD_PREVENTION
}

/**
 * 환불 긴급도
 */
enum class RefundUrgency(val description: String, val maxProcessingHours: Int) {
    LOW("낮음", 72),
    MEDIUM("보통", 24),
    HIGH("높음", 8),
    CRITICAL("긴급", 2)
}

/**
 * 환불 검증 결과
 */
data class RefundValidationResult(
    val isValid: Boolean,
    val eligibleForRefund: Boolean,
    val canAutoApprove: Boolean,
    val estimatedAmount: BigDecimal?,
    val currency: String?,
    val validationErrors: List<RefundValidationError>,
    val warnings: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
) {
    val hasErrors: Boolean
        get() = validationErrors.isNotEmpty()

    val hasWarnings: Boolean
        get() = warnings.isNotEmpty()
}

/**
 * 환불 검증 오류
 */
data class RefundValidationError(
    val code: String,
    val message: String,
    val severity: ErrorSeverity
)

enum class ErrorSeverity {
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * 환불 처리 결과
 */
data class RefundProcessResult(
    val success: Boolean,
    val refundTransactionId: String?,
    val platformRefundId: String?,
    val processedAmount: BigDecimal?,
    val currency: String?,
    val processedAt: LocalDateTime?,
    val error: RefundError?,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 환불 처리 오류
 */
data class RefundError(
    val type: RefundErrorType,
    val code: String,
    val message: String,
    val retryable: Boolean,
    val details: Map<String, Any> = emptyMap()
)

enum class RefundErrorType {
    NETWORK_ERROR,
    AUTHENTICATION_ERROR,
    AUTHORIZATION_ERROR,
    BUSINESS_RULE_VIOLATION,
    PLATFORM_SERVICE_ERROR,
    TIMEOUT_ERROR,
    VALIDATION_ERROR,
    INTERNAL_ERROR
}