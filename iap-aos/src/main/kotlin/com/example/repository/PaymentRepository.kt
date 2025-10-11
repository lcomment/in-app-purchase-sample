package com.example.repository

import com.example.domain.Payment
import com.example.domain.PaymentStatus
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class PaymentRepository {
    
    private val payments = ConcurrentHashMap<String, Payment>()
    
    fun save(payment: Payment): Payment {
        payments[payment.id] = payment
        return payment
    }
    
    fun findById(id: String): Payment? {
        return payments[id]
    }
    
    fun findBySubscriptionId(subscriptionId: String): List<Payment> {
        return payments.values.filter { it.subscriptionId == subscriptionId }
    }
    
    fun findByUserId(userId: String): List<Payment> {
        return payments.values.filter { it.userId == userId }
    }
    
    fun findByUserIdAndStatus(userId: String, status: PaymentStatus): List<Payment> {
        return payments.values.filter { it.userId == userId && it.status == status }
    }
    
    fun findByOrderId(orderId: String): Payment? {
        return payments.values.find { it.orderId == orderId }
    }
    
    fun findByPurchaseToken(purchaseToken: String): Payment? {
        return payments.values.find { it.purchaseToken == purchaseToken }
    }
    
    fun existsByPurchaseToken(purchaseToken: String): Boolean {
        return payments.values.any { it.purchaseToken == purchaseToken }
    }
    
    fun findAll(): List<Payment> {
        return payments.values.toList()
    }
    
    fun deleteById(id: String): Boolean {
        return payments.remove(id) != null
    }
}