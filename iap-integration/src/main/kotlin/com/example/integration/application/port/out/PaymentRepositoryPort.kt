package com.example.integration.application.port.out

import com.example.integration.domain.Payment
import com.example.integration.domain.Platform
import java.time.LocalDate

/**
 * 결제 저장소 포트 (출력 포트)
 */
interface PaymentRepositoryPort {
    
    /**
     * 결제 정보 저장
     */
    fun save(payment: Payment): Payment
    
    /**
     * 결제 정보 조회
     */
    fun findById(paymentId: String): Payment?
    
    /**
     * 구매 토큰으로 결제 정보 조회
     */
    fun findByPurchaseToken(purchaseToken: String): Payment?
    
    /**
     * 날짜 범위로 결제 정보 조회
     */
    fun findByPlatformAndDateRange(
        platform: Platform,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Payment>
    
    /**
     * 결제 정보 존재 여부 확인
     */
    fun existsByPurchaseToken(purchaseToken: String): Boolean
}