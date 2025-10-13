package com.example.integration.domain

/**
 * 구독 상태
 */
enum class SubscriptionStatus {
    ACTIVE,
    EXPIRED,
    CANCELED,
    ON_HOLD,
    IN_GRACE_PERIOD,
    PAUSED
}