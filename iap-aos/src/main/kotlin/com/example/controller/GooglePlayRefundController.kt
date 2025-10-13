package com.example.controller

import com.example.service.GooglePlayRefundRequest
import com.example.service.GooglePlayRefundResult
import com.example.service.GooglePlayRefundService
import com.example.service.GooglePlayRefundStatus
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Google Play 환불 컨트롤러
 * 
 * Google Play 환불 관련 REST API를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/aos/refunds")
class GooglePlayRefundController(
    private val refundService: GooglePlayRefundService
) {
    
    private val logger = LoggerFactory.getLogger(GooglePlayRefundController::class.java)
    
    /**
     * 환불 요청 처리
     */
    @PostMapping
    fun processRefund(@RequestBody request: GooglePlayRefundRequest): ResponseEntity<GooglePlayRefundResult> {
        logger.info("Received Google Play refund request: ${request.id}")
        
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
                GooglePlayRefundResult(
                    success = false,
                    refundId = null,
                    error = "Internal server error: ${e.message}",
                    timestamp = java.time.LocalDateTime.now()
                )
            )
        }
    }
    
    /**
     * 환불 상태 조회
     */
    @GetMapping("/{refundId}")
    fun getRefundStatus(@PathVariable refundId: String): ResponseEntity<GooglePlayRefundStatus> {
        logger.info("Getting refund status: $refundId")
        
        return try {
            val status = refundService.getRefundStatus(refundId)
            ResponseEntity.ok(status)
        } catch (e: Exception) {
            logger.error("Failed to get refund status", e)
            ResponseEntity.internalServerError().build()
        }
    }
    
    /**
     * 구독 환불
     */
    @PostMapping("/subscriptions/{subscriptionId}")
    fun refundSubscription(
        @PathVariable subscriptionId: String,
        @RequestParam packageName: String,
        @RequestParam token: String,
        @RequestParam(required = false) reason: String?
    ): ResponseEntity<GooglePlayRefundResult> {
        logger.info("Refunding subscription: $subscriptionId")
        
        return try {
            val result = refundService.refundSubscription(
                packageName = packageName,
                subscriptionId = subscriptionId,
                token = token,
                reason = reason ?: "Customer request"
            )
            
            if (result.success) {
                ResponseEntity.ok(result)
            } else {
                ResponseEntity.badRequest().body(result)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to refund subscription", e)
            ResponseEntity.internalServerError().build()
        }
    }
    
    /**
     * 인앱결제 환불
     */
    @PostMapping("/purchases/{productId}")
    fun refundInAppPurchase(
        @PathVariable productId: String,
        @RequestParam packageName: String,
        @RequestParam token: String,
        @RequestParam(required = false) reason: String?
    ): ResponseEntity<GooglePlayRefundResult> {
        logger.info("Refunding in-app purchase: $productId")
        
        return try {
            val result = refundService.refundInAppPurchase(
                packageName = packageName,
                productId = productId,
                token = token,
                reason = reason ?: "Customer request"
            )
            
            if (result.success) {
                ResponseEntity.ok(result)
            } else {
                ResponseEntity.badRequest().body(result)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to refund in-app purchase", e)
            ResponseEntity.internalServerError().build()
        }
    }
}