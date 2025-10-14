package com.example.integration.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class MemberSubscription(
    val id: Long = 0,
    val member: Member,
    val subscription: Subscription,
    val payment: Payment?,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val status: MemberSubscriptionStatus,
    val purchaseToken: String? = null,
    val productId: String? = null
) {
    fun getNextPaymentDate(): LocalDate? {
        if(status == MemberSubscriptionStatus.ACTIVE) {
            return startDateTime.plusMonths(1).toLocalDate()
        }
        return null
    }
    
    fun isActive(): Boolean = status == MemberSubscriptionStatus.ACTIVE
    
    fun isExpired(): Boolean = LocalDateTime.now().isAfter(endDateTime)
    
    fun cancel(): MemberSubscription = copy(
        status = MemberSubscriptionStatus.CANCELLED
    )
}