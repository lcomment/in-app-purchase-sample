package com.example.integration.application.port.`in`

import com.example.integration.domain.Platform
import com.example.integration.domain.Settlement
import java.time.LocalDate

/**
 * 정산 처리 Use Case (입력 포트)
 */
interface SettlementUseCase {
    
    /**
     * 일별 정산 처리
     */
    fun processSettlement(request: SettlementRequest): SettlementResult
    
    /**
     * 정산 데이터 조회
     */
    fun getSettlement(platform: Platform, settlementDate: LocalDate): Settlement?
}

/**
 * 정산 요청
 */
data class SettlementRequest(
    val platform: Platform,
    val settlementDate: LocalDate,
    val forceRecalculate: Boolean = false
)

/**
 * 정산 결과
 */
data class SettlementResult(
    val success: Boolean,
    val settlement: Settlement?,
    val errorMessage: String? = null
)