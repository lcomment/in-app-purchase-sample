package com.example.integration.infrastructure.web

import com.example.integration.application.port.`in`.RefundUseCase
import com.example.integration.application.port.`in`.RefundRequest
import com.example.integration.domain.Platform
import com.example.integration.domain.Refund
import com.example.integration.domain.RefundReason
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

/**
 * 환불 관련 REST API 컨트롤러 (Infrastructure Layer)
 */
@RestController
@RequestMapping("/api/v1/refunds")
class RefundController(
    private val refundUseCase: RefundUseCase
) {
    
    private val logger = LoggerFactory.getLogger(RefundController::class.java)
    
    /**
     * 환불 요청
     */
    @PostMapping("/request")
    fun requestRefund(@RequestBody request: RefundRequestDto): ResponseEntity<RefundResponseDto> {
        logger.info("Refund request: paymentId=${request.paymentId}, platform=${request.platform}")
        
        val refundRequest = RefundRequest(
            paymentId = request.paymentId,
            subscriptionId = request.subscriptionId,
            userId = request.userId,
            platform = request.platform,
            amount = request.amount?.let { BigDecimal(it) },
            currency = request.currency,
            reason = request.reason,
            customerNote = request.customerNote,
            requestedBy = request.requestedBy
        )
        
        val result = refundUseCase.requestRefund(refundRequest)
        
        val response = RefundResponseDto(
            success = result.success,
            refund = result.refund?.let { mapToDto(it) },
            message = result.errorMessage ?: "환불 요청이 성공적으로 접수되었습니다"
        )
        
        return if (result.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }
    
    /**
     * 환불 승인
     */
    @PostMapping("/{refundId}/approve")
    fun approveRefund(
        @PathVariable refundId: String,
        @RequestBody request: RefundApprovalRequestDto
    ): ResponseEntity<RefundResponseDto> {
        logger.info("Refund approval: refundId=$refundId, approvedBy=${request.approvedBy}")
        
        val result = refundUseCase.approveRefund(refundId, request.approvedBy)
        
        val response = RefundResponseDto(
            success = result.success,
            refund = result.refund?.let { mapToDto(it) },
            message = result.errorMessage ?: "환불이 승인되었습니다"
        )
        
        return if (result.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }
    
    /**
     * 환불 거부
     */
    @PostMapping("/{refundId}/reject")
    fun rejectRefund(
        @PathVariable refundId: String,
        @RequestBody request: RefundRejectionRequestDto
    ): ResponseEntity<RefundResponseDto> {
        logger.info("Refund rejection: refundId=$refundId, reason=${request.reason}")
        
        val result = refundUseCase.rejectRefund(refundId, request.reason)
        
        val response = RefundResponseDto(
            success = result.success,
            refund = result.refund?.let { mapToDto(it) },
            message = result.errorMessage ?: "환불이 거부되었습니다"
        )
        
        return if (result.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }
    
    /**
     * 환불 처리 실행
     */
    @PostMapping("/{refundId}/process")
    fun processRefund(@PathVariable refundId: String): ResponseEntity<RefundResponseDto> {
        logger.info("Processing refund: refundId=$refundId")
        
        val result = refundUseCase.processRefund(refundId)
        
        val response = RefundResponseDto(
            success = result.success,
            refund = result.refund?.let { mapToDto(it) },
            message = result.errorMessage ?: "환불 처리가 완료되었습니다"
        )
        
        return if (result.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }
    
    /**
     * 환불 상태 조회
     */
    @GetMapping("/{refundId}")
    fun getRefundStatus(@PathVariable refundId: String): ResponseEntity<RefundDto> {
        logger.info("Refund status inquiry: refundId=$refundId")
        
        val refund = refundUseCase.getRefundStatus(refundId)
        
        return if (refund != null) {
            ResponseEntity.ok(mapToDto(refund))
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    /**
     * 사용자 환불 내역 조회
     */
    @GetMapping("/user/{userId}")
    fun getUserRefunds(
        @PathVariable userId: String,
        @RequestParam(required = false) platform: Platform?
    ): ResponseEntity<List<RefundDto>> {
        logger.info("User refunds inquiry: userId=$userId, platform=$platform")
        
        val refunds = refundUseCase.getUserRefunds(userId, platform)
        val refundDtos = refunds.map { mapToDto(it) }
        
        return ResponseEntity.ok(refundDtos)
    }
    
    private fun mapToDto(refund: Refund): RefundDto {
        return RefundDto(
            id = refund.id,
            paymentId = refund.paymentId,
            subscriptionId = refund.subscriptionId,
            userId = refund.userId,
            platform = refund.platform,
            amount = refund.amount.toString(),
            currency = refund.currency,
            reason = refund.reason,
            status = refund.status,
            requestedAt = refund.requestedAt.toString(),
            processedAt = refund.processedAt?.toString(),
            approvedBy = refund.approvedBy,
            customerNote = refund.customerNote,
            internalNote = refund.internalNote,
            platformRefundId = refund.platformRefundId
        )
    }
}

/**
 * 환불 요청 DTO
 */
data class RefundRequestDto(
    val paymentId: String,
    val subscriptionId: String,
    val userId: String,
    val platform: Platform,
    val amount: String?, // null이면 전액 환불
    val currency: String,
    val reason: RefundReason,
    val customerNote: String? = null,
    val requestedBy: String = "customer"
)

/**
 * 환불 승인 요청 DTO
 */
data class RefundApprovalRequestDto(
    val approvedBy: String,
    val note: String? = null
)

/**
 * 환불 거부 요청 DTO
 */
data class RefundRejectionRequestDto(
    val reason: String
)

/**
 * 환불 응답 DTO
 */
data class RefundResponseDto(
    val success: Boolean,
    val refund: RefundDto?,
    val message: String
)

/**
 * 환불 정보 DTO
 */
data class RefundDto(
    val id: String,
    val paymentId: String,
    val subscriptionId: String,
    val userId: String,
    val platform: Platform,
    val amount: String,
    val currency: String,
    val reason: RefundReason,
    val status: com.example.integration.domain.RefundStatus,
    val requestedAt: String,
    val processedAt: String?,
    val approvedBy: String?,
    val customerNote: String?,
    val internalNote: String?,
    val platformRefundId: String?
)