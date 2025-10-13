package com.example.domain.refund

import com.example.domain.Platform
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 환불 거래 도메인 모델
 * 
 * 모든 환불 처리의 핵심 데이터를 관리합니다.
 * - 플랫폼별 환불 추적
 * - 환불 상태 라이프사이클 관리
 * - 재무 영향 추적
 */
data class RefundTransaction(
    val id: String,                           // 내부 환불 ID
    val platform: Platform,                   // 플랫폼 (AOS/IOS)
    val originalTransactionId: String,        // 원본 거래 ID
    val refundTransactionId: String?,         // 플랫폼 환불 거래 ID
    val amount: BigDecimal,                   // 환불 금액
    val currency: String,                     // 통화 코드 (ISO 4217)
    val reason: RefundReason,                 // 환불 사유
    val status: RefundStatus,                 // 환불 상태
    val requestedAt: LocalDateTime,           // 환불 요청 시간
    val processedAt: LocalDateTime?,          // 환불 처리 시간
    val completedAt: LocalDateTime?,          // 환불 완료 시간
    val approvedBy: String?,                  // 승인자 ID
    val customerNote: String?,                // 고객 메모
    val internalNote: String?,                // 내부 메모
    val metadata: Map<String, String> = emptyMap()  // 추가 메타데이터
) {
    /**
     * 환불 처리 소요 시간 계산
     */
    val processingDuration: Long?
        get() = if (requestedAt != null && completedAt != null) {
            java.time.Duration.between(requestedAt, completedAt).toMillis()
        } else null

    /**
     * 환불이 완료 상태인지 확인
     */
    val isCompleted: Boolean
        get() = status == RefundStatus.COMPLETED

    /**
     * 환불이 진행 중인지 확인
     */
    val isInProgress: Boolean
        get() = status in listOf(
            RefundStatus.REQUESTED,
            RefundStatus.PENDING_APPROVAL,
            RefundStatus.APPROVED,
            RefundStatus.PROCESSING
        )

    /**
     * 환불 실패 여부 확인
     */
    val isFailed: Boolean
        get() = status in listOf(
            RefundStatus.FAILED,
            RefundStatus.REJECTED,
            RefundStatus.CANCELLED
        )
}

/**
 * 환불 사유 열거형
 */
enum class RefundReason(val description: String, val requiresApproval: Boolean) {
    CUSTOMER_REQUEST("고객 요청", true),
    TECHNICAL_ISSUE("기술적 문제", false),
    BILLING_ERROR("결제 오류", false),
    FRAUD_PREVENTION("사기 방지", true),
    REGULATORY_COMPLIANCE("규정 준수", false),
    DUPLICATE_PAYMENT("중복 결제", false),
    SERVICE_UNAVAILABLE("서비스 이용 불가", true),
    UNAUTHORIZED_PURCHASE("무단 구매", true),
    POLICY_VIOLATION("정책 위반", true)
}

/**
 * 환불 상태 열거형
 */
enum class RefundStatus(val description: String, val isFinal: Boolean) {
    REQUESTED("요청됨", false),
    PENDING_APPROVAL("승인 대기", false),
    APPROVED("승인됨", false),
    PROCESSING("처리 중", false),
    COMPLETED("완료", true),
    FAILED("실패", true),
    REJECTED("거부됨", true),
    CANCELLED("취소됨", true);

    /**
     * 다음 상태로 전환 가능한지 확인
     */
    fun canTransitionTo(nextStatus: RefundStatus): Boolean {
        if (isFinal) return false
        
        return when (this) {
            REQUESTED -> nextStatus in listOf(PENDING_APPROVAL, APPROVED, REJECTED, CANCELLED)
            PENDING_APPROVAL -> nextStatus in listOf(APPROVED, REJECTED, CANCELLED)
            APPROVED -> nextStatus in listOf(PROCESSING, FAILED, CANCELLED)
            PROCESSING -> nextStatus in listOf(COMPLETED, FAILED)
            else -> false
        }
    }
}

/**
 * 환불 요청자 타입
 */
enum class RefundRequestor(val description: String) {
    CUSTOMER("고객"),
    SUPPORT_TEAM("고객지원팀"),
    AUTOMATIC_SYSTEM("자동 시스템"),
    PLATFORM_INITIATED("플랫폼 주도"),
    ADMIN("관리자")
}