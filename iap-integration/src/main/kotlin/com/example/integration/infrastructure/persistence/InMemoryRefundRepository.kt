package com.example.integration.infrastructure.persistence

import com.example.integration.application.port.out.RefundRepositoryPort
import com.example.integration.domain.Refund
import com.example.integration.domain.Platform
import com.example.integration.domain.RefundStatus
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * 인메모리 환불 저장소 구현체 (Infrastructure Layer)
 * 실제 운영환경에서는 JPA 또는 다른 영속성 기술을 사용
 */
@Repository
class InMemoryRefundRepository : RefundRepositoryPort {
    
    private val refunds = ConcurrentHashMap<String, Refund>()
    private val paymentIndex = ConcurrentHashMap<String, MutableList<String>>() // paymentId -> List<refundId>
    private val userIndex = ConcurrentHashMap<String, MutableList<String>>() // userId -> List<refundId>
    
    override fun save(refund: Refund): Refund {
        refunds[refund.id] = refund
        
        // 결제 ID 인덱스 업데이트
        paymentIndex.computeIfAbsent(refund.paymentId) { mutableListOf() }.let { list ->
            if (!list.contains(refund.id)) {
                list.add(refund.id)
            }
        }
        
        // 사용자 ID 인덱스 업데이트
        userIndex.computeIfAbsent(refund.userId) { mutableListOf() }.let { list ->
            if (!list.contains(refund.id)) {
                list.add(refund.id)
            }
        }
        
        return refund
    }
    
    override fun findById(refundId: String): Refund? {
        return refunds[refundId]
    }
    
    override fun findByPaymentId(paymentId: String): List<Refund> {
        val refundIds = paymentIndex[paymentId] ?: return emptyList()
        return refundIds.mapNotNull { refunds[it] }
    }
    
    override fun findByUserId(userId: String): List<Refund> {
        val refundIds = userIndex[userId] ?: return emptyList()
        return refundIds.mapNotNull { refunds[it] }
    }
    
    override fun findByUserIdAndPlatform(userId: String, platform: Platform): List<Refund> {
        return findByUserId(userId).filter { it.platform == platform }
    }
    
    override fun findByStatus(status: RefundStatus): List<Refund> {
        return refunds.values.filter { it.status == status }
    }
    
    override fun findByPlatformAndDateRange(
        platform: Platform,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Refund> {
        return refunds.values.filter { refund ->
            refund.platform == platform &&
            !refund.requestedAt.toLocalDate().isBefore(startDate) &&
            !refund.requestedAt.toLocalDate().isAfter(endDate)
        }
    }
    
    override fun findPendingRefunds(): List<Refund> {
        val pendingStatuses = listOf(
            RefundStatus.REQUESTED,
            RefundStatus.PENDING_APPROVAL,
            RefundStatus.APPROVED,
            RefundStatus.PROCESSING
        )
        return refunds.values.filter { it.status in pendingStatuses }
    }
}