package com.example.service.settlement

import com.example.domain.*
import com.example.domain.settlement.*
import com.example.domain.settlement.reconciliation.*
import com.example.domain.payment.event.*
import com.example.service.settlement.results.*
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * 일일 정산 데이터 수집 통합 서비스
 * 
 * 이 서비스는 Google Play와 App Store의 정산 데이터를 수집하고
 * 통합된 일일 정산 보고서를 생성합니다.
 */
@Service
class DailySettlementCollectionService {
    
    // 메모리 저장소 (실제로는 데이터베이스 사용)
    private val dailySettlementSummaries = ConcurrentHashMap<String, DailySettlementSummary>()
    private val reconciliationResults = ConcurrentHashMap<String, ReconciliationResult>()
    private val settlementData = ConcurrentHashMap<String, List<SettlementData>>()
    
    /**
     * 특정 날짜의 모든 플랫폼 정산 데이터 수집
     */
    fun collectDailySettlementData(
        date: LocalDate,
        googlePlaySettlementService: Any?, // GooglePlaySettlementService
        appStoreSettlementService: Any?    // AppStoreSettlementService
    ): DailySettlementCollectionResult {
        
        val collectionResults = mutableMapOf<Platform, SettlementCollectionResult>()
        
        // Google Play 정산 데이터 수집
        googlePlaySettlementService?.let {
            try {
                // 실제로는 타입 캐스팅하여 사용
                // val googlePlayService = it as GooglePlaySettlementService
                // val googlePlayData = googlePlayService.collectDailySettlementData(date)
                // val googlePlaySummary = googlePlayService.generateDailySettlementSummary(date)
                // val googlePlayReconciliation = googlePlayService.reconcileWithInternalData(date)
                
                // Mock 결과
                val mockGooglePlayData = generateMockSettlementData(date, Platform.AOS)
                val mockGooglePlaySummary = generateMockSettlementSummary(date, Platform.AOS)
                val mockGooglePlayReconciliation = generateMockReconciliationResult(date, Platform.AOS)
                
                // 저장
                storeDailySettlementData(date, Platform.AOS, mockGooglePlayData)
                storeDailySettlementSummary(date, Platform.AOS, mockGooglePlaySummary)
                storeReconciliationResult(date, Platform.AOS, mockGooglePlayReconciliation)
                
                collectionResults[Platform.AOS] = SettlementCollectionResult(
                    platform = Platform.AOS,
                    success = true,
                    dataCount = mockGooglePlayData.size,
                    summary = mockGooglePlaySummary,
                    reconciliation = mockGooglePlayReconciliation,
                    errors = emptyList()
                )
            } catch (e: Exception) {
                collectionResults[Platform.AOS] = SettlementCollectionResult(
                    platform = Platform.AOS,
                    success = false,
                    dataCount = 0,
                    summary = null,
                    reconciliation = null,
                    errors = listOf("Google Play 데이터 수집 실패: ${e.message}")
                )
            }
        }
        
        // App Store 정산 데이터 수집
        appStoreSettlementService?.let {
            try {
                // 실제로는 타입 캐스팅하여 사용
                // val appStoreService = it as AppStoreSettlementService
                // val appStoreData = appStoreService.collectDailySettlementData(date)
                // val appStoreSummary = appStoreService.generateDailySettlementSummary(date)
                // val appStoreReconciliation = appStoreService.reconcileWithInternalData(date)
                
                // Mock 결과
                val mockAppStoreData = generateMockSettlementData(date, Platform.IOS)
                val mockAppStoreSummary = generateMockSettlementSummary(date, Platform.IOS)
                val mockAppStoreReconciliation = generateMockReconciliationResult(date, Platform.IOS)
                
                // 저장
                storeDailySettlementData(date, Platform.IOS, mockAppStoreData)
                storeDailySettlementSummary(date, Platform.IOS, mockAppStoreSummary)
                storeReconciliationResult(date, Platform.IOS, mockAppStoreReconciliation)
                
                collectionResults[Platform.IOS] = SettlementCollectionResult(
                    platform = Platform.IOS,
                    success = true,
                    dataCount = mockAppStoreData.size,
                    summary = mockAppStoreSummary,
                    reconciliation = mockAppStoreReconciliation,
                    errors = emptyList()
                )
            } catch (e: Exception) {
                collectionResults[Platform.IOS] = SettlementCollectionResult(
                    platform = Platform.IOS,
                    success = false,
                    dataCount = 0,
                    summary = null,
                    reconciliation = null,
                    errors = listOf("App Store 데이터 수집 실패: ${e.message}")
                )
            }
        }
        
        val totalDataCount = collectionResults.values.sumOf { it.dataCount }
        val overallSuccess = collectionResults.values.all { it.success }
        
        return DailySettlementCollectionResult(
            date = date,
            platformResults = collectionResults,
            overallSuccess = overallSuccess,
            totalDataCount = totalDataCount,
            processedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 일일 통합 정산 보고서 생성
     */
    fun generateCombinedDailyReport(date: LocalDate): CombinedDailySettlementReport {
        val googlePlaySummary = getDailySettlementSummary(date, Platform.AOS)
        val appStoreSummary = getDailySettlementSummary(date, Platform.IOS)
        val googlePlayReconciliation = getReconciliationResult(date, Platform.AOS)
        val appStoreReconciliation = getReconciliationResult(date, Platform.IOS)
        
        val combinedMetrics = calculateCombinedMetrics(googlePlaySummary, appStoreSummary)
        val overallReconciliationStatus = determineOverallReconciliationStatus(
            googlePlayReconciliation,
            appStoreReconciliation
        )
        
        return CombinedDailySettlementReport(
            date = date,
            googlePlaySummary = googlePlaySummary,
            appStoreSummary = appStoreSummary,
            googlePlayReconciliation = googlePlayReconciliation,
            appStoreReconciliation = appStoreReconciliation,
            combinedMetrics = combinedMetrics,
            overallReconciliationStatus = overallReconciliationStatus,
            generatedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 정산 데이터 저장
     */
    private fun storeDailySettlementData(date: LocalDate, platform: Platform, data: List<SettlementData>) {
        val key = "${date}_${platform.name}"
        settlementData[key] = data
        println("정산 데이터 저장 완료: $date $platform (${data.size}건)")
    }
    
    /**
     * 일일 요약 저장
     */
    private fun storeDailySettlementSummary(date: LocalDate, platform: Platform, summary: DailySettlementSummary) {
        val key = "${date}_${platform.name}"
        dailySettlementSummaries[key] = summary
        println("정산 요약 저장 완료: $date $platform")
    }
    
    /**
     * 대사 결과 저장
     */
    private fun storeReconciliationResult(date: LocalDate, platform: Platform, result: ReconciliationResult) {
        val key = "${date}_${platform.name}"
        reconciliationResults[key] = result
        println("대사 결과 저장 완료: $date $platform - ${result.reconciliationStatus}")
    }
    
    /**
     * 통합 메트릭 계산
     */
    private fun calculateCombinedMetrics(
        googlePlaySummary: DailySettlementSummary?,
        appStoreSummary: DailySettlementSummary?
    ): CombinedDailyMetrics {
        val googlePlayRevenue = googlePlaySummary?.totalGrossAmount ?: java.math.BigDecimal.ZERO
        val appStoreRevenue = appStoreSummary?.totalGrossAmount ?: java.math.BigDecimal.ZERO
        val totalRevenue = googlePlayRevenue.add(appStoreRevenue)
        
        val googlePlayShare = if (totalRevenue > java.math.BigDecimal.ZERO) {
            googlePlayRevenue.divide(totalRevenue, 4, java.math.RoundingMode.HALF_UP)
                .multiply(java.math.BigDecimal("100"))
        } else java.math.BigDecimal.ZERO
        
        val appStoreShare = if (totalRevenue > java.math.BigDecimal.ZERO) {
            appStoreRevenue.divide(totalRevenue, 4, java.math.RoundingMode.HALF_UP)
                .multiply(java.math.BigDecimal("100"))
        } else java.math.BigDecimal.ZERO
        
        return CombinedDailyMetrics(
            totalTransactions = (googlePlaySummary?.totalTransactions ?: 0) + (appStoreSummary?.totalTransactions ?: 0),
            totalGrossRevenue = totalRevenue,
            totalPlatformFees = (googlePlaySummary?.totalPlatformFee ?: java.math.BigDecimal.ZERO)
                .add(appStoreSummary?.totalPlatformFee ?: java.math.BigDecimal.ZERO),
            totalNetRevenue = (googlePlaySummary?.totalNetAmount ?: java.math.BigDecimal.ZERO)
                .add(appStoreSummary?.totalNetAmount ?: java.math.BigDecimal.ZERO),
            googlePlayShare = googlePlayShare,
            appStoreShare = appStoreShare
        )
    }
    
    /**
     * 전체 대사 상태 결정
     */
    private fun determineOverallReconciliationStatus(
        googlePlayReconciliation: ReconciliationResult?,
        appStoreReconciliation: ReconciliationResult?
    ): ReconciliationStatus {
        val statuses = listOfNotNull(
            googlePlayReconciliation?.reconciliationStatus,
            appStoreReconciliation?.reconciliationStatus
        )
        
        return when {
            statuses.isEmpty() -> ReconciliationStatus.FAILED
            statuses.all { it == ReconciliationStatus.MATCHED } -> ReconciliationStatus.MATCHED
            statuses.any { it == ReconciliationStatus.FAILED } -> ReconciliationStatus.FAILED
            statuses.any { it == ReconciliationStatus.MAJOR_DISCREPANCY } -> ReconciliationStatus.MAJOR_DISCREPANCY
            else -> ReconciliationStatus.PARTIAL_MATCH
        }
    }
    
    /**
     * 정산 데이터 조회
     */
    fun getSettlementData(date: LocalDate, platform: Platform): List<SettlementData> {
        val key = "${date}_${platform.name}"
        return settlementData[key] ?: emptyList()
    }
    
    /**
     * 일일 요약 조회
     */
    fun getDailySettlementSummary(date: LocalDate, platform: Platform): DailySettlementSummary? {
        val key = "${date}_${platform.name}"
        return dailySettlementSummaries[key]
    }
    
    /**
     * 대사 결과 조회
     */
    fun getReconciliationResult(date: LocalDate, platform: Platform): ReconciliationResult? {
        val key = "${date}_${platform.name}"
        return reconciliationResults[key]
    }
    
    /**
     * 날짜 범위별 정산 통계
     */
    fun getSettlementStatistics(
        startDate: LocalDate,
        endDate: LocalDate
    ): SettlementPeriodStatistics {
        val allSummaries = dailySettlementSummaries.values.filter { summary ->
            summary.date >= startDate && summary.date <= endDate
        }
        
        val googlePlaySummaries = allSummaries.filter { it.platform == Platform.AOS }
        val appStoreSummaries = allSummaries.filter { it.platform == Platform.IOS }
        
        return SettlementPeriodStatistics(
            startDate = startDate,
            endDate = endDate,
            totalDays = allSummaries.map { it.date }.distinct().size,
            googlePlayDays = googlePlaySummaries.size,
            appStoreDays = appStoreSummaries.size,
            totalTransactions = allSummaries.sumOf { it.totalTransactions },
            totalGrossRevenue = allSummaries.fold(java.math.BigDecimal.ZERO) { acc, summary ->
                acc.add(summary.totalGrossAmount)
            },
            totalNetRevenue = allSummaries.fold(java.math.BigDecimal.ZERO) { acc, summary ->
                acc.add(summary.totalNetAmount)
            },
            averageDailyRevenue = if (allSummaries.isNotEmpty()) {
                allSummaries.fold(java.math.BigDecimal.ZERO) { acc, summary ->
                    acc.add(summary.totalGrossAmount)
                }.divide(java.math.BigDecimal(allSummaries.map { it.date }.distinct().size), 2, java.math.RoundingMode.HALF_UP)
            } else java.math.BigDecimal.ZERO
        )
    }
    
    // Mock 데이터 생성 메서드들
    private fun generateMockSettlementData(date: LocalDate, platform: Platform): List<SettlementData> {
        // 이전에 구현한 GooglePlaySettlementService와 AppStoreSettlementService의 Mock 데이터 생성 로직 활용
        return emptyList() // 간소화
    }
    
    private fun generateMockSettlementSummary(date: LocalDate, platform: Platform): DailySettlementSummary {
        return DailySettlementSummary(
            date = date,
            platform = platform,
            totalTransactions = if (platform == Platform.AOS) 14 else 12,
            totalGrossAmount = if (platform == Platform.AOS) java.math.BigDecimal("129.87") else java.math.BigDecimal("119.88"),
            totalPlatformFee = if (platform == Platform.AOS) java.math.BigDecimal("38.96") else java.math.BigDecimal("35.96"),
            totalNetAmount = if (platform == Platform.AOS) java.math.BigDecimal("77.91") else java.math.BigDecimal("75.13"),
            totalTaxAmount = java.math.BigDecimal("13.00"),
            purchaseCount = if (platform == Platform.AOS) 5 else 4,
            renewalCount = if (platform == Platform.AOS) 8 else 7,
            refundCount = 1,
            chargebackCount = 0,
            createdAt = LocalDateTime.now()
        )
    }
    
    private fun generateMockReconciliationResult(date: LocalDate, platform: Platform): ReconciliationResult {
        return ReconciliationResult(
            date = date,
            platform = platform,
            totalPlatformTransactions = if (platform == Platform.AOS) 14 else 12,
            totalInternalEvents = if (platform == Platform.AOS) 14 else 12,
            matchedTransactions = if (platform == Platform.AOS) 13 else 11,
            unmatchedPlatformTransactions = listOf("unmatched_${platform.name.lowercase()}_1"),
            unmatchedInternalEvents = listOf("unmatched_internal_${platform.name.lowercase()}_1"),
            discrepancies = emptyList(),
            reconciliationStatus = ReconciliationStatus.PARTIAL_MATCH,
            processedAt = LocalDateTime.now()
        )
    }
}