package com.example.integration.infrastructure.web

import com.example.integration.application.port.`in`.PaymentVerificationUseCase
import com.example.integration.application.port.`in`.PaymentCompletionUseCase
import com.example.integration.application.port.`in`.SubscriptionVerificationRequest
import com.example.integration.application.port.`in`.PaymentCompletionRequest
import com.example.integration.domain.Platform
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 결제 관련 REST API 컨트롤러 (Infrastructure Layer)
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentVerificationUseCase: PaymentVerificationUseCase,
    private val paymentCompletionUseCase: PaymentCompletionUseCase
) {
    
    private val logger = LoggerFactory.getLogger(PaymentController::class.java)
    
    /**
     * 구독 결제 검증
     */
    @PostMapping("/verify")
    fun verifyPayment(@RequestBody request: PaymentVerificationRequestDto): ResponseEntity<PaymentVerificationResponseDto> {
        logger.info("Payment verification request: platform=${request.platform}, productId=${request.productId}")
        
        val verificationRequest = SubscriptionVerificationRequest(
            platform = request.platform,
            packageName = request.packageName,
            bundleId = request.bundleId,
            productId = request.productId,
            purchaseToken = request.purchaseToken,
            userId = request.userId
        )
        
        val result = paymentVerificationUseCase.verifySubscriptionPayment(verificationRequest)
        
        val response = PaymentVerificationResponseDto(
            success = result.isValid,
            subscriptionId = result.subscription?.id?.toString(),
            message = result.errorMessage ?: "Verification completed",
            platformData = result.platformData
        )
        
        return if (result.isValid) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }
    
    /**
     * 결제 지급 완료 처리
     */
    @PostMapping("/complete")
    fun completePayment(@RequestBody request: PaymentCompletionRequestDto): ResponseEntity<PaymentCompletionResponseDto> {
        logger.info("Payment completion request: platform=${request.platform}, productId=${request.productId}")
        
        val completionRequest = PaymentCompletionRequest(
            platform = request.platform,
            packageName = request.packageName,
            bundleId = request.bundleId,
            productId = request.productId,
            purchaseToken = request.purchaseToken,
            userId = request.userId
        )
        
        val result = paymentCompletionUseCase.completeSubscriptionPayment(completionRequest)
        
        val response = PaymentCompletionResponseDto(
            success = result.success,
            paymentId = result.paymentId,
            completedAt = result.completedAt?.toString(),
            message = result.errorMessage ?: "Payment completed successfully"
        )
        
        return if (result.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }
}

/**
 * 결제 검증 요청 DTO
 */
data class PaymentVerificationRequestDto(
    val platform: Platform,
    val packageName: String? = null,
    val bundleId: String? = null,
    val productId: String,
    val purchaseToken: String,
    val userId: String
)

/**
 * 결제 검증 응답 DTO
 */
data class PaymentVerificationResponseDto(
    val success: Boolean,
    val subscriptionId: String?,
    val message: String,
    val platformData: Map<String, Any> = emptyMap()
)

/**
 * 결제 완료 요청 DTO
 */
data class PaymentCompletionRequestDto(
    val platform: Platform,
    val packageName: String? = null,
    val bundleId: String? = null,
    val productId: String,
    val purchaseToken: String,
    val userId: String
)

/**
 * 결제 완료 응답 DTO
 */
data class PaymentCompletionResponseDto(
    val success: Boolean,
    val paymentId: String?,
    val completedAt: String?,
    val message: String
)