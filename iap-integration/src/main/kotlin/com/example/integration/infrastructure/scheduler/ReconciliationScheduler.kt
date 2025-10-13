package com.example.integration.infrastructure.scheduler

import com.example.integration.application.port.`in`.ReconciliationUseCase
import com.example.integration.application.port.`in`.ReconciliationRequest
import com.example.integration.domain.Platform
import com.example.integration.domain.ReconciliationStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 대사 처리 자동화 스케줄러 (Infrastructure Layer)
 */
@Component
class ReconciliationScheduler(
    private val reconciliationUseCase: ReconciliationUseCase
) {
    
    private val logger = LoggerFactory.getLogger(ReconciliationScheduler::class.java)
    
    /**
     * 일별 대사 처리 자동 실행
     * 매일 오전 4시에 전날 대사 처리
     */
    @Scheduled(cron = "0 0 4 * * *")
    fun performAutomaticReconciliation() {
        logger.info("Starting automatic daily reconciliation")
        
        val yesterday = LocalDate.now().minusDays(1)
        val platforms = listOf(Platform.GOOGLE_PLAY, Platform.APP_STORE)
        
        platforms.forEach { platform ->
            try {
                logger.info("Performing reconciliation for platform: $platform, date: $yesterday")
                
                val request = ReconciliationRequest(
                    platform = platform,
                    reconciliationDate = yesterday,
                    includeRefunds = true
                )
                
                val result = reconciliationUseCase.performReconciliation(request)
                
                if (result.success) {
                    val record = result.reconciliationRecord
                    if (record != null) {
                        when (record.status) {
                            ReconciliationStatus.MATCHED -> {
                                logger.info("Reconciliation completed successfully for $platform: all transactions matched")
                            }
                            ReconciliationStatus.DISCREPANCY_FOUND -> {
                                logger.warn("Reconciliation completed with discrepancies for $platform: " +
                                        "${record.discrepancyCount} discrepancies found, " +
                                        "amount: ${record.discrepancyAmount} ${record.currency}")
                                
                                // 불일치 발견 시 알림 (실제 운영에서는 알림 시스템 연동)
                                notifyDiscrepancy(platform, record, result.discrepancies)
                            }
                            else -> {
                                logger.warn("Reconciliation completed with status: ${record.status} for $platform")
                            }
                        }
                    }
                } else {
                    logger.error("Reconciliation failed for $platform: ${result.errorMessage}")
                }
                
            } catch (e: Exception) {
                logger.error("Error performing reconciliation for platform: $platform", e)
            }
        }
        
        logger.info("Automatic daily reconciliation completed")
    }
    
    /**
     * 주간 대사 재검증
     * 매주 월요일 오전 5시에 지난 주 대사 재검증
     */
    @Scheduled(cron = "0 0 5 * * MON")
    fun performWeeklyReconciliationReview() {
        logger.info("Starting weekly reconciliation review")
        
        val platforms = listOf(Platform.GOOGLE_PLAY, Platform.APP_STORE)
        val endDate = LocalDate.now().minusDays(1)
        val startDate = endDate.minusDays(6) // 최근 7일
        
        platforms.forEach { platform ->
            try {
                logger.info("Reviewing reconciliations for platform: $platform, period: $startDate to $endDate")
                
                var currentDate = startDate
                var totalDiscrepancies = 0
                
                while (!currentDate.isAfter(endDate)) {
                    val existingRecord = reconciliationUseCase.getReconciliationRecord(platform, currentDate)
                    
                    if (existingRecord != null && existingRecord.status == ReconciliationStatus.DISCREPANCY_FOUND) {
                        totalDiscrepancies += existingRecord.discrepancyCount
                        logger.warn("Found unresolved discrepancies on $currentDate for $platform: ${existingRecord.discrepancyCount}")
                    }
                    
                    currentDate = currentDate.plusDays(1)
                }
                
                if (totalDiscrepancies > 0) {
                    logger.warn("Weekly review found $totalDiscrepancies unresolved discrepancies for $platform")
                    // 주간 리포트 생성 (실제 운영에서는 리포트 시스템 연동)
                    generateWeeklyReconciliationReport(platform, startDate, endDate, totalDiscrepancies)
                } else {
                    logger.info("Weekly review completed successfully for $platform: no unresolved discrepancies")
                }
                
            } catch (e: Exception) {
                logger.error("Error in weekly reconciliation review for platform: $platform", e)
            }
        }
        
        logger.info("Weekly reconciliation review completed")
    }
    
    /**
     * 불일치 발견 시 알림
     */
    private fun notifyDiscrepancy(
        platform: Platform, 
        record: com.example.integration.domain.ReconciliationRecord,
        discrepancies: List<com.example.integration.application.port.`in`.DiscrepancyDetail>
    ) {
        logger.warn("=== RECONCILIATION DISCREPANCY ALERT ===")
        logger.warn("Platform: $platform")
        logger.warn("Date: ${record.reconciliationDate}")
        logger.warn("Discrepancy Count: ${record.discrepancyCount}")
        logger.warn("Discrepancy Amount: ${record.discrepancyAmount} ${record.currency}")
        
        discrepancies.take(5).forEachIndexed { index, discrepancy ->
            logger.warn("Discrepancy ${index + 1}: TransactionId=${discrepancy.transactionId}, " +
                    "Internal=${discrepancy.internalAmount}, External=${discrepancy.externalAmount}, " +
                    "Reason=${discrepancy.reason}")
        }
        
        if (discrepancies.size > 5) {
            logger.warn("... and ${discrepancies.size - 5} more discrepancies")
        }
        
        logger.warn("=========================================")
        
        // 실제 운영환경에서는 여기서 슬랙, 이메일 등으로 알림 발송
    }
    
    /**
     * 주간 대사 리포트 생성
     */
    private fun generateWeeklyReconciliationReport(
        platform: Platform,
        startDate: LocalDate,
        endDate: LocalDate,
        totalDiscrepancies: Int
    ) {
        logger.info("=== WEEKLY RECONCILIATION REPORT ===")
        logger.info("Platform: $platform")
        logger.info("Period: $startDate to $endDate")
        logger.info("Total Unresolved Discrepancies: $totalDiscrepancies")
        logger.info("====================================")
        
        // 실제 운영환경에서는 여기서 상세 리포트 파일 생성 또는 대시보드 업데이트
    }
}