package com.example.integration.infrastructure.web

import com.example.integration.application.port.`in`.ReconciliationUseCase
import com.example.integration.application.port.`in`.ReconciliationRequest
import com.example.integration.application.port.`in`.DiscrepancyDetail
import com.example.integration.domain.Platform
import com.example.integration.domain.ReconciliationRecord
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

/**
 * 대사 처리 관련 REST API 컨트롤러 (Infrastructure Layer)
 */
@RestController
@RequestMapping("/api/v1/reconciliations")
class ReconciliationController(
    private val reconciliationUseCase: ReconciliationUseCase
) {
    
    private val logger = LoggerFactory.getLogger(ReconciliationController::class.java)
    
    /**
     * 대사 처리 실행
     */
    @PostMapping("/perform")
    fun performReconciliation(@RequestBody request: ReconciliationRequestDto): ResponseEntity<ReconciliationResponseDto> {
        logger.info("Reconciliation request: platform=${request.platform}, date=${request.reconciliationDate}")
        
        val reconciliationRequest = ReconciliationRequest(
            platform = request.platform,
            reconciliationDate = request.reconciliationDate,
            includeRefunds = request.includeRefunds
        )
        
        val result = reconciliationUseCase.performReconciliation(reconciliationRequest)
        
        val response = ReconciliationResponseDto(
            success = result.success,
            reconciliationRecord = result.reconciliationRecord?.let { mapToDto(it) },
            discrepancies = result.discrepancies,
            message = result.errorMessage ?: "Reconciliation completed successfully"
        )
        
        return if (result.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }
    
    /**
     * 대사 기록 조회
     */
    @GetMapping("/{platform}/{reconciliationDate}")
    fun getReconciliationRecord(
        @PathVariable platform: Platform,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") reconciliationDate: LocalDate
    ): ResponseEntity<ReconciliationRecordDto> {
        logger.info("Reconciliation record inquiry: platform=$platform, date=$reconciliationDate")
        
        val record = reconciliationUseCase.getReconciliationRecord(platform, reconciliationDate)
        
        return if (record != null) {
            ResponseEntity.ok(mapToDto(record))
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    private fun mapToDto(record: ReconciliationRecord): ReconciliationRecordDto {
        return ReconciliationRecordDto(
            id = record.id,
            platform = record.platform,
            reconciliationDate = record.reconciliationDate,
            internalTransactionCount = record.internalTransactionCount,
            externalTransactionCount = record.externalTransactionCount,
            internalAmount = record.internalAmount.toString(),
            externalAmount = record.externalAmount.toString(),
            discrepancyCount = record.discrepancyCount,
            discrepancyAmount = record.discrepancyAmount.toString(),
            currency = record.currency,
            status = record.status,
            notes = record.notes
        )
    }
}

/**
 * 대사 처리 요청 DTO
 */
data class ReconciliationRequestDto(
    val platform: Platform,
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    val reconciliationDate: LocalDate,
    val includeRefunds: Boolean = true
)

/**
 * 대사 처리 응답 DTO
 */
data class ReconciliationResponseDto(
    val success: Boolean,
    val reconciliationRecord: ReconciliationRecordDto?,
    val discrepancies: List<DiscrepancyDetail> = emptyList(),
    val message: String
)

/**
 * 대사 기록 DTO
 */
data class ReconciliationRecordDto(
    val id: String,
    val platform: Platform,
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    val reconciliationDate: LocalDate,
    val internalTransactionCount: Int,
    val externalTransactionCount: Int,
    val internalAmount: String,
    val externalAmount: String,
    val discrepancyCount: Int,
    val discrepancyAmount: String,
    val currency: String,
    val status: com.example.integration.domain.ReconciliationStatus,
    val notes: String?
)