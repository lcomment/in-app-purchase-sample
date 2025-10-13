package com.example.integration.domain

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 구독 정보 도메인 모델
 */
data class Subscription(
    val id: String,
    val userId: String,
    val platform: Platform,
    val productId: String,
    val purchaseToken: String,
    val orderId: String,
    val status: SubscriptionStatus,
    val startDate: LocalDateTime,
    val expiryDate: LocalDateTime,
    val autoRenewing: Boolean,
    val price: BigDecimal,
    val currency: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun isActive(): Boolean = status == SubscriptionStatus.ACTIVE
    
    fun isExpired(): Boolean = LocalDateTime.now().isAfter(expiryDate)
    
    fun renew(newExpiryDate: LocalDateTime): Subscription = 
        copy(expiryDate = newExpiryDate, updatedAt = LocalDateTime.now())
}