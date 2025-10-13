package com.example.service

import com.example.domain.*
import com.example.domain.settlement.*
import com.example.domain.settlement.reconciliation.*
import com.example.domain.payment.event.*
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
                
                storeDailyData(date, Platform.AOS, mockGooglePlayData, mockGooglePlaySummary, mockGooglePlayReconciliation)
                
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
                    errors = listOf("Google Play 정산 데이터 수집 실패: ${e.message}")
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
                
                storeDailyData(date, Platform.IOS, mockAppStoreData, mockAppStoreSummary, mockAppStoreReconciliation)
                
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
                    errors = listOf("App Store 정산 데이터 수집 실패: ${e.message}")
                )
            }
        }
        
        return DailySettlementCollectionResult(
            date = date,
            platformResults = collectionResults,
            overallSuccess = collectionResults.values.all { it.success },
            totalDataCount = collectionResults.values.sumOf { it.dataCount },
            processedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 일일 정산 데이터 저장
     */
    private fun storeDailyData(
        date: LocalDate,
        platform: Platform,
        data: List<SettlementData>,
        summary: DailySettlementSummary,
        reconciliation: ReconciliationResult
    ) {
        val key = "${date}_${platform.name}"
        
        settlementData[key] = data
        dailySettlementSummaries[key] = summary
        reconciliationResults[key] = reconciliation
        
        println("Stored settlement data for $date $platform: ${data.size} records")
    }
    
    /**
     * 통합 일일 정산 보고서 생성
     */
    fun generateCombinedDailyReport(date: LocalDate): CombinedDailySettlementReport {
        val googlePlayKey = "${date}_${Platform.AOS.name}"
        val appStoreKey = "${date}_${Platform.IOS.name}"
        
        val googlePlaySummary = dailySettlementSummaries[googlePlayKey]
        val appStoreSummary = dailySettlementSummaries[appStoreKey]
        val googlePlayReconciliation = reconciliationResults[googlePlayKey]
        val appStoreReconciliation = reconciliationResults[appStoreKey]
        
        return CombinedDailySettlementReport(
            date = date,
            googlePlaySummary = googlePlaySummary,
            appStoreSummary = appStoreSummary,
            googlePlayReconciliation = googlePlayReconciliation,
            appStoreReconciliation = appStoreReconciliation,
            combinedMetrics = calculateCombinedMetrics(googlePlaySummary, appStoreSummary),
            overallReconciliationStatus = determineOverallReconciliationStatus(
                googlePlayReconciliation,
                appStoreReconciliation
            ),
            generatedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 통합 메트릭 계산
     */
    private fun calculateCombinedMetrics(
        googlePlaySummary: DailySettlementSummary?,
        appStoreSummary: DailySettlementSummary?
    ): CombinedDailyMetrics {
        val googlePlay = googlePlaySummary
        val appStore = appStoreSummary
        
        return CombinedDailyMetrics(
            totalTransactions = (googlePlay?.totalTransactions ?: 0) + (appStore?.totalTransactions ?: 0),
            totalGrossRevenue = (googlePlay?.totalGrossAmount ?: java.math.BigDecimal.ZERO)
                .add(appStore?.totalGrossAmount ?: java.math.BigDecimal.ZERO),
            totalPlatformFees = (googlePlay?.totalPlatformFee ?: java.math.BigDecimal.ZERO)
                .add(appStore?.totalPlatformFee ?: java.math.BigDecimal.ZERO),
            totalNetRevenue = (googlePlay?.totalNetAmount ?: java.math.BigDecimal.ZERO)
                .add(appStore?.totalNetAmount ?: java.math.BigDecimal.ZERO),
            googlePlayShare = if (googlePlay != null && appStore != null) {
                val total = googlePlay.totalGrossAmount.add(appStore.totalGrossAmount)
                if (total.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    googlePlay.totalGrossAmount.divide(total, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(java.math.BigDecimal("100"))
                } else java.math.BigDecimal.ZERO
            } else java.math.BigDecimal.ZERO,
            appStoreShare = if (googlePlay != null && appStore != null) {
                val total = googlePlay.totalGrossAmount.add(appStore.totalGrossAmount)
                if (total.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    appStore.totalGrossAmount.divide(total, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(java.math.BigDecimal("100"))
                } else java.math.BigDecimal.ZERO
            } else java.math.BigDecimal.ZERO
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

// 결과 데이터 클래스들
data class DailySettlementCollectionResult(
    val date: LocalDate,
    val platformResults: Map<Platform, SettlementCollectionResult>,
    val overallSuccess: Boolean,
    val totalDataCount: Int,
    val processedAt: LocalDateTime
)

data class SettlementCollectionResult(
    val platform: Platform,
    val success: Boolean,
    val dataCount: Int,
    val summary: DailySettlementSummary?,
    val reconciliation: ReconciliationResult?,
    val errors: List<String>
)

data class CombinedDailySettlementReport(
    val date: LocalDate,
    val googlePlaySummary: DailySettlementSummary?,
    val appStoreSummary: DailySettlementSummary?,
    val googlePlayReconciliation: ReconciliationResult?,
    val appStoreReconciliation: ReconciliationResult?,
    val combinedMetrics: CombinedDailyMetrics,
    val overallReconciliationStatus: ReconciliationStatus,
    val generatedAt: LocalDateTime
)

data class CombinedDailyMetrics(
    val totalTransactions: Int,
    val totalGrossRevenue: java.math.BigDecimal,
    val totalPlatformFees: java.math.BigDecimal,
    val totalNetRevenue: java.math.BigDecimal,
    val googlePlayShare: java.math.BigDecimal, // 백분율
    val appStoreShare: java.math.BigDecimal     // 백분율
)

data class SettlementPeriodStatistics(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalDays: Int,
    val googlePlayDays: Int,
    val appStoreDays: Int,
    val totalTransactions: Int,
    val totalGrossRevenue: java.math.BigDecimal,
    val totalNetRevenue: java.math.BigDecimal,
    val averageDailyRevenue: java.math.BigDecimal
)