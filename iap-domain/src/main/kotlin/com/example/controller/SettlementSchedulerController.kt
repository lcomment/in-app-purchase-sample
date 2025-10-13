package com.example.controller

import com.example.service.DailySettlementScheduler
import com.example.service.ScheduleExecutionLog
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

/**
 * 정산 스케줄러 관리 컨트롤러
 * 
 * 정산 스케줄러의 수동 실행, 상태 조회, 이력 관리 등을 위한 REST API를 제공합니다.
 */
@RestController
@RequestMapping("/api/settlement/scheduler")
class SettlementSchedulerController(
    private val dailySettlementScheduler: DailySettlementScheduler
) {
    
    /**
     * 수동 정산 실행
     * POST /api/settlement/scheduler/execute
     */
    @PostMapping("/execute")
    fun executeManualSettlement(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<Map<String, Any>> {
        
        return try {
            val executionId = dailySettlementScheduler.executeManual(date)
            
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "정산 작업이 시작되었습니다",
                "executionId" to executionId,
                "targetDate" to date.toString()
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "정산 작업 시작 실패: ${e.message}",
                "targetDate" to date.toString()
            ))
        }
    }
    
    /**
     * 실행 상태 조회
     * GET /api/settlement/scheduler/status/{executionId}
     */
    @GetMapping("/status/{executionId}")
    fun getExecutionStatus(@PathVariable executionId: String): ResponseEntity<ScheduleExecutionLog> {
        val executionLog = dailySettlementScheduler.getExecutionStatus(executionId)
        
        return if (executionLog != null) {
            ResponseEntity.ok(executionLog)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    /**
     * 실행 이력 조회
     * GET /api/settlement/scheduler/history
     */
    @GetMapping("/history")
    fun getExecutionHistory(
        @RequestParam(defaultValue = "7") days: Int
    ): ResponseEntity<List<ScheduleExecutionLog>> {
        val history = dailySettlementScheduler.getExecutionHistory(days)
        return ResponseEntity.ok(history)
    }
    
    /**
     * 현재 실행 중인 작업 조회
     * GET /api/settlement/scheduler/running
     */
    @GetMapping("/running")
    fun getRunningExecutions(): ResponseEntity<List<ScheduleExecutionLog>> {
        val runningExecutions = dailySettlementScheduler.getRunningExecutions()
        return ResponseEntity.ok(runningExecutions)
    }
    
    /**
     * 스케줄러 상태 대시보드
     * GET /api/settlement/scheduler/dashboard
     */
    @GetMapping("/dashboard")
    fun getSchedulerDashboard(): ResponseEntity<Map<String, Any>> {
        val history = dailySettlementScheduler.getExecutionHistory(30)
        val runningExecutions = dailySettlementScheduler.getRunningExecutions()
        
        // 통계 계산
        val totalExecutions = history.size
        val successfulExecutions = history.count { it.result?.success == true }
        val failedExecutions = history.count { it.result?.success == false }
        val successRate = if (totalExecutions > 0) {
            (successfulExecutions.toDouble() / totalExecutions * 100)
        } else 0.0
        
        val averageExecutionTime = history.mapNotNull { it.duration }.let { durations ->
            if (durations.isNotEmpty()) durations.average() else 0.0
        }
        
        // 최근 실행 상태
        val recentExecutions = history.take(10)
        
        return ResponseEntity.ok(mapOf(
            "statistics" to mapOf(
                "totalExecutions" to totalExecutions,
                "successfulExecutions" to successfulExecutions,
                "failedExecutions" to failedExecutions,
                "successRate" to String.format("%.2f", successRate),
                "averageExecutionTimeMs" to averageExecutionTime.toLong()
            ),
            "currentStatus" to mapOf(
                "runningExecutions" to runningExecutions.size,
                "runningDetails" to runningExecutions
            ),
            "recentExecutions" to recentExecutions,
            "healthStatus" to determineHealthStatus(history)
        ))
    }
    
    /**
     * 스케줄러 헬스 체크
     * GET /api/settlement/scheduler/health
     */
    @GetMapping("/health")
    fun getSchedulerHealth(): ResponseEntity<Map<String, Any>> {
        val recentHistory = dailySettlementScheduler.getExecutionHistory(7)
        val healthStatus = determineHealthStatus(recentHistory)
        
        val healthInfo = mapOf(
            "status" to healthStatus,
            "lastExecutionDate" to (recentHistory.firstOrNull()?.scheduledDate?.toString() ?: "N/A"),
            "recentSuccessRate" to calculateRecentSuccessRate(recentHistory),
            "activeExecutions" to dailySettlementScheduler.getRunningExecutions().size,
            "lastWeekExecutions" to recentHistory.size
        )
        
        return when (healthStatus) {
            "HEALTHY" -> ResponseEntity.ok(healthInfo)
            "WARNING" -> ResponseEntity.status(206).body(healthInfo) // 206 Partial Content
            "CRITICAL" -> ResponseEntity.status(503).body(healthInfo) // 503 Service Unavailable
            else -> ResponseEntity.status(500).body(healthInfo)
        }
    }
    
    /**
     * 헬스 상태 결정
     */
    private fun determineHealthStatus(history: List<ScheduleExecutionLog>): String {
        if (history.isEmpty()) return "UNKNOWN"
        
        val recentExecutions = history.take(7) // 최근 7일
        val successRate = calculateRecentSuccessRate(recentExecutions)
        val hasRecentFailures = recentExecutions.any { 
            it.result?.success == false && 
            it.startedAt.isAfter(java.time.LocalDateTime.now().minusDays(2))
        }
        
        return when {
            successRate >= 95.0 && !hasRecentFailures -> "HEALTHY"
            successRate >= 80.0 -> "WARNING" 
            else -> "CRITICAL"
        }
    }
    
    /**
     * 최근 성공률 계산
     */
    private fun calculateRecentSuccessRate(executions: List<ScheduleExecutionLog>): Double {
        if (executions.isEmpty()) return 0.0
        
        val completedExecutions = executions.filter { it.result != null }
        if (completedExecutions.isEmpty()) return 0.0
        
        val successfulExecutions = completedExecutions.count { it.result?.success == true }
        return (successfulExecutions.toDouble() / completedExecutions.size) * 100
    }
    
    /**
     * 긴급 재실행 (관리자용)
     * POST /api/settlement/scheduler/emergency
     */
    @PostMapping("/emergency")
    fun executeEmergencySettlement(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @RequestParam(required = false) reason: String?
    ): ResponseEntity<Map<String, Any>> {
        
        return try {
            val executionId = dailySettlementScheduler.executeManual(date)
            
            println("🚨 긴급 정산 실행: $date (사유: ${reason ?: "미명시"})")
            
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "긴급 정산 작업이 시작되었습니다",
                "executionId" to executionId,
                "targetDate" to date.toString(),
                "reason" to (reason ?: "미명시"),
                "priority" to "EMERGENCY"
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "긴급 정산 작업 시작 실패: ${e.message}",
                "targetDate" to date.toString()
            ))
        }
    }
}