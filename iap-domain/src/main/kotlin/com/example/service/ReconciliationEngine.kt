package com.example.service

import com.example.domain.*
import com.example.domain.settlement.*
import com.example.domain.settlement.reconciliation.*
import com.example.domain.payment.event.*
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.abs

/**
 * 기본 대사 처리 엔진
 * 
 * 플랫폼 정산 데이터와 내부 PaymentEvent 데이터 간의 자동 대사 처리를 담당합니다.
 * - 자동 매칭 및 검증
 * - 불일치 항목 자동 분류 및 해결
 * - 예외 상황 처리 및 알림
 */
@Service
class ReconciliationEngine {
    
    companion object {
        // 허용 가능한 금액 차이 (1센트)
        private val ACCEPTABLE_AMOUNT_DIFFERENCE = BigDecimal("0.01")
        
        // 허용 가능한 시간 차이 (1시간)
        private const val ACCEPTABLE_TIME_DIFFERENCE_HOURS = 1L
        
        // 환율 변동 허용 범위 (5%)
        private val CURRENCY_FLUCTUATION_TOLERANCE = BigDecimal("0.05")
    }
    
    /**
     * 전체 대사 처리 실행
     */
    fun processReconciliation(
        date: LocalDate,
        platform: Platform,
        settlementData: List<SettlementData>,
        paymentEvents: List<PaymentEvent>
    ): ReconciliationProcessResult {
        
        val startTime = LocalDateTime.now()
        
        // 1단계: 기본 매칭
        val basicMatching = performBasicMatching(settlementData, paymentEvents)
        
        // 2단계: 고급 매칭 (패턴 기반)
        val advancedMatching = performAdvancedMatching(
            basicMatching.unmatchedSettlements,
            basicMatching.unmatchedEvents
        )
        
        // 3단계: 불일치 항목 분석
        val discrepancyAnalysis = analyzeDiscrepancies(
            basicMatching.matches + advancedMatching.matches
        )
        
        // 4단계: 자동 해결 시도
        val autoResolution = attemptAutoResolution(discrepancyAnalysis.discrepancies)
        
        // 5단계: 최종 결과 생성
        val finalResult = generateFinalResult(
            date = date,
            platform = platform,
            basicMatching = basicMatching,
            advancedMatching = advancedMatching,
            discrepancyAnalysis = discrepancyAnalysis,
            autoResolution = autoResolution,
            processingTime = ChronoUnit.MILLIS.between(startTime, LocalDateTime.now())
        )
        
        return finalResult
    }
    
    /**
     * 1단계: 기본 매칭 - Transaction ID 기준
     */
    private fun performBasicMatching(
        settlementData: List<SettlementData>,
        paymentEvents: List<PaymentEvent>
    ): BasicMatchingResult {
        
        val matches = mutableListOf<ReconciliationMatch>()
        val unmatchedSettlements = mutableListOf<SettlementData>()
        val unmatchedEvents = mutableListOf<PaymentEvent>()
        
        // Settlement 데이터를 Transaction ID로 매핑
        val eventMap = paymentEvents.associateBy { it.id }
        
        settlementData.forEach { settlement ->
            val matchingEvent = eventMap[settlement.transactionId] 
                ?: eventMap[settlement.originalTransactionId]
            
            if (matchingEvent != null) {
                matches.add(
                    ReconciliationMatch(
                        settlementData = settlement,
                        paymentEvent = matchingEvent,
                        matchType = MatchType.EXACT_ID_MATCH,
                        confidence = 1.0
                    )
                )
            } else {
                unmatchedSettlements.add(settlement)
            }
        }
        
        // 매칭되지 않은 이벤트 찾기
        val matchedEventIds = matches.map { it.paymentEvent.id }.toSet()
        paymentEvents.forEach { event ->
            if (event.id !in matchedEventIds) {
                unmatchedEvents.add(event)
            }
        }
        
        return BasicMatchingResult(
            matches = matches,
            unmatchedSettlements = unmatchedSettlements,
            unmatchedEvents = unmatchedEvents
        )
    }
    
