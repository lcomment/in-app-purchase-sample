package com.example.service

import com.example.config.AppStoreConfig
import com.example.domain.*
import com.example.domain.settlement.*
import com.example.domain.settlement.reconciliation.*
import com.example.domain.payment.event.*
import com.example.repository.IOSPaymentEventRepository
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * App Store 정산 데이터 수집 서비스
 * 
 * App Store에서는 다음과 같은 방식으로 정산 데이터를 제공:
 * 1. App Store Connect의 Sales and Trends (수동 다운로드)
 * 2. Reporter tool을 통한 자동화 다운로드
 * 3. App Store Connect API를 통한 Sales Reports
 * 4. Financial Reports (월별 지급 정보)
 * 
 * 이 샘플에서는 App Store Connect API의 Sales Reports를 사용합니다.
 */
@Service
class AppStoreSettlementService(
    private val appStoreConfig: AppStoreConfig,
    private val restClient: RestClient,
    private val paymentEventRepository: IOSPaymentEventRepository
) {
    
    /**
     * App Store Connect API에서 일일 정산 데이터 수집
     */
    fun collectDailySettlementData(date: LocalDate): List<SettlementData> {
        return try {
            // 실제로는 App Store Connect API 호출
            // GET /v1/salesReports
            val jwt = appStoreConfig.appStoreJwtToken()
            
            // Mock 데이터 생성 - 실제로는 API 응답 파싱
            generateMockAppStoreSettlementData(date)
        } catch (e: Exception) {
            println("Failed to collect App Store settlement data for date: $date - ${e.message}")
            emptyList()
        }
    }
    
    /**
     * App Store Connect API를 통한 Sales Report 조회
     * 실제 구현시에는 이 메서드를 사용
     */
    private fun fetchSalesReportFromAPI(date: LocalDate): String? {
        return try {
            val jwt = appStoreConfig.appStoreJwtToken()
            
            val response = restClient.method(HttpMethod.GET)
                .uri("/v1/salesReports?filter[frequency]=DAILY&filter[reportDate]=${date}&filter[reportType]=SALES")
                .header("Authorization", "Bearer $jwt")
                .retrieve()
                .toEntity(String::class.java)
            
            response.body
        } catch (e: Exception) {
            println("Failed to fetch sales report from App Store Connect API: ${e.message}")
            null
        }
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
            platform = Platform.IOS,
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
        val internalEvents = paymentEventRepository.findByDateAndPlatform(date, Platform.IOS)
        
        // originalTransactionId 기준으로 매칭 (App Store 특성)
        val platformTransactionIds = platformSettlementData.mapNotNull { 
            it.originalTransactionId ?: it.transactionId 
        }.toSet()
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
            platform = Platform.IOS,
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
            val matchingEvent = internalEvents.find { 
                it.id == settlement.transactionId || it.id == settlement.originalTransactionId 
            }
            
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
                
                // App Store 특성: 환율 변동으로 인한 금액 차이 검증
                if (settlement.currency != "USD") {
                    discrepancies.add(
                        ReconciliationDiscrepancy(
                            transactionId = settlement.transactionId,
                            discrepancyType = DiscrepancyType.AMOUNT_MISMATCH,
                            platformData = "${settlement.amount} ${settlement.currency}",
                            internalData = "USD conversion may differ",
                            description = "환율 변동으로 인한 금액 차이 가능"
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
     * App Store 정산 데이터 Mock 생성
     * 실제로는 App Store Connect API Sales Report 파싱
     */
    private fun generateMockAppStoreSettlementData(date: LocalDate): List<SettlementData> {
        val settlements = mutableListOf<SettlementData>()
        
        // 구매 데이터
        repeat(4) { index ->
            val grossAmount = BigDecimal("9.99")
            val platformFee = grossAmount.multiply(BigDecimal("0.30")) // App Store 30% 수수료
            val taxAmount = grossAmount.multiply(BigDecimal("0.08")) // 8% 세금 (지역별 상이)
            val netAmount = grossAmount.subtract(platformFee).subtract(taxAmount)
            
            settlements.add(
                SettlementData(
                    id = UUID.randomUUID().toString(),
                    platform = Platform.IOS,
                    settlementDate = date,
                    transactionId = "ios_purchase_${date}_${index}",
                    originalTransactionId = "ios_original_${date}_${index}",
                    productId = "monthly_premium",
                    subscriptionId = "monthly_premium",
                    eventType = SettlementEventType.PURCHASE,
                    amount = grossAmount,
                    currency = "USD",
                    platformFee = platformFee,
                    netAmount = netAmount,
                    taxAmount = taxAmount,
                    userId = "ios_user_${1000 + index}",
                    countryCode = "US",
                    createdAt = LocalDateTime.now(),
                    platformSettlementId = "ios_settlement_${date}_${index}"
                )
            )
        }
        
        // 갱신 데이터
        repeat(7) { index ->
            val grossAmount = BigDecimal("9.99")
            val platformFee = grossAmount.multiply(BigDecimal("0.30"))
            val taxAmount = grossAmount.multiply(BigDecimal("0.08"))
            val netAmount = grossAmount.subtract(platformFee).subtract(taxAmount)
            
            settlements.add(
                SettlementData(
                    id = UUID.randomUUID().toString(),
                    platform = Platform.IOS,
                    settlementDate = date,
                    transactionId = "ios_renewal_${date}_${index}",
                    originalTransactionId = "ios_original_renewal_${date}_${index}",
                    productId = "monthly_premium",
                    subscriptionId = "monthly_premium",
                    eventType = SettlementEventType.RENEWAL,
                    amount = grossAmount,
                    currency = "USD",
                    platformFee = platformFee,
                    netAmount = netAmount,
                    taxAmount = taxAmount,
                    userId = "ios_user_${2000 + index}",
                    countryCode = "US",
                    createdAt = LocalDateTime.now(),
                    platformSettlementId = "ios_settlement_renewal_${date}_${index}"
                )
            )
        }
        
        // 환불 데이터
        repeat(1) { index ->
            val grossAmount = BigDecimal("-9.99") // 환불은 음수
            val platformFee = BigDecimal("0") // 환불시 수수료 반환
            val taxAmount = BigDecimal("-0.79") // 세금 반환
            val netAmount = grossAmount.subtract(platformFee).subtract(taxAmount)
            
            settlements.add(
                SettlementData(
                    id = UUID.randomUUID().toString(),
                    platform = Platform.IOS,
                    settlementDate = date,
                    transactionId = "ios_refund_${date}_${index}",
                    originalTransactionId = "ios_original_refund_${date}_${index}",
                    productId = "monthly_premium",
                    subscriptionId = "monthly_premium",
                    eventType = SettlementEventType.REFUND,
                    amount = grossAmount,
                    currency = "USD",
                    platformFee = platformFee,
                    netAmount = netAmount,
                    taxAmount = taxAmount,
                    userId = "ios_user_${3000 + index}",
                    countryCode = "US",
                    createdAt = LocalDateTime.now(),
                    platformSettlementId = "ios_settlement_refund_${date}_${index}"
                )
            )
        }
        
        return settlements
    }
    
    /**
     * App Store 특화 정산 데이터 처리
     * - 환율 변동 고려
     * - 지역별 세율 차이
     * - Original Transaction ID 기반 매칭
     */
    fun processAppStoreSpecificReconciliation(date: LocalDate): Map<String, Any> {
        val settlementData = collectDailySettlementData(date)
        
        // 통화별 집계
        val byCurrency = settlementData.groupBy { it.currency }
        
        // 국가별 집계
        val byCountry = settlementData.groupBy { it.countryCode }
        
        // Original Transaction ID 기반 구독 체인 분석
        val subscriptionChains = settlementData.filter { it.originalTransactionId != null }
            .groupBy { it.originalTransactionId }
        
        return mapOf(
            "totalSettlements" to settlementData.size,
            "currencyBreakdown" to byCurrency.mapValues { (_, settlements) ->
                mapOf(
                    "count" to settlements.size,
                    "totalAmount" to settlements.sumOf { it.amount },
                    "netAmount" to settlements.sumOf { it.netAmount }
                )
            },
            "countryBreakdown" to byCountry.mapValues { (_, settlements) ->
                mapOf(
                    "count" to settlements.size,
                    "totalAmount" to settlements.sumOf { it.amount }
                )
            },
            "subscriptionChains" to subscriptionChains.size,
            "averageSubscriptionValue" to if (subscriptionChains.isNotEmpty()) {
                val chainTotals = subscriptionChains.values.map { chain ->
                    chain.sumOf { it.amount }.toDouble()
                }
                chainTotals.average()
            } else 0.0
        )
    }
    
    /**
     * 정산 데이터 유효성 검증 (App Store 특화)
     */
    fun validateSettlementData(settlementData: List<SettlementData>): List<String> {
        val validationErrors = mutableListOf<String>()
        
        settlementData.forEach { settlement ->
            // App Store 필수 필드 검증
            if (settlement.originalTransactionId.isNullOrBlank() && 
                settlement.eventType in listOf(SettlementEventType.RENEWAL, SettlementEventType.REFUND)) {
                validationErrors.add("Original Transaction ID is required for renewals and refunds: ${settlement.id}")
            }
            
            // 금액 검증
            if (settlement.amount == BigDecimal.ZERO && settlement.eventType != SettlementEventType.TAX_ADJUSTMENT) {
                validationErrors.add("Amount cannot be zero for settlement: ${settlement.id}")
            }
            
            // App Store 수수료율 검증 (15% 또는 30%)
            val expectedFeeRate = if (settlement.amount < BigDecimal("1000000")) BigDecimal("0.30") else BigDecimal("0.15")
            val actualFeeRate = if (settlement.amount != BigDecimal.ZERO) {
                settlement.platformFee.divide(settlement.amount.abs(), 4, java.math.RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
            
            if (settlement.eventType != SettlementEventType.REFUND && 
                actualFeeRate.subtract(expectedFeeRate).abs() > BigDecimal("0.01")) {
                validationErrors.add("Unexpected platform fee rate for settlement: ${settlement.id}")
            }
            
            // 통화 코드 검증
            if (settlement.currency.length != 3) {
                validationErrors.add("Invalid currency code for settlement: ${settlement.id}")
            }
        }
        
        return validationErrors
    }
}