package com.example.service

import com.example.domain.Platform
import com.example.domain.settlement.reconciliation.ReconciliationStatus
import com.example.service.alert.AlertNotificationService
import com.example.service.alert.requests.NotificationRequest
import com.example.service.alert.configuration.AlertChannel
import com.example.service.alert.priority.NotificationPriority
import com.example.service.reconciliation.results.ReconciliationProcessResult
import com.example.service.settlement.results.SettlementPeriodStatistics
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * 일일 정산 스케줄러
 * 
 * 매일 자동으로 정산 프로세스를 실행합니다.
 * - 정해진 시간에 자동 실행
 * - 실패 시 재시도 로직
 * - 진행 상황 모니터링
 * - 완료 후 리포팅
 */
@Service
class DailySettlementScheduler(
    private val dailySettlementCollectionService: com.example.service.settlement.DailySettlementCollectionService,
    private val reconciliationOrchestrator: ReconciliationOrchestrator,
    private val discrepancyDetectionService: DiscrepancyDetectionService,
    private val alertNotificationService: AlertNotificationService
) {
    
    // 스케줄 실행 이력 저장소
    private val executionHistory = ConcurrentHashMap<String, ScheduleExecutionLog>()
    private val executorService = Executors.newFixedThreadPool(2) // 병렬 처리용
    
    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MINUTES = 10
    }
    
    /**
     * 매일 오전 9시에 정산 프로세스 실행
     * cron: 초 분 시 일 월 요일
     * "0 0 9 * * *" = 매일 오전 9시 0분 0초
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    fun executeDaily() {
        val today = LocalDate.now()
        val executionId = "daily_${today}_${System.currentTimeMillis()}"
        
        println("🕘 일일 정산 스케줄 시작: $today")
        
        val executionLog = ScheduleExecutionLog(
            id = executionId,
            executionType = ScheduleExecutionType.DAILY_AUTO,
            scheduledDate = today,
            startedAt = LocalDateTime.now(),
            status = ExecutionStatus.RUNNING
        )
        
        executionHistory[executionId] = executionLog
        
        try {
            // 전일(D-1) 데이터를 대상으로 정산 처리
            val targetDate = today.minusDays(1)
            executeSettlementProcess(targetDate, executionId)
            
        } catch (e: Exception) {
            handleExecutionFailure(executionId, e)
        }
    }
    
    /**
     * 매시간 정각에 실시간 모니터링 (오전 9시 ~ 오후 6시)
     * cron: "0 0 9-18 * * MON-FRI" = 평일 오전 9시부터 오후 6시까지 매시간
     */
    @Scheduled(cron = "0 0 9-18 * * MON-FRI", zone = "Asia/Seoul")
    fun executeHourlyMonitoring() {
        val now = LocalDateTime.now()
        println("⏰ 시간별 모니터링 실행: ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}")
        
        try {
            // 오늘과 어제 데이터의 실시간 상태 체크
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            
            listOf(today, yesterday).forEach { date ->
                checkRealTimeStatus(date)
            }
            
        } catch (e: Exception) {
            println("시간별 모니터링 실행 중 오류 발생: ${e.message}")
        }
    }
    
    /**
     * 매주 월요일 오전 10시에 주간 요약 리포트 생성
     * cron: "0 0 10 * * MON" = 매주 월요일 오전 10시
     */
    @Scheduled(cron = "0 0 10 * * MON", zone = "Asia/Seoul")
    fun executeWeeklyReport() {
        val today = LocalDate.now()
        println("📊 주간 요약 리포트 생성: $today")
        
        try {
            generateWeeklyReport(today)
        } catch (e: Exception) {
            println("주간 리포트 생성 중 오류 발생: ${e.message}")
        }
    }
    
    /**
     * 수동 정산 실행 (특정 날짜)
     */
    fun executeManual(targetDate: LocalDate): String {
        val executionId = "manual_${targetDate}_${System.currentTimeMillis()}"
        
        println("🔧 수동 정산 실행: $targetDate")
        
        val executionLog = ScheduleExecutionLog(
            id = executionId,
            executionType = ScheduleExecutionType.MANUAL,
            scheduledDate = targetDate,
            startedAt = LocalDateTime.now(),
            status = ExecutionStatus.RUNNING
        )
        
        executionHistory[executionId] = executionLog
        
        // 비동기 실행
        CompletableFuture.runAsync({
            try {
                executeSettlementProcess(targetDate, executionId)
            } catch (e: Exception) {
                handleExecutionFailure(executionId, e)
            }
        }, executorService)
        
        return executionId
    }
    
    /**
     * 정산 프로세스 실행
     */
    private fun executeSettlementProcess(targetDate: LocalDate, executionId: String) {
        updateExecutionStatus(executionId, ExecutionStatus.RUNNING, "정산 데이터 수집 시작")
        
        val steps = mutableListOf<ProcessStep>()
        
        try {
            // 1단계: 정산 데이터 수집
            steps.add(ProcessStep("데이터 수집", "시작", LocalDateTime.now()))
            
            val collectionResult = dailySettlementCollectionService.collectDailySettlementData(
                date = targetDate,
                googlePlaySettlementService = null, // 실제로는 주입된 서비스 사용
                appStoreSettlementService = null
            )
            
            if (!collectionResult.overallSuccess) {
                throw RuntimeException("정산 데이터 수집 실패: ${collectionResult.platformResults}")
            }
            
            steps.add(ProcessStep("데이터 수집", "완료", LocalDateTime.now()))
            updateExecutionStatus(executionId, ExecutionStatus.RUNNING, "대사 처리 시작")
            
            // 2단계: 대사 처리
            steps.add(ProcessStep("대사 처리", "시작", LocalDateTime.now()))
            
            val reconciliationResult = reconciliationOrchestrator.processAllPlatformsReconciliation(
                date = targetDate,
                settlementCollectionService = dailySettlementCollectionService,
                googlePlayEventRepository = null, // 실제로는 주입된 리포지토리 사용
                appStoreEventRepository = null
            )
            
            steps.add(ProcessStep("대사 처리", "완료", LocalDateTime.now()))
            updateExecutionStatus(executionId, ExecutionStatus.RUNNING, "불일치 탐지 시작")
            
            // 3단계: 불일치 탐지 및 알림
            steps.add(ProcessStep("불일치 탐지", "시작", LocalDateTime.now()))
            
            val detectionResults = mutableListOf<DiscrepancyDetectionResult>()
            
            reconciliationResult.platformResults.forEach { (platform, result) ->
                val detectionResult = discrepancyDetectionService.detectDiscrepancies(result)
                detectionResults.add(detectionResult)
            }
            
            steps.add(ProcessStep("불일치 탐지", "완료", LocalDateTime.now()))
            updateExecutionStatus(executionId, ExecutionStatus.RUNNING, "완료 리포트 생성")
            
            // 4단계: 완료 리포트 발송
            steps.add(ProcessStep("리포트 발송", "시작", LocalDateTime.now()))
            
            sendCompletionReport(targetDate, reconciliationResult, detectionResults)
            
            steps.add(ProcessStep("리포트 발송", "완료", LocalDateTime.now()))
            
            // 성공 완료
            updateExecutionStatus(
                executionId = executionId,
                status = ExecutionStatus.COMPLETED,
                message = "정산 프로세스 성공 완료",
                steps = steps,
                result = ScheduleExecutionResult(
                    success = true,
                    processedPlatforms = reconciliationResult.platformResults.keys.toList(),
                    totalRecords = reconciliationResult.totalSettlementRecords,
                    matchingRate = reconciliationResult.overallMatchingRate,
                    detectedIssues = detectionResults.sumOf { it.detectedDiscrepancies.size },
                    overallStatus = reconciliationResult.overallStatus
                )
            )
            
        } catch (e: Exception) {
            handleExecutionFailure(executionId, e, steps)
        }
    }
    
    /**
     * 실시간 상태 체크
     */
    private fun checkRealTimeStatus(date: LocalDate) {
        try {
            // 해당 날짜의 최신 대사 결과 확인
            Platform.values().forEach { platform ->
                val reconciliationResult = reconciliationOrchestrator.getReconciliationHistory(platform, 1).firstOrNull()
                
                if (reconciliationResult != null && reconciliationResult.date == date) {
                    // 상태가 나쁜 경우 알림
                    if (reconciliationResult.finalStatus in listOf(ReconciliationStatus.FAILED, ReconciliationStatus.MAJOR_DISCREPANCY)) {
                        sendRealTimeAlert(date, platform, reconciliationResult)
                    }
                }
            }
            
        } catch (e: Exception) {
            println("실시간 상태 체크 중 오류: ${e.message}")
        }
    }
    
    /**
     * 주간 요약 리포트 생성
     */
    private fun generateWeeklyReport(reportDate: LocalDate) {
        val weekStart = reportDate.minusDays(7)
        val weekEnd = reportDate.minusDays(1)
        
        println("📈 주간 리포트 생성: $weekStart ~ $weekEnd")
        
        try {
            // 주간 통계 수집
            val weeklyStats = dailySettlementCollectionService.getSettlementStatistics(weekStart, weekEnd)
            
            // 플랫폼별 트렌드 분석
            val platformTrends = Platform.values().map { platform ->
                platform to reconciliationOrchestrator.analyzeReconciliationTrend(platform, 7)
            }.toMap()
            
            // 주간 리포트 발송
            sendWeeklyReport(weekStart, weekEnd, weeklyStats, platformTrends)
            
        } catch (e: Exception) {
            println("주간 리포트 생성 실패: ${e.message}")
        }
    }
    
    /**
     * 실행 상태 업데이트
     */
    private fun updateExecutionStatus(
        executionId: String,
        status: ExecutionStatus,
        message: String,
        steps: List<ProcessStep> = emptyList(),
        result: ScheduleExecutionResult? = null
    ) {
        val currentLog = executionHistory[executionId]
        if (currentLog != null) {
            val updatedLog = currentLog.copy(
                status = status,
                currentStep = message,
                steps = steps,
                result = result,
                completedAt = if (status in listOf(ExecutionStatus.COMPLETED, ExecutionStatus.FAILED)) {
                    LocalDateTime.now()
                } else null
            )
            executionHistory[executionId] = updatedLog
        }
        
        println("📝 실행 상태 업데이트 [$executionId]: $status - $message")
    }
    
    /**
     * 실행 실패 처리
     */
    private fun handleExecutionFailure(
        executionId: String,
        exception: Exception,
        steps: List<ProcessStep> = emptyList()
    ) {
        println("❌ 정산 실행 실패 [$executionId]: ${exception.message}")
        
        val currentLog = executionHistory[executionId]
        val retryCount = currentLog?.retryCount ?: 0
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            // 재시도 스케줄링
            scheduleRetry(executionId, retryCount + 1)
            
            updateExecutionStatus(
                executionId = executionId,
                status = ExecutionStatus.RETRYING,
                message = "재시도 대기 중 (${retryCount + 1}/$MAX_RETRY_ATTEMPTS)",
                steps = steps
            )
        } else {
            // 최대 재시도 횟수 초과
            updateExecutionStatus(
                executionId = executionId,
                status = ExecutionStatus.FAILED,
                message = "최대 재시도 횟수 초과: ${exception.message}",
                steps = steps,
                result = ScheduleExecutionResult(
                    success = false,
                    processedPlatforms = emptyList(),
                    totalRecords = 0,
                    matchingRate = 0.0,
                    detectedIssues = 0,
                    overallStatus = ReconciliationStatus.FAILED,
                    errorMessage = exception.message
                )
            )
            
            // 실패 알림 발송
            sendFailureAlert(executionId, exception)
        }
    }
    
    /**
     * 재시도 스케줄링
     */
    private fun scheduleRetry(executionId: String, retryCount: Int) {
        val currentLog = executionHistory[executionId] ?: return
        
        // 재시도 로그 업데이트
        executionHistory[executionId] = currentLog.copy(retryCount = retryCount)
        
        // 지연 후 재시도 (실제로는 Spring의 @Async나 TaskScheduler 사용)
        CompletableFuture.runAsync({
            try {
                Thread.sleep(RETRY_DELAY_MINUTES * 60 * 1000L) // 10분 대기
                executeSettlementProcess(currentLog.scheduledDate, executionId)
            } catch (e: Exception) {
                handleExecutionFailure(executionId, e)
            }
        }, executorService)
        
        println("🔄 재시도 예약 [$executionId]: ${RETRY_DELAY_MINUTES}분 후 재시도 ($retryCount/$MAX_RETRY_ATTEMPTS)")
    }
    
    /**
     * 완료 리포트 발송
     */
    private fun sendCompletionReport(
        date: LocalDate,
        reconciliationResult: CombinedReconciliationResult,
        detectionResults: List<DiscrepancyDetectionResult>
    ) {
        val totalIssues = detectionResults.sumOf { it.detectedDiscrepancies.size }
        val criticalIssues = detectionResults.sumOf { result ->
            result.detectedDiscrepancies.count { it.severity == DiscrepancySeverity.CRITICAL }
        }
        
        val report = buildString {
            appendLine("📊 일일 정산 완료 리포트 - $date")
            appendLine()
            appendLine("📈 전체 현황:")
            appendLine("  • 처리된 정산 건수: ${reconciliationResult.totalSettlementRecords}건")
            appendLine("  • 매칭률: ${String.format("%.2f", reconciliationResult.overallMatchingRate * 100)}%")
            appendLine("  • 전체 상태: ${reconciliationResult.overallStatus.description}")
            appendLine()
            appendLine("🔍 플랫폼별 결과:")
            reconciliationResult.platformResults.forEach { (platform, result) ->
                appendLine("  ${platform.name}:")
                appendLine("    - 매칭률: ${String.format("%.2f", result.matchingRate * 100)}%")
                appendLine("    - 미해결 불일치: ${result.unresolvedDiscrepancies}건")
                appendLine("    - 상태: ${result.finalStatus.description}")
            }
            appendLine()
            if (totalIssues > 0) {
                appendLine("⚠️ 탐지된 이슈:")
                appendLine("  • 전체 이슈: ${totalIssues}건")
                appendLine("  • 긴급 이슈: ${criticalIssues}건")
            } else {
                appendLine("✅ 탐지된 이슈 없음")
            }
        }
        
        val priority = when {
            criticalIssues > 0 -> NotificationPriority.CRITICAL
            reconciliationResult.overallMatchingRate < 0.90 -> NotificationPriority.HIGH
            totalIssues > 0 -> NotificationPriority.MEDIUM
            else -> NotificationPriority.LOW
        }
        
        val request = NotificationRequest(
            title = "일일 정산 완료 - $date",
            message = report,
            priority = priority,
            channels = listOf(AlertChannel.EMAIL, AlertChannel.SLACK),
            recipients = listOf("finance@company.com"),
            metadata = mapOf(
                "type" to "daily_completion",
                "date" to date.toString(),
                "overall_status" to reconciliationResult.overallStatus.name
            )
        )
        
        alertNotificationService.sendNotification(request)
    }
    
    /**
     * 실시간 알림 발송
     */
    private fun sendRealTimeAlert(
        date: LocalDate,
        platform: Platform,
        result: ReconciliationProcessResult
    ) {
        val request = NotificationRequest(
            title = "🚨 실시간 알림 - ${platform.name} 정산 이상",
            message = "날짜: $date\n상태: ${result.finalStatus.description}\n매칭률: ${String.format("%.2f", result.matchingRate * 100)}%",
            priority = NotificationPriority.HIGH,
            channels = listOf(AlertChannel.SLACK, AlertChannel.EMAIL),
            recipients = listOf("finance@company.com"),
            metadata = mapOf(
                "type" to "real_time_alert",
                "platform" to platform.name,
                "date" to date.toString()
            )
        )
        
        alertNotificationService.sendNotification(request)
    }
    
    /**
     * 실패 알림 발송
     */
    private fun sendFailureAlert(executionId: String, exception: Exception) {
        val request = NotificationRequest(
            title = "❌ 정산 프로세스 실행 실패",
            message = "실행 ID: $executionId\n오류: ${exception.message}\n최대 재시도 횟수 초과",
            priority = NotificationPriority.CRITICAL,
            channels = listOf(AlertChannel.EMAIL, AlertChannel.SLACK, AlertChannel.SMS),
            recipients = listOf("finance@company.com", "tech-lead@company.com"),
            metadata = mapOf(
                "type" to "execution_failure",
                "execution_id" to executionId,
                "error" to (exception.message ?: "Unknown error")
            )
        )
        
        alertNotificationService.sendNotification(request)
    }
    
    /**
     * 주간 리포트 발송
     */
    private fun sendWeeklyReport(
        weekStart: LocalDate,
        weekEnd: LocalDate,
        stats: SettlementPeriodStatistics,
        trends: Map<Platform, ReconciliationTrendAnalysis>
    ) {
        val report = buildString {
            appendLine("📊 주간 정산 요약 리포트")
            appendLine("기간: $weekStart ~ $weekEnd")
            appendLine()
            appendLine("📈 전체 통계:")
            appendLine("  • 총 처리일: ${stats.totalDays}일")
            appendLine("  • 총 거래 건수: ${stats.totalTransactions}건")
            appendLine("  • 총 매출: $${stats.totalGrossRevenue}")
            appendLine("  • 일평균 매출: $${stats.averageDailyRevenue}")
            appendLine()
            appendLine("📊 플랫폼별 트렌드:")
            trends.forEach { (platform, trend) ->
                appendLine("  ${platform.name}:")
                appendLine("    - 평균 매칭률: ${String.format("%.2f", trend.averageMatchingRate * 100)}%")
                appendLine("    - 트렌드: ${trend.trend.description}")
                appendLine("    - 분석일수: ${trend.analyzedDays}일")
            }
        }
        
        val request = NotificationRequest(
            title = "주간 정산 요약 리포트 ($weekStart ~ $weekEnd)",
            message = report,
            priority = NotificationPriority.MEDIUM,
            channels = listOf(AlertChannel.EMAIL),
            recipients = listOf("finance@company.com", "management@company.com"),
            metadata = mapOf(
                "type" to "weekly_report",
                "week_start" to weekStart.toString(),
                "week_end" to weekEnd.toString()
            )
        )
        
        alertNotificationService.sendNotification(request)
    }
    
    /**
     * 실행 이력 조회
     */
    fun getExecutionHistory(days: Int = 7): List<ScheduleExecutionLog> {
        val cutoffTime = LocalDateTime.now().minusDays(days.toLong())
        return executionHistory.values
            .filter { it.startedAt.isAfter(cutoffTime) }
            .sortedByDescending { it.startedAt }
    }
    
    /**
     * 실행 상태 조회
     */
    fun getExecutionStatus(executionId: String): ScheduleExecutionLog? {
        return executionHistory[executionId]
    }
    
    /**
     * 현재 실행 중인 작업 조회
     */
    fun getRunningExecutions(): List<ScheduleExecutionLog> {
        return executionHistory.values.filter { 
            it.status in listOf(ExecutionStatus.RUNNING, ExecutionStatus.RETRYING) 
        }
    }
}

