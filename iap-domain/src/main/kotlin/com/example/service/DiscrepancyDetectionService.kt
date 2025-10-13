package com.example.service

import com.example.domain.*
import com.example.domain.settlement.*
import com.example.domain.settlement.reconciliation.*
import com.example.service.reconciliation.results.ReconciliationProcessResult
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 정산 불일치 탐지 및 알림 서비스
 * 
 * 실시간으로 정산 불일치를 탐지하고 적절한 알림을 발송합니다.
 * - 실시간 불일치 모니터링
 * - 임계값 기반 알림 트리거
 * - 불일치 패턴 분석
 * - 알림 채널별 발송
 */
@Service
class DiscrepancyDetectionService {
    
    // 불일치 이력 저장소 (실제로는 데이터베이스 사용)
    private val discrepancyHistory = ConcurrentHashMap<String, List<DetectedDiscrepancy>>()
    private val alertHistory = ConcurrentHashMap<String, List<DiscrepancyAlert>>()
    
    companion object {
        // 알림 임계값 설정
        private val CRITICAL_AMOUNT_THRESHOLD = BigDecimal("1000.00") // $1000 이상 금액 불일치
        private const val CRITICAL_DISCREPANCY_COUNT = 10 // 10건 이상 불일치
        private const val CRITICAL_MATCHING_RATE = 0.85 // 85% 미만 매칭률
        private const val ALERT_COOLDOWN_MINUTES = 30 // 알림 재발송 방지 (30분)
    }
    
