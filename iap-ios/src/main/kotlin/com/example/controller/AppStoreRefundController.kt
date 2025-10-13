package com.example.controller

import com.example.service.*
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * App Store 환불 컨트롤러
 * 
 * App Store 환불 관련 REST API를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/ios/refunds")
class AppStoreRefundController(
    private val refundService: AppStoreRefundService
) {
    
    private val logger = LoggerFactory.getLogger(AppStoreRefundController::class.java)
    
    /**
     * 환불 요청 처리
     */
    @PostMapping
    fun processRefund(@RequestBody request: AppStoreRefundRequest): ResponseEntity<AppStoreRefundResult> {
        logger.info("Received App Store refund request: ${request.id}")
        
        return try {
            val result = refundService.processRefundRequest(request)
            
            if (result.success) {
                ResponseEntity.ok(result)
            } else {
                ResponseEntity.badRequest().body(result)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to process refund request", e)
            ResponseEntity.internalServerError().body(
                AppStoreRefundResult(
                    success = false,
                    refundRequestId = null,
                    error = "Internal server error: ${e.message}",
                    timestamp = java.time.LocalDateTime.now()
                )
            )
        }
    }
    
    /**
     * 환불 상태 조회
     */
    @GetMapping("/{refundRequestId}")
    fun getRefundStatus(@PathVariable refundRequestId: String): ResponseEntity<AppStoreRefundStatus> {
        logger.info("Getting refund status: $refundRequestId")
        
        return try {
            val status = refundService.getRefundStatus(refundRequestId)
            ResponseEntity.ok(status)
        } catch (e: Exception) {
            logger.error("Failed to get refund status", e)
            ResponseEntity.internalServerError().build()
        }
    }
    
    /**
     * 구독 환불 요청
     */
    @PostMapping("/subscriptions")
    fun requestSubscriptionRefund(
        @RequestParam originalTransactionId: String,
        @RequestParam reason: AppStoreRefundReason,
        @RequestParam(required = false) amount: java.math.BigDecimal?,
        @RequestParam(defaultValue = "USD") currency: String
    ): ResponseEntity<AppStoreRefundResult> {
        logger.info("Requesting subscription refund: $originalTransactionId")
        
        return try {
            val result = refundService.requestSubscriptionRefund(
                originalTransactionId = originalTransactionId,
                reason = reason,
                amount = amount,
                currency = currency
            )
            
            if (result.success) {
                ResponseEntity.ok(result)
            } else {
                ResponseEntity.badRequest().body(result)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to request subscription refund", e)
            ResponseEntity.internalServerError().build()
        }
    }
    
    /**
     * 인앱결제 환불 요청
     */
    @PostMapping("/purchases")
    fun requestInAppPurchaseRefund(
        @RequestParam transactionId: String,
        @RequestParam reason: AppStoreRefundReason,
        @RequestParam(required = false) amount: java.math.BigDecimal?,
        @RequestParam(defaultValue = "USD") currency: String
    ): ResponseEntity<AppStoreRefundResult> {
        logger.info("Requesting in-app purchase refund: $transactionId")
        
        return try {
            val result = refundService.requestInAppPurchaseRefund(
                transactionId = transactionId,
                reason = reason,
                amount = amount,
                currency = currency
            )
            
            if (result.success) {
                ResponseEntity.ok(result)
            } else {
                ResponseEntity.badRequest().body(result)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to request in-app purchase refund", e)
            ResponseEntity.internalServerError().build()
        }
    }
}