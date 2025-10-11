package com.example.repository

import com.example.domain.Subscription
import com.example.domain.SubscriptionStatus
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class IOSSubscriptionRepository {
    
    private val subscriptions = ConcurrentHashMap<String, Subscription>()
    
    fun save(subscription: Subscription): Subscription {
        subscriptions[subscription.id] = subscription
        return subscription
    }
    
    fun findById(id: String): Subscription? {
        return subscriptions[id]
    }
    
    fun findByUserId(userId: String): List<Subscription> {
        return subscriptions.values.filter { it.userId == userId }
    }
    
    fun findByUserIdAndStatus(userId: String, status: SubscriptionStatus): List<Subscription> {
        return subscriptions.values.filter { it.userId == userId && it.status == status }
    }
    
    fun findByPurchaseToken(purchaseToken: String): Subscription? {
        return subscriptions.values.find { it.purchaseToken == purchaseToken }
    }
    
    fun findByOriginalTransactionId(originalTransactionId: String): Subscription? {
        return subscriptions.values.find { it.orderId == originalTransactionId }
    }
    
    fun existsByPurchaseToken(purchaseToken: String): Boolean {
        return subscriptions.values.any { it.purchaseToken == purchaseToken }
    }
    
    fun existsByOriginalTransactionId(originalTransactionId: String): Boolean {
        return subscriptions.values.any { it.orderId == originalTransactionId }
    }
    
    fun findAll(): List<Subscription> {
        return subscriptions.values.toList()
    }
    
    fun deleteById(id: String): Boolean {
        return subscriptions.remove(id) != null
    }
}