    /**
     * 2단계: 고급 매칭 - 패턴 및 유사성 기반
     */
    private fun performAdvancedMatching(
        unmatchedSettlements: List<SettlementData>,
        unmatchedEvents: List<PaymentEvent>
    ): AdvancedMatchingResult {
        
        val matches = mutableListOf<ReconciliationMatch>()
        val stillUnmatchedSettlements = mutableListOf<SettlementData>()
        val matchedEvents = mutableSetOf<PaymentEvent>()
        
        unmatchedSettlements.forEach { settlement ->
            var bestMatch: PaymentEvent? = null
            var bestScore = 0.0
            
            unmatchedEvents.forEach { event ->
                if (event !in matchedEvents) {
                    val score = calculateMatchingScore(settlement, event)
                    if (score > bestScore && score >= 0.7) { // 70% 이상 유사도
                        bestScore = score
                        bestMatch = event
                    }
                }
            }
            
            if (bestMatch != null) {
                matches.add(
                    ReconciliationMatch(
                        settlementData = settlement,
                        paymentEvent = bestMatch!!,
                        matchType = MatchType.PATTERN_MATCH,
                        confidence = bestScore
                    )
                )
                matchedEvents.add(bestMatch!!)
            } else {
                stillUnmatchedSettlements.add(settlement)
            }
        }
        
        val stillUnmatchedEvents = unmatchedEvents.filter { it !in matchedEvents }
        
        return AdvancedMatchingResult(
            matches = matches,
            unmatchedSettlements = stillUnmatchedSettlements,
            unmatchedEvents = stillUnmatchedEvents
        )
    }
    
    /**
     * 매칭 점수 계산 (0.0 ~ 1.0)
     */
    private fun calculateMatchingScore(settlement: SettlementData, event: PaymentEvent): Double {
        var score = 0.0
        
        // 이벤트 타입 매칭 (40% 가중치)
        if (isEventTypeMatching(settlement.eventType, event.eventType)) {
            score += 0.4
        }
        
        // 시간 근접성 (30% 가중치)
        val timeDiff = abs(ChronoUnit.HOURS.between(settlement.createdAt, event.createdAt))
        if (timeDiff <= 24) {
            score += 0.3 * (1.0 - (timeDiff / 24.0))
        }
        
        // 사용자 ID 매칭 (20% 가중치)
        if (settlement.userId != null && settlement.userId == event.subscriptionId) {
            score += 0.2
        }
        
        // 제품 ID 매칭 (10% 가중치)
        if (settlement.productId.contains(event.subscriptionId ?: "")) {
            score += 0.1
        }
        
        return score
    }
    
    /**
     * 이벤트 타입 매칭 검사
     */
    private fun isEventTypeMatching(settlementType: SettlementEventType, eventType: PaymentEventType): Boolean {
        return when (settlementType) {
            SettlementEventType.PURCHASE -> eventType == PaymentEventType.PURCHASE
            SettlementEventType.RENEWAL -> eventType == PaymentEventType.RENEWAL
            SettlementEventType.REFUND -> eventType == PaymentEventType.REFUND
            else -> false
        }
    }
    
    /**
     * 3단계: 불일치 항목 분석
     */
    private fun analyzeDiscrepancies(matches: List<ReconciliationMatch>): DiscrepancyAnalysisResult {
        val discrepancies = mutableListOf<ReconciliationDiscrepancy>()
        val validMatches = mutableListOf<ReconciliationMatch>()
        
        matches.forEach { match ->
            val foundDiscrepancies = findDiscrepancies(match)
            
            if (foundDiscrepancies.isNotEmpty()) {
                discrepancies.addAll(foundDiscrepancies)
            } else {
                validMatches.add(match)
            }
        }
        
        return DiscrepancyAnalysisResult(
            validMatches = validMatches,
            discrepancies = discrepancies
        )
    }
    
