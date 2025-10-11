package com.example.domain

import java.time.LocalDateTime

data class Subscription(
    val id: String,
    val userId: String,
    val planId: String,
    val platform: Platform,
    val purchaseToken: String,
    val orderId: String,
    val status: SubscriptionStatus,
    val startDate: LocalDateTime,
    val expiryDate: LocalDateTime,
    val autoRenewing: Boolean,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun isActive(): Boolean = status == SubscriptionStatus.ACTIVE
    
    fun isExpired(): Boolean = LocalDateTime.now().isAfter(expiryDate)
    
    fun renew(newExpiryDate: LocalDateTime): Subscription = 
        copy(expiryDate = newExpiryDate, updatedAt = LocalDateTime.now())
    
    fun cancel(): Subscription = 
        copy(status = SubscriptionStatus.CANCELED, autoRenewing = false, updatedAt = LocalDateTime.now())
}