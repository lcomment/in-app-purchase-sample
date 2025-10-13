package com.example.integration.application.service

import com.example.integration.application.port.`in`.PaymentCompletionUseCase
import com.example.integration.application.port.`in`.PaymentCompletionRequest
import com.example.integration.application.port.`in`.PaymentCompletionResult
import com.example.integration.application.port.out.PaymentRepositoryPort
import com.example.integration.domain.Payment
import com.example.integration.domain.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 지급 완료 처리 서비스 (Application Layer)
 */
@Service
@Transactional
class PaymentCompletionService(
    private val platformAdapterFactory: PlatformAdapterFactory,
    private val paymentRepository: PaymentRepositoryPort
) : PaymentCompletionUseCase {
    
    private val logger = LoggerFactory.getLogger(PaymentCompletionService::class.java)
    
    override fun completeSubscriptionPayment(request: PaymentCompletionRequest): PaymentCompletionResult {
        logger.info("Completing subscription payment: platform=${request.platform}, productId=${request.productId}")
        
        try {
            // 기존 결제 정보 조회
            val existingPayment = paymentRepository.findByPurchaseToken(request.purchaseToken)
            if (existingPayment?.acknowledgmentState == true) {
                logger.warn("Payment already acknowledged: ${request.purchaseToken}")
                return PaymentCompletionResult(
                    success = true,
                    paymentId = existingPayment.id,
                    completedAt = existingPayment.updatedAt,
                    errorMessage = "이미 완료된 결제입니다"
                )
            }
            
            // 플랫폼별 어댑터 가져오기
            val adapter = platformAdapterFactory.getAdapter(request.platform)
            
            // 플랫폼에 지급 완료 처리
            val acknowledgmentResult = adapter.acknowledgePayment(
                productId = request.productId,
                purchaseToken = request.purchaseToken,
                packageName = request.packageName,
                bundleId = request.bundleId
            )
            
            if (!acknowledgmentResult.success) {
                logger.error("Platform acknowledgment failed: ${acknowledgmentResult.errorMessage}")
                return PaymentCompletionResult(
                    success = false,
                    paymentId = null,
                    completedAt = null,
                    errorMessage = acknowledgmentResult.errorMessage
                )
            }
            
            // 결제 상태 업데이트
            val updatedPayment = if (existingPayment != null) {
                val acknowledgedPayment = existingPayment.acknowledge()
                paymentRepository.save(acknowledgedPayment)
            } else {
                // 결제 정보가 없는 경우 새로 생성 (일반적이지 않은 상황)
                logger.warn("Payment not found for token: ${request.purchaseToken}, creating new payment record")
                val newPayment = Payment(
                    id = UUID.randomUUID().toString(),
                    subscriptionId = "", // 구독 정보가 필요하지만 현재 컨텍스트에서는 알 수 없음
                    userId = request.userId,
                    platform = request.platform,
                    orderId = acknowledgmentResult.paymentId ?: "",
                    transactionId = acknowledgmentResult.paymentId ?: "",
                    purchaseToken = request.purchaseToken,
                    productId = request.productId,
                    amount = java.math.BigDecimal.ZERO, // 실제 금액은 플랫폼에서 가져와야 함
                    currency = "USD",
                    status = PaymentStatus.SUCCESS,
                    paymentDate = LocalDateTime.now(),
                    acknowledgmentState = true
                )
                paymentRepository.save(newPayment)
            }
            
            logger.info("Payment completion successful: paymentId=${updatedPayment.id}")
            
            return PaymentCompletionResult(
                success = true,
                paymentId = updatedPayment.id,
                completedAt = updatedPayment.updatedAt
            )
            
        } catch (e: Exception) {
            logger.error("Payment completion failed", e)
            return PaymentCompletionResult(
                success = false,
                paymentId = null,
                completedAt = null,
                errorMessage = "지급 완료 처리 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
}