package com.example.integration.application.port.out

import com.example.integration.domain.Settlement
import com.example.integration.domain.Platform
import java.time.LocalDate

/**
 * 정산 저장소 포트 (출력 포트)
 */
interface SettlementRepositoryPort {
    
    /**
     * 정산 정보 저장
     */
    fun save(settlement: Settlement): Settlement
    
    /**
     * 정산 정보 조회
     */
    fun findByPlatformAndDate(platform: Platform, settlementDate: LocalDate): Settlement?
    
    /**
     * 날짜 범위로 정산 정보 조회
     */
    fun findByPlatformAndDateRange(
        platform: Platform,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Settlement>
}