// 데이터 클래스들
data class ScheduleExecutionLog(
    val id: String,
    val executionType: ScheduleExecutionType,
    val scheduledDate: LocalDate,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime? = null,
    val status: ExecutionStatus,
    val currentStep: String = "",
    val steps: List<ProcessStep> = emptyList(),
    val retryCount: Int = 0,
    val result: ScheduleExecutionResult? = null
) {
    val duration: Long?
        get() = completedAt?.let { 
            java.time.Duration.between(startedAt, it).toMillis() 
        }
}

data class ProcessStep(
    val name: String,
    val status: String,
    val timestamp: LocalDateTime
)

data class ScheduleExecutionResult(
    val success: Boolean,
    val processedPlatforms: List<Platform>,
    val totalRecords: Int,
    val matchingRate: Double,
    val detectedIssues: Int,
    val overallStatus: ReconciliationStatus,
    val errorMessage: String? = null
)

enum class ScheduleExecutionType(val description: String) {
    DAILY_AUTO("일일 자동 실행"),
    MANUAL("수동 실행"),
    RETRY("재시도 실행"),
    EMERGENCY("긴급 실행")
}

enum class ExecutionStatus(val description: String) {
    SCHEDULED("예약됨"),
    RUNNING("실행 중"),
    RETRYING("재시도 중"),
    COMPLETED("완료"),
    FAILED("실패"),
    CANCELLED("취소됨")
}