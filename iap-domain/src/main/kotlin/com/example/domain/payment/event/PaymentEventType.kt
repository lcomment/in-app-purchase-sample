package com.example.domain.payment.event

enum class PaymentEventType {
    PURCHASE,
    RENEWAL,
    CANCELLATION,
    REFUND,
    EXPIRATION,
    GRACE_PERIOD_START,
    GRACE_PERIOD_END,
    PAUSE,
    RESUME
}