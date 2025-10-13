package com.example.service

import com.example.domain.*
import com.example.domain.settlement.*
import com.example.domain.settlement.reconciliation.*
import com.example.domain.payment.event.*
import com.example.repository.PaymentEventRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Google Play 정산 데이터 수집 서비스
 * 
 * Google Play에서는 다음과 같은 방식으로 정산 데이터를 제공:
 * 1. Google Play Console의 Financial reports (수동 다운로드)
 * 2. Cloud Storage의 정산 리포트 (자동화 가능)
 * 3. Play Developer Reporting API (베타)
 * 
 * 이 샘플에서는 Cloud Storage CSV 파일 기반의 정산 데이터 처리를 모의합니다.
 */
@Service
class GooglePlaySettlementService(
    private val paymentEventRepository: PaymentEventRepository
) {
    
    /**
     * Google Play Cloud Storage에서 일일 정산 데이터 수집
     * 실제로는 GCS에서 CSV 파일을 다운로드하여 파싱
     */
    fun collectDailySettlementData(date: LocalDate): List<SettlementData> {
        // Mock 데이터 생성 - 실제로는 GCS에서 CSV 파일 읽기
        return generateMockGooglePlaySettlementData(date)
    }
    
    /**
     * 일일 정산 요약 데이터 생성
     */
    fun generateDailySettlementSummary(date: LocalDate): DailySettlementSummary {
        val settlementData = collectDailySettlementData(date)
        
        val totalTransactions = settlementData.size
        val totalGrossAmount = settlementData.sumOf { it.amount }
        val totalPlatformFee = settlementData.sumOf { it.platformFee }
        val totalNetAmount = settlementData.sumOf { it.netAmount }
        val totalTaxAmount = settlementData.mapNotNull { it.taxAmount }.sumOf { it }
        
        val eventCounts = settlementData.groupBy { it.eventType }
        
        return DailySettlementSummary(
            date = date,
            platform = Platform.AOS,
            totalTransactions = totalTransactions,
            totalGrossAmount = totalGrossAmount,
            totalPlatformFee = totalPlatformFee,
            totalNetAmount = totalNetAmount,
            totalTaxAmount = totalTaxAmount,
            purchaseCount = eventCounts[SettlementEventType.PURCHASE]?.size ?: 0,
            renewalCount = eventCounts[SettlementEventType.RENEWAL]?.size ?: 0,
            refundCount = eventCounts[SettlementEventType.REFUND]?.size ?: 0,
            chargebackCount = eventCounts[SettlementEventType.CHARGEBACK]?.size ?: 0,
            createdAt = LocalDateTime.now()
        )
    }
    
    /**
     * 플랫폼 정산 데이터와 내부 PaymentEvent 데이터 대사
     */
    fun reconcileWithInternalData(date: LocalDate): ReconciliationResult {
        val platformSettlementData = collectDailySettlementData(date)
        val internalEvents = paymentEventRepository.findByDateAndPlatform(date, Platform.AOS)
        
        // 거래 ID 기준으로 매칭
        val platformTransactionIds = platformSettlementData.map { it.transactionId }.toSet()
        val internalEventIds = internalEvents.map { it.id }.toSet()
        
        val matchedTransactions = platformTransactionIds.intersect(internalEventIds)
        val unmatchedPlatformTransactions = (platformTransactionIds - internalEventIds).toList()
        val unmatchedInternalEvents = (internalEventIds - platformTransactionIds).toList()
        
        // 불일치 항목 분석
        val discrepancies = analyzeDiscrepancies(platformSettlementData, internalEvents)
        
        // 대사 상태 결정
        val reconciliationStatus = determineReconciliationStatus(
            platformSettlementData.size,
            internalEvents.size,
            matchedTransactions.size,
            discrepancies.size
        )
        
        return ReconciliationResult(
            date = date,
            platform = Platform.AOS,
            totalPlatformTransactions = platformSettlementData.size,
            totalInternalEvents = internalEvents.size,
            matchedTransactions = matchedTransactions.size,
            unmatchedPlatformTransactions = unmatchedPlatformTransactions,
            unmatchedInternalEvents = unmatchedInternalEvents,
            discrepancies = discrepancies,
            reconciliationStatus = reconciliationStatus,
            processedAt = LocalDateTime.now()
        )
    }
    
    private fun analyzeDiscrepancies(
        platformData: List<SettlementData>,
        internalEvents: List<PaymentEvent>
    ): List<ReconciliationDiscrepancy> {
        val discrepancies = mutableListOf<ReconciliationDiscrepancy>()
        
        // 이벤트 타입 매핑
        val eventTypeMapping = mapOf(
            SettlementEventType.PURCHASE to PaymentEventType.PURCHASE,
            SettlementEventType.RENEWAL to PaymentEventType.RENEWAL,
            SettlementEventType.REFUND to PaymentEventType.REFUND
        )
        
        platformData.forEach { settlement ->
            val matchingEvent = internalEvents.find { it.id == settlement.transactionId }
            
            if (matchingEvent != null) {
                val expectedEventType = eventTypeMapping[settlement.eventType]
                
                if (expectedEventType != null && expectedEventType != matchingEvent.eventType) {
                    discrepancies.add(
                        ReconciliationDiscrepancy(
                            transactionId = settlement.transactionId,
                            discrepancyType = DiscrepancyType.EVENT_TYPE_MISMATCH,
                            platformData = settlement.eventType.description,
                            internalData = matchingEvent.eventType.name,
                            description = "이벤트 타입이 일치하지 않음"
                        )
                    )
                }
            }
        }
        
        return discrepancies
    }
    
    private fun determineReconciliationStatus(
        platformCount: Int,
        internalCount: Int,
        matchedCount: Int,
        discrepancyCount: Int
    ): ReconciliationStatus {
        return when {
            platformCount == internalCount && matchedCount == platformCount && discrepancyCount == 0 -> {
                ReconciliationStatus.MATCHED
            }
            matchedCount.toDouble() / maxOf(platformCount, internalCount) >= 0.95 && discrepancyCount <= 2 -> {
                ReconciliationStatus.PARTIAL_MATCH
            }
            matchedCount.toDouble() / maxOf(platformCount, internalCount) >= 0.80 -> {
                ReconciliationStatus.MAJOR_DISCREPANCY
            }
            else -> {
                ReconciliationStatus.FAILED
            }
        }
    }
    
    /**
     * Google Play 정산 데이터 Mock 생성
     * 실제로는 GCS CSV 파일에서 파싱
     */
    private fun generateMockGooglePlaySettlementData(date: LocalDate): List<SettlementData> {
        val settlements = mutableListOf<SettlementData>()
        
        // 구매 데이터
        repeat(5) { index ->
            val grossAmount = BigDecimal("9.99")
            val platformFee = grossAmount.multiply(BigDecimal("0.30")) // Google Play 30% 수수료
            val taxAmount = grossAmount.multiply(BigDecimal("0.10")) // 10% 세금
            val netAmount = grossAmount.subtract(platformFee).subtract(taxAmount)
            
            settlements.add(
                SettlementData(
                    id = UUID.randomUUID().toString(),
                    platform = Platform.AOS,
                    settlementDate = date,
                    transactionId = "gp_purchase_${date}_${index}",
                    originalTransactionId = null,
                    productId = "monthly_premium",
                    subscriptionId = "monthly_premium",
                    eventType = SettlementEventType.PURCHASE,
                    amount = grossAmount,
                    currency = "USD",
                    platformFee = platformFee,
                    netAmount = netAmount,
                    taxAmount = taxAmount,
                    userId = "user_${1000 + index}",
                    countryCode = "US",
                    createdAt = LocalDateTime.now(),
                    platformSettlementId = "gp_settlement_${date}_${index}"
                )
            )
        }
        
        // 갱신 데이터
        repeat(8) { index ->
            val grossAmount = BigDecimal("9.99")
            val platformFee = grossAmount.multiply(BigDecimal("0.30"))
            val taxAmount = grossAmount.multiply(BigDecimal("0.10"))
            val netAmount = grossAmount.subtract(platformFee).subtract(taxAmount)
            
            settlements.add(
                SettlementData(
                    id = UUID.randomUUID().toString(),
                    platform = Platform.AOS,
                    settlementDate = date,
                    transactionId = "gp_renewal_${date}_${index}",
                    originalTransactionId = "gp_original_${date}_${index}",
                    productId = "monthly_premium",
                    subscriptionId = "monthly_premium",
                    eventType = SettlementEventType.RENEWAL,
                    amount = grossAmount,
                    currency = "USD",
                    platformFee = platformFee,
                    netAmount = netAmount,
                    taxAmount = taxAmount,
                    userId = "user_${2000 + index}",
                    countryCode = "US",
                    createdAt = LocalDateTime.now(),
                    platformSettlementId = "gp_settlement_renewal_${date}_${index}"
                )
            )
        }
        
        // 환불 데이터
        repeat(1) { index ->
            val grossAmount = BigDecimal("-9.99") // 환불은 음수
            val platformFee = BigDecimal("0") // 환불시 수수료 반환
            val taxAmount = BigDecimal("-0.99") // 세금 반환
            val netAmount = grossAmount.subtract(platformFee).subtract(taxAmount)
            
            settlements.add(
                SettlementData(
                    id = UUID.randomUUID().toString(),
                    platform = Platform.AOS,
                    settlementDate = date,
                    transactionId = "gp_refund_${date}_${index}",
                    originalTransactionId = "gp_original_refund_${date}_${index}",
                    productId = "monthly_premium",
                    subscriptionId = "monthly_premium",
                    eventType = SettlementEventType.REFUND,
                    amount = grossAmount,
                    currency = "USD",
                    platformFee = platformFee,
                    netAmount = netAmount,
                    taxAmount = taxAmount,
                    userId = "user_${3000 + index}",
                    countryCode = "US",
                    createdAt = LocalDateTime.now(),
                    platformSettlementId = "gp_settlement_refund_${date}_${index}"
                )
            )
        }
        
        return settlements
    }
    
    /**
     * 정산 데이터 유효성 검증
     */
    fun validateSettlementData(settlementData: List<SettlementData>): List<String> {
        val validationErrors = mutableListOf<String>()
        
        settlementData.forEach { settlement ->
            // 필수 필드 검증
            if (settlement.transactionId.isBlank()) {
                validationErrors.add("Transaction ID is required for settlement: ${settlement.id}")
            }
            
            // 금액 검증
            if (settlement.amount == BigDecimal.ZERO && settlement.eventType != SettlementEventType.TAX_ADJUSTMENT) {
                validationErrors.add("Amount cannot be zero for settlement: ${settlement.id}")
            }
            
            // 수수료 검증
            if (settlement.platformFee < BigDecimal.ZERO && settlement.eventType != SettlementEventType.REFUND) {
                validationErrors.add("Platform fee cannot be negative for non-refund settlement: ${settlement.id}")
            }
            
            // 순 금액 검증
            val expectedNetAmount = settlement.amount.subtract(settlement.platformFee)
                .subtract(settlement.taxAmount ?: BigDecimal.ZERO)
            
            if (settlement.netAmount.compareTo(expectedNetAmount) != 0) {
                validationErrors.add("Net amount calculation mismatch for settlement: ${settlement.id}")
            }
        }
        
        return validationErrors
    }
}