    /**
     * 대사 결과 기반 불일치 탐지
     */
    fun detectDiscrepancies(
        reconciliationResult: ReconciliationProcessResult
    ): DiscrepancyDetectionResult {
        
        val detectedDiscrepancies = mutableListOf<DetectedDiscrepancy>()
        val alerts = mutableListOf<DiscrepancyAlert>()
        
        // 1. 매칭률 기반 탐지
        if (reconciliationResult.matchingRate < CRITICAL_MATCHING_RATE) {
            val discrepancy = DetectedDiscrepancy(
                id = UUID.randomUUID().toString(),
                type = DiscrepancyDetectionType.LOW_MATCHING_RATE,
                severity = DiscrepancySeverity.HIGH,
                platform = reconciliationResult.platform,
                date = reconciliationResult.date,
                description = "매칭률이 임계값(${CRITICAL_MATCHING_RATE * 100}%) 미만: ${String.format("%.2f", reconciliationResult.matchingRate * 100)}%",
                affectedRecords = reconciliationResult.totalSettlementRecords - reconciliationResult.totalMatches,
                estimatedImpact = calculateImpactAmount(reconciliationResult),
                detectedAt = LocalDateTime.now(),
                metadata = mapOf(
                    "matchingRate" to reconciliationResult.matchingRate.toString(),
                    "totalRecords" to reconciliationResult.totalSettlementRecords.toString(),
                    "matchedRecords" to reconciliationResult.totalMatches.toString()
                )
            )
            
            detectedDiscrepancies.add(discrepancy)
            
            if (shouldTriggerAlert(discrepancy)) {
                alerts.add(createAlert(discrepancy))
            }
        }
        
        // 2. 미해결 불일치 개수 기반 탐지
        if (reconciliationResult.unresolvedDiscrepancies >= CRITICAL_DISCREPANCY_COUNT) {
            val discrepancy = DetectedDiscrepancy(
                id = UUID.randomUUID().toString(),
                type = DiscrepancyDetectionType.HIGH_UNRESOLVED_COUNT,
                severity = DiscrepancySeverity.MEDIUM,
                platform = reconciliationResult.platform,
                date = reconciliationResult.date,
                description = "미해결 불일치가 임계값(${CRITICAL_DISCREPANCY_COUNT}건) 이상: ${reconciliationResult.unresolvedDiscrepancies}건",
                affectedRecords = reconciliationResult.unresolvedDiscrepancies,
                estimatedImpact = BigDecimal.ZERO, // 개별 불일치 상세 분석 필요
                detectedAt = LocalDateTime.now(),
                metadata = mapOf(
                    "unresolvedCount" to reconciliationResult.unresolvedDiscrepancies.toString(),
                    "autoResolvedCount" to reconciliationResult.autoResolvedDiscrepancies.toString()
                )
            )
            
            detectedDiscrepancies.add(discrepancy)
            
            if (shouldTriggerAlert(discrepancy)) {
                alerts.add(createAlert(discrepancy))
            }
        }
        
        // 3. 개별 불일치 항목 분석
        reconciliationResult.unresolvedDiscrepancyDetails.forEach { discrepancy ->
            val detectedDiscrepancy = analyzeIndividualDiscrepancy(discrepancy, reconciliationResult)
            if (detectedDiscrepancy != null) {
                detectedDiscrepancies.add(detectedDiscrepancy)
                
                if (shouldTriggerAlert(detectedDiscrepancy)) {
                    alerts.add(createAlert(detectedDiscrepancy))
                }
            }
        }
        
        // 4. 패턴 기반 탐지
        val patternDiscrepancies = detectPatterns(reconciliationResult)
        detectedDiscrepancies.addAll(patternDiscrepancies)
        
        patternDiscrepancies.forEach { discrepancy ->
            if (shouldTriggerAlert(discrepancy)) {
                alerts.add(createAlert(discrepancy))
            }
        }
        
        // 결과 저장
        storeDetectionResults(reconciliationResult.date, reconciliationResult.platform, detectedDiscrepancies)
        
        // 알림 발송
        alerts.forEach { alert ->
            sendAlert(alert)
        }
        
        return DiscrepancyDetectionResult(
            date = reconciliationResult.date,
            platform = reconciliationResult.platform,
            detectedDiscrepancies = detectedDiscrepancies,
            triggeredAlerts = alerts,
            totalSeverityScore = calculateSeverityScore(detectedDiscrepancies),
            recommendedActions = generateRecommendations(detectedDiscrepancies),
            processedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 개별 불일치 항목 분석
     */
    private fun analyzeIndividualDiscrepancy(
        discrepancy: ReconciliationDiscrepancy,
        reconciliationResult: ReconciliationProcessResult
    ): DetectedDiscrepancy? {
        
        // 금액 불일치의 경우 임계값 확인
        if (discrepancy.discrepancyType == DiscrepancyType.AMOUNT_MISMATCH) {
            val amountDifference = extractAmountFromDescription(discrepancy.description)
            
            if (amountDifference >= CRITICAL_AMOUNT_THRESHOLD) {
                return DetectedDiscrepancy(
                    id = UUID.randomUUID().toString(),
                    type = DiscrepancyDetectionType.LARGE_AMOUNT_MISMATCH,
                    severity = DiscrepancySeverity.CRITICAL,
                    platform = reconciliationResult.platform,
                    date = reconciliationResult.date,
                    description = "대량 금액 불일치 탐지: $${amountDifference} (거래 ID: ${discrepancy.transactionId})",
                    affectedRecords = 1,
                    estimatedImpact = amountDifference,
                    detectedAt = LocalDateTime.now(),
                    metadata = mapOf(
                        "transactionId" to discrepancy.transactionId,
                        "platformData" to (discrepancy.platformData ?: ""),
                        "internalData" to (discrepancy.internalData ?: ""),
                        "originalDiscrepancyType" to discrepancy.discrepancyType.description
                    )
                )
            }
        }
        
        return null
    }
    
    /**
     * 패턴 기반 불일치 탐지
     */
    private fun detectPatterns(reconciliationResult: ReconciliationProcessResult): List<DetectedDiscrepancy> {
        val patterns = mutableListOf<DetectedDiscrepancy>()
        
        // 연속적인 불일치 패턴 탐지
        val recentHistory = getRecentDiscrepancyHistory(reconciliationResult.platform, 7)
        
        if (recentHistory.size >= 3) {
            val recentFailureRate = recentHistory.count { it.any { discrepancy -> 
                discrepancy.severity in listOf(DiscrepancySeverity.HIGH, DiscrepancySeverity.CRITICAL) 
            } }.toDouble() / recentHistory.size
            
            if (recentFailureRate >= 0.5) { // 50% 이상이 심각한 불일치
                patterns.add(
                    DetectedDiscrepancy(
                        id = UUID.randomUUID().toString(),
                        type = DiscrepancyDetectionType.RECURRING_PATTERN,
                        severity = DiscrepancySeverity.HIGH,
                        platform = reconciliationResult.platform,
                        date = reconciliationResult.date,
                        description = "최근 ${recentHistory.size}일 중 ${String.format("%.0f", recentFailureRate * 100)}%에서 심각한 불일치 발생",
                        affectedRecords = 0,
                        estimatedImpact = BigDecimal.ZERO,
                        detectedAt = LocalDateTime.now(),
                        metadata = mapOf(
                            "patternDays" to recentHistory.size.toString(),
                            "failureRate" to recentFailureRate.toString(),
                            "patternType" to "RECURRING_HIGH_SEVERITY"
                        )
                    )
                )
            }
        }
        
        return patterns
    }
    
    /**
     * 임계값 기반 금액 추출
     */
    private fun extractAmountFromDescription(description: String): BigDecimal {
        // 간단한 금액 추출 로직 (실제로는 더 정교한 파싱 필요)
        val regex = """\$?(\d+\.?\d*)""".toRegex()
        val matchResult = regex.find(description)
        
        return matchResult?.groups?.get(1)?.value?.let {
            try {
                BigDecimal(it)
            } catch (e: Exception) {
                BigDecimal.ZERO
            }
        } ?: BigDecimal.ZERO
    }
    
    /**
     * 영향 금액 계산
     */
    private fun calculateImpactAmount(reconciliationResult: ReconciliationProcessResult): BigDecimal {
        // 평균 거래 금액을 기준으로 추정 (실제로는 실제 거래 금액 사용)
        val averageTransactionAmount = BigDecimal("9.99") // Mock
        return averageTransactionAmount.multiply(
            BigDecimal(reconciliationResult.totalSettlementRecords - reconciliationResult.totalMatches)
        )
    }
    
    /**
     * 알림 트리거 여부 결정
     */
    private fun shouldTriggerAlert(discrepancy: DetectedDiscrepancy): Boolean {
        // 쿨다운 기간 확인
        if (isInCooldownPeriod(discrepancy)) {
            return false
        }
        
        // 심각도 기반 필터링
        return when (discrepancy.severity) {
            DiscrepancySeverity.CRITICAL -> true
            DiscrepancySeverity.HIGH -> true
            DiscrepancySeverity.MEDIUM -> discrepancy.affectedRecords >= 5
            DiscrepancySeverity.LOW -> false
        }
    }
    
    /**
     * 쿨다운 기간 확인
     */
    private fun isInCooldownPeriod(discrepancy: DetectedDiscrepancy): Boolean {
        val platformKey = "${discrepancy.platform.name}_${discrepancy.type.name}"
        val recentAlerts = alertHistory[platformKey] ?: return false
        
        val cutoffTime = LocalDateTime.now().minusMinutes(ALERT_COOLDOWN_MINUTES.toLong())
        return recentAlerts.any { it.sentAt.isAfter(cutoffTime) }
    }
    
    /**
     * 알림 생성
     */
    private fun createAlert(discrepancy: DetectedDiscrepancy): DiscrepancyAlert {
        return DiscrepancyAlert(
            id = UUID.randomUUID().toString(),
            discrepancyId = discrepancy.id,
            alertType = mapSeverityToAlertType(discrepancy.severity),
            platform = discrepancy.platform,
            title = generateAlertTitle(discrepancy),
            message = generateAlertMessage(discrepancy),
            severity = discrepancy.severity,
            recipients = determineRecipients(discrepancy),
            channels = determineAlertChannels(discrepancy.severity),
            createdAt = LocalDateTime.now(),
            sentAt = LocalDateTime.now(),
            metadata = discrepancy.metadata
        )
    }
    
    /**
     * 알림 제목 생성
     */
    private fun generateAlertTitle(discrepancy: DetectedDiscrepancy): String {
        return when (discrepancy.type) {
            DiscrepancyDetectionType.LOW_MATCHING_RATE -> 
                "🔴 ${discrepancy.platform.name} 매칭률 저하 경고"
            DiscrepancyDetectionType.HIGH_UNRESOLVED_COUNT -> 
                "⚠️ ${discrepancy.platform.name} 미해결 불일치 다수 발생"
            DiscrepancyDetectionType.LARGE_AMOUNT_MISMATCH -> 
                "🚨 ${discrepancy.platform.name} 대량 금액 불일치 탐지"
            DiscrepancyDetectionType.RECURRING_PATTERN -> 
                "📊 ${discrepancy.platform.name} 반복적 불일치 패턴 탐지"
            else -> "❌ ${discrepancy.platform.name} 정산 불일치 탐지"
        }
    }
    
    /**
     * 알림 메시지 생성
     */
    private fun generateAlertMessage(discrepancy: DetectedDiscrepancy): String {
        return buildString {
            appendLine("📅 날짜: ${discrepancy.date}")
            appendLine("🏷️ 플랫폼: ${discrepancy.platform.name}")
            appendLine("📝 설명: ${discrepancy.description}")
            appendLine("📊 영향 범위: ${discrepancy.affectedRecords}건")
            
            if (discrepancy.estimatedImpact > BigDecimal.ZERO) {
                appendLine("💰 예상 영향 금액: $${discrepancy.estimatedImpact}")
            }
            
            appendLine("⏰ 탐지 시간: ${discrepancy.detectedAt}")
            
            when (discrepancy.severity) {
                DiscrepancySeverity.CRITICAL -> appendLine("🆘 즉시 대응 필요")
                DiscrepancySeverity.HIGH -> appendLine("⚡ 신속한 확인 필요")
                DiscrepancySeverity.MEDIUM -> appendLine("👀 검토 권장")
                DiscrepancySeverity.LOW -> appendLine("📋 모니터링 계속")
            }
        }
    }
    
    /**
     * 수신자 결정
     */
    private fun determineRecipients(discrepancy: DetectedDiscrepancy): List<String> {
        return when (discrepancy.severity) {
            DiscrepancySeverity.CRITICAL -> listOf(
                "finance-team@company.com",
                "tech-lead@company.com",
                "ceo@company.com"
            )
            DiscrepancySeverity.HIGH -> listOf(
                "finance-team@company.com",
                "tech-lead@company.com"
            )
            DiscrepancySeverity.MEDIUM -> listOf(
                "finance-team@company.com"
            )
            DiscrepancySeverity.LOW -> listOf(
                "finance-team@company.com"
            )
        }
    }
    
    /**
     * 알림 채널 결정
     */
    private fun determineAlertChannels(severity: DiscrepancySeverity): List<AlertChannel> {
        return when (severity) {
            DiscrepancySeverity.CRITICAL -> listOf(
                AlertChannel.EMAIL,
                AlertChannel.SLACK,
                AlertChannel.SMS,
                AlertChannel.WEBHOOK
            )
            DiscrepancySeverity.HIGH -> listOf(
                AlertChannel.EMAIL,
                AlertChannel.SLACK,
                AlertChannel.WEBHOOK
            )
            DiscrepancySeverity.MEDIUM -> listOf(
                AlertChannel.EMAIL,
                AlertChannel.SLACK
            )
            DiscrepancySeverity.LOW -> listOf(
                AlertChannel.EMAIL
            )
        }
    }
    
    /**
     * 심각도를 알림 타입으로 매핑
     */
    private fun mapSeverityToAlertType(severity: DiscrepancySeverity): AlertType {
        return when (severity) {
            DiscrepancySeverity.CRITICAL -> AlertType.CRITICAL
            DiscrepancySeverity.HIGH -> AlertType.WARNING
            DiscrepancySeverity.MEDIUM -> AlertType.INFO
            DiscrepancySeverity.LOW -> AlertType.INFO
        }
    }
    
    /**
     * 알림 발송
     */
    private fun sendAlert(alert: DiscrepancyAlert) {
        // 알림 이력에 저장
        val platformKey = "${alert.platform.name}_${alert.alertType.name}"
        val currentHistory = alertHistory[platformKey] ?: emptyList()
        alertHistory[platformKey] = (currentHistory + alert).takeLast(50)
        
        // 각 채널별로 알림 발송
        alert.channels.forEach { channel ->
            when (channel) {
                AlertChannel.EMAIL -> {
                    //TODO: 이메일 발송 구현
                    println("EMAIL 알림 발송: ${alert.title}")
                }
                AlertChannel.SLACK -> {
                    //TODO: 슬랙 메시지 발송 구현
                    println("SLACK 알림 발송: ${alert.title}")
                }
                AlertChannel.SMS -> {
                    //TODO: SMS 발송 구현
                    println("SMS 알림 발송: ${alert.title}")
                }
                AlertChannel.WEBHOOK -> {
                    //TODO: 웹훅 호출 구현
                    println("WEBHOOK 알림 발송: ${alert.title}")
                }
                AlertChannel.PUSH_NOTIFICATION -> {
                    //TODO: 푸시 알림 발송 구현
                    println("PUSH 알림 발송: ${alert.title}")
                }
            }
        }
        
        println("알림 발송 완료: ${alert.id} - ${alert.title}")
    }
    
    /**
     * 탐지 결과 저장
     */
    private fun storeDetectionResults(
        date: LocalDate,
        platform: Platform,
        discrepancies: List<DetectedDiscrepancy>
    ) {
        val key = "${date}_${platform.name}"
        discrepancyHistory[key] = discrepancies
        
        println("불일치 탐지 결과 저장: $date $platform - ${discrepancies.size}건")
    }
    
    /**
     * 최근 불일치 이력 조회
     */
    private fun getRecentDiscrepancyHistory(platform: Platform, days: Int): List<List<DetectedDiscrepancy>> {
        val result = mutableListOf<List<DetectedDiscrepancy>>()
        
        for (i in 1..days) {
            val date = LocalDate.now().minusDays(i.toLong())
            val key = "${date}_${platform.name}"
            val dayDiscrepancies = discrepancyHistory[key] ?: emptyList()
            result.add(dayDiscrepancies)
        }
        
        return result
    }
    
    /**
     * 심각도 점수 계산
     */
    private fun calculateSeverityScore(discrepancies: List<DetectedDiscrepancy>): Int {
        return discrepancies.fold(0) { acc, discrepancy ->
            acc + when (discrepancy.severity) {
                DiscrepancySeverity.CRITICAL -> 10
                DiscrepancySeverity.HIGH -> 5
                DiscrepancySeverity.MEDIUM -> 2
                DiscrepancySeverity.LOW -> 1
            }
        }
    }
    
    /**
     * 권장 조치사항 생성
     */
    private fun generateRecommendations(discrepancies: List<DetectedDiscrepancy>): List<String> {
        val recommendations = mutableListOf<String>()
        
        discrepancies.forEach { discrepancy ->
            when (discrepancy.type) {
                DiscrepancyDetectionType.LOW_MATCHING_RATE -> {
                    recommendations.add("데이터 매핑 규칙 검토 및 개선")
                    recommendations.add("플랫폼 API 연동 상태 확인")
                }
                DiscrepancyDetectionType.HIGH_UNRESOLVED_COUNT -> {
                    recommendations.add("수동 대사 작업 수행")
                    recommendations.add("자동 해결 규칙 추가 검토")
                }
                DiscrepancyDetectionType.LARGE_AMOUNT_MISMATCH -> {
                    recommendations.add("해당 거래 즉시 수동 검증")
                    recommendations.add("플랫폼 정산 데이터 재확인")
                }
                DiscrepancyDetectionType.RECURRING_PATTERN -> {
                    recommendations.add("시스템 정합성 점검")
                    recommendations.add("데이터 동기화 프로세스 개선")
                }
                else -> {
                    recommendations.add("상세 조사 및 원인 분석")
                }
            }
        }
        
        return recommendations.distinct()
    }
    
    /**
     * 알림 이력 조회
     */
    fun getAlertHistory(platform: Platform, days: Int = 7): List<DiscrepancyAlert> {
        val results = mutableListOf<DiscrepancyAlert>()
        
        alertHistory.values.forEach { alerts ->
            alerts.filter { 
                it.platform == platform && 
                it.sentAt.isAfter(LocalDateTime.now().minusDays(days.toLong()))
            }.let { results.addAll(it) }
        }
        
        return results.sortedByDescending { it.sentAt }
    }
}

// 데이터 클래스들
data class DiscrepancyDetectionResult(
    val date: LocalDate,
    val platform: Platform,
    val detectedDiscrepancies: List<DetectedDiscrepancy>,
    val triggeredAlerts: List<DiscrepancyAlert>,
    val totalSeverityScore: Int,
    val recommendedActions: List<String>,
    val processedAt: LocalDateTime
)

data class DetectedDiscrepancy(
    val id: String,
    val type: DiscrepancyDetectionType,
    val severity: DiscrepancySeverity,
    val platform: Platform,
    val date: LocalDate,
    val description: String,
    val affectedRecords: Int,
    val estimatedImpact: BigDecimal,
    val detectedAt: LocalDateTime,
    val metadata: Map<String, String>
)

data class DiscrepancyAlert(
    val id: String,
    val discrepancyId: String,
    val alertType: AlertType,
    val platform: Platform,
    val title: String,
    val message: String,
    val severity: DiscrepancySeverity,
    val recipients: List<String>,
    val channels: List<AlertChannel>,
    val createdAt: LocalDateTime,
    val sentAt: LocalDateTime,
    val metadata: Map<String, String>
)

enum class DiscrepancyDetectionType(val description: String) {
    LOW_MATCHING_RATE("낮은 매칭률"),
    HIGH_UNRESOLVED_COUNT("높은 미해결 불일치 수"),
    LARGE_AMOUNT_MISMATCH("대량 금액 불일치"),
    RECURRING_PATTERN("반복적 불일치 패턴"),
    SYSTEM_ERROR("시스템 오류"),
    DATA_QUALITY_ISSUE("데이터 품질 문제")
}

enum class DiscrepancySeverity(val description: String, val level: Int) {
    LOW("낮음", 1),
    MEDIUM("보통", 2),
    HIGH("높음", 3),
    CRITICAL("치명적", 4)
}

enum class AlertType(val description: String) {
    INFO("정보"),
    WARNING("경고"),
    CRITICAL("치명적")
}

enum class AlertChannel(val description: String) {
    EMAIL("이메일"),
    SLACK("슬랙"),
    SMS("SMS"),
    WEBHOOK("웹훅"),
    PUSH_NOTIFICATION("푸시 알림")
}