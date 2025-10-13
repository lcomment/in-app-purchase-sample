package com.example.integration.domain

/**
 * 환불 상태
 */
enum class RefundStatus {
    REQUESTED,          // 환불 요청됨
    PENDING_APPROVAL,   // 승인 대기
    APPROVED,           // 승인됨
    PROCESSING,         // 처리 중
    COMPLETED,          // 완료됨
    FAILED,             // 실패
    REJECTED,           // 거부됨
    CANCELLED           // 취소됨
}