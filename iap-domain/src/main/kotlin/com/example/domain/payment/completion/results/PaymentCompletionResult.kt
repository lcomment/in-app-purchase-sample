package com.example.domain.payment.completion.results

import com.example.domain.Platform
import java.time.LocalDateTime

data class PaymentCompletionResult(
    val success: Boolean,
    val platform: Platform,
    val transactionId: String,
    val originalTransactionId: String? = null,
    val completedAt: LocalDateTime?,
    val message: String,
    val needsClientAction: Boolean = false  // App Store의 경우 클라이언트 액션 필요
)