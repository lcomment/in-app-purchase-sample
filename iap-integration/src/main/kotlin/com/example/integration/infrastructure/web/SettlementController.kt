package com.example.integration.infrastructure.web

import com.example.integration.application.port.`in`.SettlementUseCase
import com.example.integration.application.port.`in`.SettlementRequest
import com.example.integration.domain.Platform
import com.example.integration.domain.Settlement
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

/**
 * 정산 관련 REST API 컨트롤러 (Infrastructure Layer)
 */
@RestController
@RequestMapping("/api/v1/settlements")
class SettlementController(
    private val settlementUseCase: SettlementUseCase
) {
    
    private val logger = LoggerFactory.getLogger(SettlementController::class.java)
    
    /**
     * 정산 처리 실행
     */
    @PostMapping("/process")
    fun processSettlement(@RequestBody request: SettlementRequestDto): ResponseEntity<SettlementResponseDto> {
        logger.info("Settlement processing request: platform=${request.platform}, date=${request.settlementDate}")
        
        val settlementRequest = SettlementRequest(
            platform = request.platform,
            settlementDate = request.settlementDate,
            forceRecalculate = request.forceRecalculate
        )
        
        val result = settlementUseCase.processSettlement(settlementRequest)
        
        val response = SettlementResponseDto(
            success = result.success,
            settlement = result.settlement?.let { mapToDto(it) },
            message = result.errorMessage ?: "Settlement processed successfully"
        )
        
        return if (result.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }
    
    /**
     * 정산 데이터 조회
     */
    @GetMapping("/{platform}/{settlementDate}")
    fun getSettlement(
        @PathVariable platform: Platform,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") settlementDate: LocalDate
    ): ResponseEntity<SettlementDto> {
        logger.info("Settlement inquiry: platform=$platform, date=$settlementDate")
        
        val settlement = settlementUseCase.getSettlement(platform, settlementDate)
        
        return if (settlement != null) {
            ResponseEntity.ok(mapToDto(settlement))
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    private fun mapToDto(settlement: Settlement): SettlementDto {
        return SettlementDto(
            id = settlement.id,
            platform = settlement.platform,
            settlementDate = settlement.settlementDate,
            totalRevenue = settlement.totalRevenue.toString(),
            platformFee = settlement.platformFee.toString(),
            netRevenue = settlement.netRevenue.toString(),
            currency = settlement.currency,
            paymentCount = settlement.paymentCount,
            refundCount = settlement.refundCount,
            refundAmount = settlement.refundAmount.toString(),
            status = settlement.status
        )
    }
}

/**
 * 정산 요청 DTO
 */
data class SettlementRequestDto(
    val platform: Platform,
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    val settlementDate: LocalDate,
    val forceRecalculate: Boolean = false
)

/**
 * 정산 응답 DTO
 */
data class SettlementResponseDto(
    val success: Boolean,
    val settlement: SettlementDto?,
    val message: String
)

/**
 * 정산 정보 DTO
 */
data class SettlementDto(
    val id: String,
    val platform: Platform,
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    val settlementDate: LocalDate,
    val totalRevenue: String,
    val platformFee: String,
    val netRevenue: String,
    val currency: String,
    val paymentCount: Int,
    val refundCount: Int,
    val refundAmount: String,
    val status: com.example.integration.domain.SettlementStatus
)