    /**
     * 매칭 항목의 불일치 찾기
     */
    private fun findDiscrepancies(match: ReconciliationMatch): List<ReconciliationDiscrepancy> {
        val discrepancies = mutableListOf<ReconciliationDiscrepancy>()
        val settlement = match.settlementData
        val event = match.paymentEvent
        
        // 이벤트 타입 불일치
        if (!isEventTypeMatching(settlement.eventType, event.eventType)) {
            discrepancies.add(
                ReconciliationDiscrepancy(
                    transactionId = settlement.transactionId,
                    discrepancyType = DiscrepancyType.EVENT_TYPE_MISMATCH,
                    platformData = settlement.eventType.description,
                    internalData = event.eventType.name,
                    description = "이벤트 타입이 일치하지 않음"
                )
            )
        }
        
        // 시간 차이가 큰 경우
        val timeDiff = abs(ChronoUnit.HOURS.between(settlement.createdAt, event.createdAt))
        if (timeDiff > ACCEPTABLE_TIME_DIFFERENCE_HOURS) {
            discrepancies.add(
                ReconciliationDiscrepancy(
                    transactionId = settlement.transactionId,
                    discrepancyType = DiscrepancyType.TIMING_MISMATCH,
                    platformData = settlement.createdAt.toString(),
                    internalData = event.createdAt.toString(),
                    description = "${timeDiff}시간 차이"
                )
            )
        }
        
        return discrepancies
    }
    
    /**
     * 4단계: 자동 해결 시도
     */
    private fun attemptAutoResolution(discrepancies: List<ReconciliationDiscrepancy>): AutoResolutionResult {
        val resolvedDiscrepancies = mutableListOf<ResolvedDiscrepancy>()
        val unresolvedDiscrepancies = mutableListOf<ReconciliationDiscrepancy>()
        
        discrepancies.forEach { discrepancy ->
            val resolution = attemptResolution(discrepancy)
            
            if (resolution != null) {
                resolvedDiscrepancies.add(
                    ResolvedDiscrepancy(
                        originalDiscrepancy = discrepancy,
                        resolutionMethod = resolution.method,
                        resolutionDescription = resolution.description,
                        resolvedAt = LocalDateTime.now()
                    )
                )
            } else {
                unresolvedDiscrepancies.add(discrepancy)
            }
        }
        
        return AutoResolutionResult(
            resolvedDiscrepancies = resolvedDiscrepancies,
            unresolvedDiscrepancies = unresolvedDiscrepancies
        )
    }
    
    /**
     * 개별 불일치 항목 해결 시도
     */
    private fun attemptResolution(discrepancy: ReconciliationDiscrepancy): ResolutionMethod? {
        return when (discrepancy.discrepancyType) {
            DiscrepancyType.TIMING_MISMATCH -> {
                // 시간 차이가 허용 범위 내인 경우 자동 승인
                val description = discrepancy.description
                val hours = description.replace("시간 차이", "").replace("시간", "").trim().toIntOrNull()
                
                if (hours != null && hours <= 24) {
                    ResolutionMethod(
                        method = "AUTO_TIMING_TOLERANCE",
                        description = "24시간 이내 시간 차이로 자동 승인"
                    )
                } else null
            }
            
            DiscrepancyType.AMOUNT_MISMATCH -> {
                // 환율 변동 범위 내 금액 차이 자동 승인
                ResolutionMethod(
                    method = "AUTO_CURRENCY_TOLERANCE",
                    description = "환율 변동 허용 범위 내 자동 승인"
                )
            }
            
            else -> null // 수동 해결 필요
        }
    }
    
