package com.example.integration.infrastructure.scheduler

import com.example.integration.application.port.`in`.SettlementUseCase
import com.example.integration.application.port.`in`.SettlementRequest
import com.example.integration.domain.Platform
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 정산 자동화 스케줄러 (Infrastructure Layer)
 */
@Component
class SettlementScheduler(
    private val settlementUseCase: SettlementUseCase
) {
    
    private val logger = LoggerFactory.getLogger(SettlementScheduler::class.java)
    
    /**
     * 일별 정산 자동 처리
     * 매일 오전 2시에 전날 정산 처리
     */
    @Scheduled(cron = "0 0 2 * * *")
    fun processAutomaticSettlement() {
        logger.info("Starting automatic daily settlement processing")
        
        val yesterday = LocalDate.now().minusDays(1)
        val platforms = listOf(Platform.GOOGLE_PLAY, Platform.APP_STORE)
        
        platforms.forEach { platform ->
            try {
                logger.info("Processing settlement for platform: $platform, date: $yesterday")
                
                val request = SettlementRequest(
                    platform = platform,
                    settlementDate = yesterday,
                    forceRecalculate = false
                )
                
                val result = settlementUseCase.processSettlement(request)
                
                if (result.success) {
                    logger.info("Settlement processing completed for $platform: ${result.settlement?.id}")
                } else {
                    logger.error("Settlement processing failed for $platform: ${result.errorMessage}")
                }
                
            } catch (e: Exception) {
                logger.error("Error processing settlement for platform: $platform", e)
            }
        }
        
        logger.info("Automatic daily settlement processing completed")
    }
    
    /**
     * 주간 정산 재처리
     * 매주 일요일 오전 3시에 지난 주 정산 재검증
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    fun processWeeklySettlementReview() {
        logger.info("Starting weekly settlement review")
        
        val platforms = listOf(Platform.GOOGLE_PLAY, Platform.APP_STORE)
        val endDate = LocalDate.now().minusDays(1)
        val startDate = endDate.minusDays(6) // 최근 7일
        
        platforms.forEach { platform ->
            try {
                logger.info("Reviewing settlements for platform: $platform, period: $startDate to $endDate")
                
                var currentDate = startDate
                while (!currentDate.isAfter(endDate)) {
                    val request = SettlementRequest(
                        platform = platform,
                        settlementDate = currentDate,
                        forceRecalculate = true // 주간 리뷰에서는 강제 재계산
                    )
                    
                    val result = settlementUseCase.processSettlement(request)
                    
                    if (result.success) {
                        logger.debug("Settlement review completed for $platform on $currentDate")
                    } else {
                        logger.warn("Settlement review failed for $platform on $currentDate: ${result.errorMessage}")
                    }
                    
                    currentDate = currentDate.plusDays(1)
                }
                
            } catch (e: Exception) {
                logger.error("Error in weekly settlement review for platform: $platform", e)
            }
        }
        
        logger.info("Weekly settlement review completed")
    }
}