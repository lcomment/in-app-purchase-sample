package com.example.integration.domain

/**
 * 환불 사유
 */
enum class RefundReason {
    CUSTOMER_REQUEST,       // 고객 요청
    TECHNICAL_ISSUE,        // 기술적 문제
    BILLING_ERROR,          // 결제 오류
    FRAUD_PREVENTION,       // 사기 방지
    REGULATORY_COMPLIANCE,  // 규정 준수
    OTHER                   // 기타
}