package com.example.integration.domain

/**
 * 결제 상태
 */
enum class PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    DISPUTED
}