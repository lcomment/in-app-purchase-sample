package com.example.domain

import java.math.BigDecimal
import java.time.Duration

data class SubscriptionPlan(
    val id: String,
    val name: String,
    val productId: String,
    val price: BigDecimal,
    val currency: String,
    val duration: Duration,
    val platform: Platform,
    val isActive: Boolean = true
)