package com.example.integration.infrastructure.scheduler

import com.example.integration.application.port.`in`.RefundUseCase
import com.example.integration.application.port.out.RefundRepositoryPort
import com.example.integration.domain.Platform
import com.example.integration.domain.RefundStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 환불 자동화 스케줄러 (Infrastructure Layer)
 */
@Component
class RefundScheduler(
    private val refundUseCase: RefundUseCase,
    private val refundRepository: RefundRepositoryPort
) {
    
    private val logger = LoggerFactory.getLogger(RefundScheduler::class.java)
    
    /**
     * 승인된 환불 자동 처리
     * 매 30분마다 승인된 환불을 자동으로 처리
     */
    @Scheduled(fixedRate = 30 * 60 * 1000) // 30분마다 실행
    fun processApprovedRefunds() {
        logger.info("Starting automatic processing of approved refunds")
        
        try {
            val approvedRefunds = refundRepository.findByStatus(RefundStatus.APPROVED)
            
            if (approvedRefunds.isEmpty()) {
                logger.debug("No approved refunds to process")
                return
            }
            
            logger.info("Found ${approvedRefunds.size} approved refunds to process")
            
            approvedRefunds.forEach { refund ->
                try {
                    logger.info("Processing approved refund: refundId=${refund.id}")
                    
                    val result = refundUseCase.processRefund(refund.id)
                    
                    if (result.success) {
                        logger.info("Successfully processed refund: refundId=${refund.id}")
                    } else {
                        logger.warn("Failed to process refund: refundId=${refund.id}, error=${result.errorMessage}")
                    }
                    
                    // 처리 간격을 두어 API 부하 방지
                    Thread.sleep(1000) // 1초 간격
                    
                } catch (e: Exception) {
                    logger.error("Error processing refund: refundId=${refund.id}", e)
                }
            }
            
            logger.info("Completed automatic processing of approved refunds")
            
        } catch (e: Exception) {
            logger.error("Error in automatic refund processing", e)
        }
    }
    
    /**
     * 처리 중인 환불 상태 체크
     * 매 시간마다 처리 중인 환불의 상태를 확인하고 오래된 요청을 정리
     */
    @Scheduled(fixedRate = 60 * 60 * 1000) // 1시간마다 실행
    fun checkProcessingRefunds() {
        logger.info("Starting processing refunds status check")
        
        try {
            val processingRefunds = refundRepository.findByStatus(RefundStatus.PROCESSING)
            
            if (processingRefunds.isEmpty()) {
                logger.debug("No processing refunds to check")
                return
            }
            
            logger.info("Found ${processingRefunds.size} processing refunds to check")
            
            val cutoffTime = LocalDateTime.now().minusHours(24) // 24시간 이상 처리 중인 것들
            
            processingRefunds.forEach { refund ->
                if (refund.updatedAt.isBefore(cutoffTime)) {
                    logger.warn("Found stuck refund (processing for >24h): refundId=${refund.id}, " +
                            "platform=${refund.platform}, updatedAt=${refund.updatedAt}")
                    
                    // 실제 운영환경에서는 여기서 알림을 발송하거나 수동 처리 큐에 추가
                    notifyStuckRefund(refund)
                }
            }
            
            logger.info("Completed processing refunds status check")
            
        } catch (e: Exception) {
            logger.error("Error in processing refunds status check", e)
        }
    }
    
    /**
     * 일별 환불 통계 생성
     * 매일 오전 6시에 전날 환불 통계 생성
     */
    @Scheduled(cron = "0 0 6 * * *")
    fun generateDailyRefundReport() {
        logger.info("Starting daily refund report generation")
        
        val yesterday = LocalDate.now().minusDays(1)
        val platforms = listOf(Platform.GOOGLE_PLAY, Platform.APP_STORE)
        
        platforms.forEach { platform ->
            try {
                val refunds = refundRepository.findByPlatformAndDateRange(platform, yesterday, yesterday)
                
                val totalRefunds = refunds.size
                val completedRefunds = refunds.count { it.status == RefundStatus.COMPLETED }
                val failedRefunds = refunds.count { it.status == RefundStatus.FAILED }
                val pendingRefunds = refunds.count { 
                    it.status in listOf(RefundStatus.REQUESTED, RefundStatus.APPROVED, RefundStatus.PROCESSING) 
                }
                
                val totalAmount = refunds
                    .filter { it.status == RefundStatus.COMPLETED }
                    .sumOf { it.amount }
                
                logger.info("=== DAILY REFUND REPORT ($yesterday) ===")
                logger.info("Platform: $platform")
                logger.info("Total Refunds: $totalRefunds")
                logger.info("Completed: $completedRefunds")
                logger.info("Failed: $failedRefunds")
                logger.info("Pending: $pendingRefunds")
                logger.info("Total Refunded Amount: $totalAmount USD")
                logger.info("Success Rate: ${if (totalRefunds > 0) String.format("%.2f", (completedRefunds.toDouble() / totalRefunds * 100)) else "0.00"}%")
                logger.info("========================================")
                
            } catch (e: Exception) {
                logger.error("Error generating daily refund report for platform: $platform", e)
            }
        }
        
        logger.info("Daily refund report generation completed")
    }
    
    /**
     * 주간 환불 리뷰
     * 매주 월요일 오전 7시에 지난 주 환불 현황 리뷰
     */
    @Scheduled(cron = "0 0 7 * * MON")
    fun performWeeklyRefundReview() {
        logger.info("Starting weekly refund review")
        
        val endDate = LocalDate.now().minusDays(1)
        val startDate = endDate.minusDays(6) // 최근 7일
        val platforms = listOf(Platform.GOOGLE_PLAY, Platform.APP_STORE)
        
        platforms.forEach { platform ->
            try {
                val weeklyRefunds = refundRepository.findByPlatformAndDateRange(platform, startDate, endDate)
                
                val longPendingRefunds = weeklyRefunds.filter { refund ->
                    refund.status in listOf(RefundStatus.REQUESTED, RefundStatus.APPROVED) &&
                    refund.requestedAt.isBefore(LocalDateTime.now().minusDays(3))
                }
                
                if (longPendingRefunds.isNotEmpty()) {
                    logger.warn("=== WEEKLY REFUND REVIEW ALERT ===")
                    logger.warn("Platform: $platform")
                    logger.warn("Found ${longPendingRefunds.size} refunds pending for >3 days")
                    
                    longPendingRefunds.take(10).forEach { refund ->
                        logger.warn("Long pending refund: refundId=${refund.id}, " +
                                "status=${refund.status}, requestedAt=${refund.requestedAt}, " +
                                "amount=${refund.amount} ${refund.currency}")
                    }
                    
                    if (longPendingRefunds.size > 10) {
                        logger.warn("... and ${longPendingRefunds.size - 10} more")
                    }
                    
                    logger.warn("=================================")
                    
                    // 실제 운영환경에서는 여기서 관리자에게 알림 발송
                }
                
            } catch (e: Exception) {
                logger.error("Error in weekly refund review for platform: $platform", e)
            }
        }
        
        logger.info("Weekly refund review completed")
    }
    
    /**
     * 처리 정체 환불 알림
     */
    private fun notifyStuckRefund(refund: com.example.integration.domain.Refund) {
        logger.error("=== STUCK REFUND ALERT ===")
        logger.error("RefundId: ${refund.id}")
        logger.error("Platform: ${refund.platform}")
        logger.error("Amount: ${refund.amount} ${refund.currency}")
        logger.error("Status: ${refund.status}")
        logger.error("Last Updated: ${refund.updatedAt}")
        logger.error("Processing Duration: ${java.time.Duration.between(refund.updatedAt, LocalDateTime.now()).toHours()} hours")
        logger.error("========================")
        
        // 실제 운영환경에서는 여기서 슬랙, 이메일 등으로 긴급 알림 발송
    }
}