package com.example.integration.application.port.out

import com.example.integration.domain.Refund
import com.example.integration.domain.Platform
import com.example.integration.domain.RefundStatus
import java.time.LocalDate

/**
 * 환불 저장소 포트 (출력 포트)
 */
interface RefundRepositoryPort {
    
    /**
     * 환불 정보 저장
     */
    fun save(refund: Refund): Refund
    
    /**
     * 환불 정보 조회
     */
    fun findById(refundId: String): Refund?
    
    /**
     * 결제 ID로 환불 정보 조회
     */
    fun findByPaymentId(paymentId: String): List<Refund>
    
    /**
     * 사용자 환불 내역 조회
     */
    fun findByUserId(userId: String): List<Refund>
    
    /**
     * 사용자 및 플랫폼별 환불 내역 조회
     */
    fun findByUserIdAndPlatform(userId: String, platform: Platform): List<Refund>
    
    /**
     * 상태별 환불 목록 조회
     */
    fun findByStatus(status: RefundStatus): List<Refund>
    
    /**
     * 날짜 범위로 환불 목록 조회
     */
    fun findByPlatformAndDateRange(
        platform: Platform,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Refund>
    
    /**
     * 처리 대기 중인 환불 목록 조회
     */
    fun findPendingRefunds(): List<Refund>
}