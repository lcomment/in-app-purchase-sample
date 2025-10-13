package com.example.integration.application.service

import com.example.integration.application.port.`in`.PaymentVerificationUseCase
import com.example.integration.application.port.`in`.SubscriptionVerificationRequest
import com.example.integration.application.port.`in`.SubscriptionVerificationResult
import com.example.integration.application.port.out.PaymentRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * 결제 검증 서비스 (Application Layer)
 */
@Service
@Transactional
class PaymentVerificationService(
    private val platformAdapterFactory: PlatformAdapterFactory,
    private val paymentRepository: PaymentRepositoryPort
) : PaymentVerificationUseCase {
    
    private val logger = LoggerFactory.getLogger(PaymentVerificationService::class.java)
    
    override fun verifySubscriptionPayment(request: SubscriptionVerificationRequest): SubscriptionVerificationResult {
        logger.info("Verifying subscription payment: platform=${request.platform}, productId=${request.productId}")
        
        try {
            // 중복 결제 확인
            if (paymentRepository.existsByPurchaseToken(request.purchaseToken)) {
                logger.warn("Duplicate payment token: ${request.purchaseToken}")
                return SubscriptionVerificationResult(
                    isValid = false,
                    subscription = null,
                    errorMessage = "이미 처리된 결제입니다"
                )
            }
            
            // 플랫폼별 어댑터 가져오기
            val adapter = platformAdapterFactory.getAdapter(request.platform)
            
            // 플랫폼에서 검증
            val verificationResult = adapter.verifySubscription(
                productId = request.productId,
                purchaseToken = request.purchaseToken,
                packageName = request.packageName,
                bundleId = request.bundleId
            )
            
            if (!verificationResult.isValid) {
                logger.warn("Platform verification failed: ${verificationResult.errorMessage}")
                return SubscriptionVerificationResult(
                    isValid = false,
                    subscription = null,
                    errorMessage = verificationResult.errorMessage
                )
            }
            
            val subscription = verificationResult.subscription ?: run {
                logger.error("Platform verification succeeded but no subscription returned")
                return SubscriptionVerificationResult(
                    isValid = false,
                    subscription = null,
                    errorMessage = "플랫폼 검증은 성공했지만 구독 정보를 가져올 수 없습니다"
                )
            }
            
            logger.info("Subscription verification successful: subscriptionId=${subscription.id}")
            
            return SubscriptionVerificationResult(
                isValid = true,
                subscription = subscription,
                platformData = verificationResult.rawData
            )
            
        } catch (e: Exception) {
            logger.error("Payment verification failed", e)
            return SubscriptionVerificationResult(
                isValid = false,
                subscription = null,
                errorMessage = "결제 검증 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
}