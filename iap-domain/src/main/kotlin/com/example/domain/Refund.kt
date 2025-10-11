package com.example.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class Refund(
    val id: String,
    val paymentId: String,
    val subscriptionId: String,
    val userId: String,
    val amount: BigDecimal,
    val currency: String,
    val reason: String,
    val platform: Platform,
    val platformRefundId: String? = null,
    val refundDate: LocalDateTime,
    val createdAt: LocalDateTime = LocalDateTime.now()
)