package com.example.repository

import com.example.domain.Subscription
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class SubscriptionRepository {
    
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
    
    fun findByUserIdAndStatus(userId: String, status: com.example.domain.SubscriptionStatus): List<Subscription> {
        return subscriptions.values.filter { it.userId == userId && it.status == status }
    }
    
    fun findByPurchaseToken(purchaseToken: String): Subscription? {
        return subscriptions.values.find { it.purchaseToken == purchaseToken }
    }
    
    fun existsByPurchaseToken(purchaseToken: String): Boolean {
        return subscriptions.values.any { it.purchaseToken == purchaseToken }
    }
    
    fun findAll(): List<Subscription> {
        return subscriptions.values.toList()
    }
    
    fun deleteById(id: String): Boolean {
        return subscriptions.remove(id) != null
    }
}