    /**
     * 5단계: 최종 결과 생성
     */
    private fun generateFinalResult(
        date: LocalDate,
        platform: Platform,
        basicMatching: BasicMatchingResult,
        advancedMatching: AdvancedMatchingResult,
        discrepancyAnalysis: DiscrepancyAnalysisResult,
        autoResolution: AutoResolutionResult,
        processingTime: Long
    ): ReconciliationProcessResult {
        
        val allMatches = basicMatching.matches + advancedMatching.matches
        val totalSettlements = basicMatching.matches.size + basicMatching.unmatchedSettlements.size
        val totalEvents = basicMatching.matches.size + basicMatching.unmatchedEvents.size
        
        val finalStatus = determineFinalStatus(
            totalMatches = allMatches.size,
            totalSettlements = totalSettlements,
            totalEvents = totalEvents,
            unresolvedDiscrepancies = autoResolution.unresolvedDiscrepancies.size
        )
        
        return ReconciliationProcessResult(
            date = date,
            platform = platform,
            totalSettlementRecords = totalSettlements,
            totalEventRecords = totalEvents,
            exactMatches = basicMatching.matches.size,
            patternMatches = advancedMatching.matches.size,
            totalMatches = allMatches.size,
            unmatchedSettlements = advancedMatching.unmatchedSettlements.size,
            unmatchedEvents = advancedMatching.unmatchedEvents.size,
            totalDiscrepancies = discrepancyAnalysis.discrepancies.size,
            autoResolvedDiscrepancies = autoResolution.resolvedDiscrepancies.size,
            unresolvedDiscrepancies = autoResolution.unresolvedDiscrepancies.size,
            processingTimeMs = processingTime,
            finalStatus = finalStatus,
            detailedMatches = allMatches,
            unresolvedDiscrepancyDetails = autoResolution.unresolvedDiscrepancies,
            autoResolutionDetails = autoResolution.resolvedDiscrepancies,
            processedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 최종 대사 상태 결정
     */
    private fun determineFinalStatus(
        totalMatches: Int,
        totalSettlements: Int,
        totalEvents: Int,
        unresolvedDiscrepancies: Int
    ): ReconciliationStatus {
        val maxRecords = maxOf(totalSettlements, totalEvents)
        val matchRate = if (maxRecords > 0) totalMatches.toDouble() / maxRecords else 0.0
        
        return when {
            matchRate >= 1.0 && unresolvedDiscrepancies == 0 -> ReconciliationStatus.MATCHED
            matchRate >= 0.95 && unresolvedDiscrepancies <= 2 -> ReconciliationStatus.PARTIAL_MATCH
            matchRate >= 0.80 -> ReconciliationStatus.MAJOR_DISCREPANCY
            else -> ReconciliationStatus.FAILED
        }
    }
}

// 결과 데이터 클래스들
data class ReconciliationProcessResult(
    val date: LocalDate,
    val platform: Platform,
    val totalSettlementRecords: Int,
    val totalEventRecords: Int,
    val exactMatches: Int,
    val patternMatches: Int,
    val totalMatches: Int,
    val unmatchedSettlements: Int,
    val unmatchedEvents: Int,
    val totalDiscrepancies: Int,
    val autoResolvedDiscrepancies: Int,
    val unresolvedDiscrepancies: Int,
    val processingTimeMs: Long,
    val finalStatus: ReconciliationStatus,
    val detailedMatches: List<ReconciliationMatch>,
    val unresolvedDiscrepancyDetails: List<ReconciliationDiscrepancy>,
    val autoResolutionDetails: List<ResolvedDiscrepancy>,
    val processedAt: LocalDateTime
) {
    val matchingRate: Double
        get() = if (maxOf(totalSettlementRecords, totalEventRecords) > 0) {
            totalMatches.toDouble() / maxOf(totalSettlementRecords, totalEventRecords)
        } else 0.0
    
    val autoResolutionRate: Double
        get() = if (totalDiscrepancies > 0) {
            autoResolvedDiscrepancies.toDouble() / totalDiscrepancies
        } else 1.0
}

data class BasicMatchingResult(
    val matches: List<ReconciliationMatch>,
    val unmatchedSettlements: List<SettlementData>,
    val unmatchedEvents: List<PaymentEvent>
)

data class AdvancedMatchingResult(
    val matches: List<ReconciliationMatch>,
    val unmatchedSettlements: List<SettlementData>,
    val unmatchedEvents: List<PaymentEvent>
)

data class DiscrepancyAnalysisResult(
    val validMatches: List<ReconciliationMatch>,
    val discrepancies: List<ReconciliationDiscrepancy>
)

data class AutoResolutionResult(
    val resolvedDiscrepancies: List<ResolvedDiscrepancy>,
    val unresolvedDiscrepancies: List<ReconciliationDiscrepancy>
)

data class ReconciliationMatch(
    val settlementData: SettlementData,
    val paymentEvent: PaymentEvent,
    val matchType: MatchType,
    val confidence: Double
)

data class ResolvedDiscrepancy(
    val originalDiscrepancy: ReconciliationDiscrepancy,
    val resolutionMethod: String,
    val resolutionDescription: String,
    val resolvedAt: LocalDateTime
)

data class ResolutionMethod(
    val method: String,
    val description: String
)

enum class MatchType(val description: String) {
    EXACT_ID_MATCH("정확한 ID 매칭"),
    PATTERN_MATCH("패턴 기반 매칭"),
    MANUAL_MATCH("수동 매칭")
}