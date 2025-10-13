package com.example.integration.application.service

import com.example.integration.application.port.`in`.SettlementUseCase
import com.example.integration.application.port.`in`.SettlementRequest
import com.example.integration.application.port.`in`.SettlementResult
import com.example.integration.application.port.out.PaymentRepositoryPort
import com.example.integration.application.port.out.SettlementRepositoryPort
import com.example.integration.domain.Platform
import com.example.integration.domain.Settlement
import com.example.integration.domain.SettlementStatus
import com.example.integration.domain.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * 정산 처리 서비스 (Application Layer)
 */
@Service
@Transactional
class SettlementService(
    private val platformAdapterFactory: PlatformAdapterFactory,
    private val paymentRepository: PaymentRepositoryPort,
    private val settlementRepository: SettlementRepositoryPort
) : SettlementUseCase {
    
    private val logger = LoggerFactory.getLogger(SettlementService::class.java)
    
    companion object {
        // 플랫폼별 수수료율
        private val PLATFORM_FEE_RATES = mapOf(
            Platform.GOOGLE_PLAY to BigDecimal("0.30"), // 30%
            Platform.APP_STORE to BigDecimal("0.30")     // 30%
        )
    }
    
    override fun processSettlement(request: SettlementRequest): SettlementResult {
        logger.info("Processing settlement: platform=${request.platform}, date=${request.settlementDate}")
        
        try {
            // 기존 정산 데이터 확인
            val existingSettlement = settlementRepository.findByPlatformAndDate(
                request.platform, 
                request.settlementDate
            )
            
            if (existingSettlement != null && !request.forceRecalculate) {
                logger.info("Settlement already exists: ${existingSettlement.id}")
                return SettlementResult(
                    success = true,
                    settlement = existingSettlement
                )
            }
            
            // 해당 날짜의 결제 데이터 조회
            val payments = paymentRepository.findByPlatformAndDateRange(
                platform = request.platform,
                startDate = request.settlementDate,
                endDate = request.settlementDate
            )
            
            if (payments.isEmpty()) {
                logger.warn("No payments found for settlement: platform=${request.platform}, date=${request.settlementDate}")
            }
            
            // 정산 계산
            val successfulPayments = payments.filter { it.status == PaymentStatus.SUCCESS }
            val refundedPayments = payments.filter { it.status == PaymentStatus.REFUNDED }
            
            val totalRevenue = successfulPayments
                .map { it.amount }
                .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }
            
            val refundAmount = refundedPayments
                .map { it.amount }
                .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }
            
            val platformFeeRate = PLATFORM_FEE_RATES[request.platform] ?: BigDecimal.ZERO
            val platformFee = totalRevenue.multiply(platformFeeRate)
            val netRevenue = totalRevenue.subtract(platformFee)
            
            // 정산 데이터 생성
            val settlement = Settlement(
                id = existingSettlement?.id ?: UUID.randomUUID().toString(),
                platform = request.platform,
                settlementDate = request.settlementDate,
                totalRevenue = totalRevenue,
                platformFee = platformFee,
                netRevenue = netRevenue,
                currency = successfulPayments.firstOrNull()?.currency ?: "USD",
                paymentCount = successfulPayments.size,
                refundCount = refundedPayments.size,
                refundAmount = refundAmount,
                status = SettlementStatus.COMPLETED
            )
            
            // 정산 데이터 저장
            val savedSettlement = settlementRepository.save(settlement)
            
            logger.info("Settlement processed successfully: id=${savedSettlement.id}, " +
                    "totalRevenue=${totalRevenue}, netRevenue=${netRevenue}")
            
            return SettlementResult(
                success = true,
                settlement = savedSettlement
            )
            
        } catch (e: Exception) {
            logger.error("Settlement processing failed", e)
            return SettlementResult(
                success = false,
                settlement = null,
                errorMessage = "정산 처리 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    override fun getSettlement(platform: Platform, settlementDate: LocalDate): Settlement? {
        return settlementRepository.findByPlatformAndDate(platform, settlementDate)
    }
}