package com.example.integration.domain

enum class WebhookProcessingStatus {
    PENDING,    // 처리 대기 중
    PROCESSING, // 처리 중
    PROCESSED,  // 처리 완료
    FAILED,     // 처리 실패
    SKIPPED     // 처리 스킵 (중복 등)
}