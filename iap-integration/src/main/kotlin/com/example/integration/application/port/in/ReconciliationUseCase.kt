package com.example.integration.application.port.`in`

import com.example.integration.domain.Platform
import com.example.integration.domain.ReconciliationRecord
import java.time.LocalDate

/**
 * 대사 처리 Use Case (입력 포트)
 */
interface ReconciliationUseCase {
    
    /**
     * 대사 처리 실행
     */
    fun performReconciliation(request: ReconciliationRequest): ReconciliationResult
    
    /**
     * 대사 기록 조회
     */
    fun getReconciliationRecord(platform: Platform, reconciliationDate: LocalDate): ReconciliationRecord?
}

/**
 * 대사 처리 요청
 */
data class ReconciliationRequest(
    val platform: Platform,
    val reconciliationDate: LocalDate,
    val includeRefunds: Boolean = true
)

/**
 * 대사 처리 결과
 */
data class ReconciliationResult(
    val success: Boolean,
    val reconciliationRecord: ReconciliationRecord?,
    val discrepancies: List<DiscrepancyDetail> = emptyList(),
    val errorMessage: String? = null
)

/**
 * 불일치 상세 정보
 */
data class DiscrepancyDetail(
    val transactionId: String,
    val internalAmount: String?,
    val externalAmount: String?,
    val reason: String
)