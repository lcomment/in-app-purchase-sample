package com.example.domain.payment.completion.results

import java.time.LocalDateTime

data class PaymentCompletionStatus(
    val isCompleted: Boolean,
    val isPending: Boolean,
    val lastCheckedAt: LocalDateTime,
    val message: String
)