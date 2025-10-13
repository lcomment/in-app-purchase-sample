package com.example.service

import com.example.domain.Platform
import com.example.domain.settlement.reconciliation.ReconciliationStatus
import com.example.domain.payment.event.*
import com.example.domain.settlement.*
import com.example.domain.settlement.reconciliation.*
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * 대사 처리 오케스트레이터
 * 
 * 여러 플랫폼의 대사 작업을 조율하고 전체적인 대사 프로세스를 관리합니다.
 * - 일괄 대사 처리 스케줄링
 * - 대사 결과 통합 및 리포팅
 * - 예외 상황 모니터링 및 알림
 */
@Service
class ReconciliationOrchestrator(
    private val reconciliationEngine: ReconciliationEngine
) {
    
    // 대사 결과 저장소 (실제로는 데이터베이스 사용)
    private val reconciliationResults = ConcurrentHashMap<String, ReconciliationProcessResult>()
    private val reconciliationHistory = ConcurrentHashMap<String, List<ReconciliationProcessResult>>()
    
    /**
     * 특정 날짜의 모든 플랫폼 대사 처리
     */
    fun processAllPlatformsReconciliation(
        date: LocalDate,
        settlementCollectionService: DailySettlementCollectionService,
        googlePlayEventRepository: Any?, // PaymentEventRepository
        appStoreEventRepository: Any?     // IOSPaymentEventRepository
    ): CombinedReconciliationResult {
        
        val platformResults = mutableMapOf<Platform, ReconciliationProcessResult>()
        val errors = mutableListOf<String>()
        
        // Google Play 대사 처리
        try {
            val googlePlaySettlements = settlementCollectionService.getSettlementData(date, Platform.AOS)
            // 실제로는 타입 캐스팅: val googlePlayEvents = (googlePlayEventRepository as PaymentEventRepository).findByDateAndPlatform(date, Platform.AOS)
            val googlePlayEvents = emptyList<PaymentEvent>() // Mock
            
            if (googlePlaySettlements.isNotEmpty()) {
                val googlePlayResult = reconciliationEngine.processReconciliation(
                    date = date,
                    platform = Platform.AOS,
                    settlementData = googlePlaySettlements,
                    paymentEvents = googlePlayEvents
                )
                
                platformResults[Platform.AOS] = googlePlayResult
                storeReconciliationResult(date, Platform.AOS, googlePlayResult)
            }
        } catch (e: Exception) {
            errors.add("Google Play 대사 처리 실패: ${e.message}")
        }
        
        // App Store 대사 처리
        try {
            val appStoreSettlements = settlementCollectionService.getSettlementData(date, Platform.IOS)
            // 실제로는 타입 캐스팅: val appStoreEvents = (appStoreEventRepository as IOSPaymentEventRepository).findByDateAndPlatform(date, Platform.IOS)
            val appStoreEvents = emptyList<PaymentEvent>() // Mock
            
            if (appStoreSettlements.isNotEmpty()) {
                val appStoreResult = reconciliationEngine.processReconciliation(
                    date = date,
                    platform = Platform.IOS,
                    settlementData = appStoreSettlements,
                    paymentEvents = appStoreEvents
                )
                
                platformResults[Platform.IOS] = appStoreResult
                storeReconciliationResult(date, Platform.IOS, appStoreResult)
            }
        } catch (e: Exception) {
            errors.add("App Store 대사 처리 실패: ${e.message}")
        }
        
        // 통합 결과 생성
        val combinedResult = createCombinedResult(date, platformResults, errors)
        
        // 알림 필요 여부 확인
        if (shouldTriggerAlert(combinedResult)) {
            triggerReconciliationAlert(combinedResult)
        }
        
        return combinedResult
    }
    
    /**
     * 대사 결과 저장
     */
    private fun storeReconciliationResult(
        date: LocalDate,
        platform: Platform,
        result: ReconciliationProcessResult
    ) {
        val key = "${date}_${platform.name}"
        reconciliationResults[key] = result
        
        // 히스토리에 추가
        val historyKey = platform.name
        val currentHistory = reconciliationHistory[historyKey] ?: emptyList()
        reconciliationHistory[historyKey] = (currentHistory + result).takeLast(30) // 최근 30일만 보관
        
        println("대사 결과 저장 완료: $date $platform - ${result.finalStatus}")
    }
    
    /**
     * 통합 대사 결과 생성
     */
    private fun createCombinedResult(
        date: LocalDate,
        platformResults: Map<Platform, ReconciliationProcessResult>,
        errors: List<String>
    ): CombinedReconciliationResult {
        
        val totalSettlements = platformResults.values.sumOf { it.totalSettlementRecords }
        val totalEvents = platformResults.values.sumOf { it.totalEventRecords }
        val totalMatches = platformResults.values.sumOf { it.totalMatches }
        val totalUnresolvedDiscrepancies = platformResults.values.sumOf { it.unresolvedDiscrepancies }
        
        val overallStatus = determineOverallStatus(platformResults.values.toList())
        val overallMatchingRate = if (maxOf(totalSettlements, totalEvents) > 0) {
            totalMatches.toDouble() / maxOf(totalSettlements, totalEvents)
        } else 0.0
        
        // 플랫폼별 성능 비교
        val platformComparison = createPlatformComparison(platformResults)
        
        return CombinedReconciliationResult(
            date = date,
            platformResults = platformResults,
            totalSettlementRecords = totalSettlements,
            totalEventRecords = totalEvents,
            totalMatches = totalMatches,
            overallMatchingRate = overallMatchingRate,
            totalUnresolvedDiscrepancies = totalUnresolvedDiscrepancies,
            overallStatus = overallStatus,
            platformComparison = platformComparison,
            errors = errors,
            processedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 전체 대사 상태 결정
     */
    private fun determineOverallStatus(results: List<ReconciliationProcessResult>): ReconciliationStatus {
        if (results.isEmpty()) return ReconciliationStatus.FAILED
        
        return when {
            results.all { it.finalStatus == ReconciliationStatus.MATCHED } -> ReconciliationStatus.MATCHED
            results.any { it.finalStatus == ReconciliationStatus.FAILED } -> ReconciliationStatus.FAILED
            results.any { it.finalStatus == ReconciliationStatus.MAJOR_DISCREPANCY } -> ReconciliationStatus.MAJOR_DISCREPANCY
            else -> ReconciliationStatus.PARTIAL_MATCH
        }
    }
    
    /**
     * 플랫폼별 성능 비교 생성
     */
    private fun createPlatformComparison(
        platformResults: Map<Platform, ReconciliationProcessResult>
    ): PlatformComparisonResult {
        
        val googlePlayResult = platformResults[Platform.AOS]
        val appStoreResult = platformResults[Platform.IOS]
        
        return PlatformComparisonResult(
            googlePlayMetrics = googlePlayResult?.let {
                PlatformMetrics(
                    platform = Platform.AOS,
                    matchingRate = it.matchingRate,
                    autoResolutionRate = it.autoResolutionRate,
                    processingTimeMs = it.processingTimeMs,
                    status = it.finalStatus
                )
            },
            appStoreMetrics = appStoreResult?.let {
                PlatformMetrics(
                    platform = Platform.IOS,
                    matchingRate = it.matchingRate,
                    autoResolutionRate = it.autoResolutionRate,
                    processingTimeMs = it.processingTimeMs,
                    status = it.finalStatus
                )
            },
            betterPerformingPlatform = determineBetterPerformingPlatform(googlePlayResult, appStoreResult)
        )
    }
    
    /**
     * 더 나은 성능의 플랫폼 결정
     */
    private fun determineBetterPerformingPlatform(
        googlePlayResult: ReconciliationProcessResult?,
        appStoreResult: ReconciliationProcessResult?
    ): Platform? {
        if (googlePlayResult == null) return appStoreResult?.platform
        if (appStoreResult == null) return googlePlayResult.platform
        
        val googlePlayScore = calculatePerformanceScore(googlePlayResult)
        val appStoreScore = calculatePerformanceScore(appStoreResult)
        
        return when {
            googlePlayScore > appStoreScore -> Platform.AOS
            appStoreScore > googlePlayScore -> Platform.IOS
            else -> null // 동일한 성능
        }
    }
    
    /**
     * 성능 점수 계산
     */
    private fun calculatePerformanceScore(result: ReconciliationProcessResult): Double {
        var score = 0.0
        
        // 매칭률 (50% 가중치)
        score += result.matchingRate * 0.5
        
        // 자동 해결률 (30% 가중치)
        score += result.autoResolutionRate * 0.3
        
        // 처리 속도 (20% 가중치) - 빠를수록 높은 점수
        val processingSpeedScore = if (result.processingTimeMs > 0) {
            kotlin.math.min(1.0, 1000.0 / result.processingTimeMs)
        } else 1.0
        score += processingSpeedScore * 0.2
        
        return score
    }
    
    /**
     * 알림 필요 여부 확인
     */
    private fun shouldTriggerAlert(result: CombinedReconciliationResult): Boolean {
        return when {
            result.overallStatus == ReconciliationStatus.FAILED -> true
            result.overallStatus == ReconciliationStatus.MAJOR_DISCREPANCY -> true
            result.overallMatchingRate < 0.90 -> true
            result.totalUnresolvedDiscrepancies > 10 -> true
            result.errors.isNotEmpty() -> true
            else -> false
        }
    }
    
    /**
     * 대사 알림 트리거
     */
    private fun triggerReconciliationAlert(result: CombinedReconciliationResult) {
        val alertMessage = buildString {
            appendLine("🚨 대사 처리 알림 - ${result.date}")
            appendLine("전체 상태: ${result.overallStatus.description}")
            appendLine("매칭률: ${String.format("%.2f", result.overallMatchingRate * 100)}%")
            
            if (result.totalUnresolvedDiscrepancies > 0) {
                appendLine("미해결 불일치: ${result.totalUnresolvedDiscrepancies}건")
            }
            
            if (result.errors.isNotEmpty()) {
                appendLine("오류:")
                result.errors.forEach { error ->
                    appendLine("  - $error")
                }
            }
            
            appendLine("플랫폼별 상세:")
            result.platformResults.forEach { (platform, platformResult) ->
                appendLine("  ${platform.name}: ${platformResult.finalStatus.description} (매칭률: ${String.format("%.2f", platformResult.matchingRate * 100)}%)")
            }
        }
        
        // 실제로는 이메일, 슬랙, SMS 등으로 알림 발송
        println("ALERT: $alertMessage")
    }
    
    /**
     * 대사 이력 조회
     */
    fun getReconciliationHistory(
        platform: Platform,
        days: Int = 7
    ): List<ReconciliationProcessResult> {
        val historyKey = platform.name
        return reconciliationHistory[historyKey]?.takeLast(days) ?: emptyList()
    }
    
    /**
     * 대사 트렌드 분석
     */
    fun analyzeReconciliationTrend(
        platform: Platform,
        days: Int = 30
    ): ReconciliationTrendAnalysis {
        val history = getReconciliationHistory(platform, days)
        
        if (history.isEmpty()) {
            return ReconciliationTrendAnalysis(
                platform = platform,
                analyzedDays = 0,
                averageMatchingRate = 0.0,
                averageAutoResolutionRate = 0.0,
                averageProcessingTimeMs = 0L,
                statusDistribution = emptyMap(),
                trend = TrendDirection.STABLE
            )
        }
        
        val averageMatchingRate = history.map { it.matchingRate }.average()
        val averageAutoResolutionRate = history.map { it.autoResolutionRate }.average()
        val averageProcessingTime = history.map { it.processingTimeMs }.average().toLong()
        
        val statusDistribution = history.groupBy { it.finalStatus }
            .mapValues { (_, results) -> results.size }
        
        val trend = determineTrend(history)
        
        return ReconciliationTrendAnalysis(
            platform = platform,
            analyzedDays = history.size,
            averageMatchingRate = averageMatchingRate,
            averageAutoResolutionRate = averageAutoResolutionRate,
            averageProcessingTimeMs = averageProcessingTime,
            statusDistribution = statusDistribution,
            trend = trend
        )
    }
    
    /**
     * 트렌드 방향 결정
     */
    private fun determineTrend(history: List<ReconciliationProcessResult>): TrendDirection {
        if (history.size < 3) return TrendDirection.STABLE
        
        val recent = history.takeLast(3)
        val earlier = history.takeLast(6).take(3)
        
        val recentAverage = recent.map { it.matchingRate }.average()
        val earlierAverage = earlier.map { it.matchingRate }.average()
        
        return when {
            recentAverage > earlierAverage + 0.05 -> TrendDirection.IMPROVING
            recentAverage < earlierAverage - 0.05 -> TrendDirection.DEGRADING
            else -> TrendDirection.STABLE
        }
    }
}

// 결과 데이터 클래스들
data class CombinedReconciliationResult(
    val date: LocalDate,
    val platformResults: Map<Platform, ReconciliationProcessResult>,
    val totalSettlementRecords: Int,
    val totalEventRecords: Int,
    val totalMatches: Int,
    val overallMatchingRate: Double,
    val totalUnresolvedDiscrepancies: Int,
    val overallStatus: ReconciliationStatus,
    val platformComparison: PlatformComparisonResult,
    val errors: List<String>,
    val processedAt: LocalDateTime
)

data class PlatformComparisonResult(
    val googlePlayMetrics: PlatformMetrics?,
    val appStoreMetrics: PlatformMetrics?,
    val betterPerformingPlatform: Platform?
)

data class PlatformMetrics(
    val platform: Platform,
    val matchingRate: Double,
    val autoResolutionRate: Double,
    val processingTimeMs: Long,
    val status: ReconciliationStatus
)

data class ReconciliationTrendAnalysis(
    val platform: Platform,
    val analyzedDays: Int,
    val averageMatchingRate: Double,
    val averageAutoResolutionRate: Double,
    val averageProcessingTimeMs: Long,
    val statusDistribution: Map<ReconciliationStatus, Int>,
    val trend: TrendDirection
)

enum class TrendDirection(val description: String) {
    IMPROVING("개선 중"),
    STABLE("안정"),
    DEGRADING("악화 중")
}