package com.example.integration.application.port.out

import com.example.integration.domain.ReconciliationRecord
import com.example.integration.domain.Platform
import java.time.LocalDate

/**
 * 대사 처리 저장소 포트 (출력 포트)
 */
interface ReconciliationRepositoryPort {
    
    /**
     * 대사 기록 저장
     */
    fun save(reconciliationRecord: ReconciliationRecord): ReconciliationRecord
    
    /**
     * 대사 기록 조회
     */
    fun findByPlatformAndDate(platform: Platform, reconciliationDate: LocalDate): ReconciliationRecord?
    
    /**
     * 날짜 범위로 대사 기록 조회
     */
    fun findByPlatformAndDateRange(
        platform: Platform,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ReconciliationRecord>
}