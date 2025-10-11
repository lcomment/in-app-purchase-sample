package com.example.repository

import com.example.domain.Payment
import com.example.domain.PaymentStatus
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class IOSPaymentRepository {
    
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
    
    fun findByTransactionId(transactionId: String): Payment? {
        return payments.values.find { it.transactionId == transactionId }
    }
    
    fun findByOriginalTransactionId(originalTransactionId: String): Payment? {
        return payments.values.find { it.orderId == originalTransactionId }
    }
    
    fun existsByTransactionId(transactionId: String): Boolean {
        return payments.values.any { it.transactionId == transactionId }
    }
    
    fun findAll(): List<Payment> {
        return payments.values.toList()
    }
    
    fun deleteById(id: String): Boolean {
        return payments.remove(id) != null
    }
}