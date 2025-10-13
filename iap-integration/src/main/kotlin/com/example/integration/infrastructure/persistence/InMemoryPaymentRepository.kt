package com.example.integration.infrastructure.persistence

import com.example.integration.application.port.out.PaymentRepositoryPort
import com.example.integration.domain.Payment
import com.example.integration.domain.Platform
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * 인메모리 결제 저장소 구현체 (Infrastructure Layer)
 * 실제 운영환경에서는 JPA 또는 다른 영속성 기술을 사용
 */
@Repository
class InMemoryPaymentRepository : PaymentRepositoryPort {
    
    private val payments = ConcurrentHashMap<String, Payment>()
    private val tokenIndex = ConcurrentHashMap<String, String>() // purchaseToken -> paymentId
    
    override fun save(payment: Payment): Payment {
        payments[payment.id] = payment
        tokenIndex[payment.purchaseToken] = payment.id
        return payment
    }
    
    override fun findById(paymentId: String): Payment? {
        return payments[paymentId]
    }
    
    override fun findByPurchaseToken(purchaseToken: String): Payment? {
        val paymentId = tokenIndex[purchaseToken] ?: return null
        return payments[paymentId]
    }
    
    override fun findByPlatformAndDateRange(
        platform: Platform,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Payment> {
        return payments.values.filter { payment ->
            payment.platform == platform &&
            !payment.paymentDate.toLocalDate().isBefore(startDate) &&
            !payment.paymentDate.toLocalDate().isAfter(endDate)
        }
    }
    
    override fun existsByPurchaseToken(purchaseToken: String): Boolean {
        return tokenIndex.containsKey(purchaseToken)
    }
}