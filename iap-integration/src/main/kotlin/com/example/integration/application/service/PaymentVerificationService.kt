package com.example.integration.application.service

import com.example.integration.application.port.`in`.PaymentVerificationUseCase
import com.example.integration.application.port.`in`.SubscriptionVerificationRequest
import com.example.integration.application.port.`in`.SubscriptionVerificationResult
import com.example.integration.application.port.out.PaymentRepositoryPort
import com.example.integration.application.port.out.MemberSubscriptionRepositoryPort
import com.example.integration.domain.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 결제 검증 서비스 (Application Layer)
 */
@Service
@Transactional
class PaymentVerificationService(
    private val platformAdapterFactory: PlatformAdapterFactory,
    private val paymentRepository: PaymentRepositoryPort,
    private val memberSubscriptionRepository: MemberSubscriptionRepositoryPort
) : PaymentVerificationUseCase {
    
    private val logger = LoggerFactory.getLogger(PaymentVerificationService::class.java)
    
    override fun verifySubscriptionPayment(request: SubscriptionVerificationRequest): SubscriptionVerificationResult {
        logger.info("Verifying subscription payment: platform=${request.platform}, productId=${request.productId}")
        
        try {
            // 중복 결제 확인 (기존 결제 및 회원 구독 모두 확인)
            if (paymentRepository.existsByPurchaseToken(request.purchaseToken) || 
                memberSubscriptionRepository.findByPurchaseToken(request.purchaseToken) != null) {
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
            
            // 회원 정보 생성 (실제로는 DB에서 조회해야 함)
            val member = Member(
                id = request.userId.toLongOrNull() ?: 0L,
                email = "user@example.com", // 실제로는 회원 서비스에서 조회
                password = ""
            )
            
            // 구독 시작일과 종료일 계산
            val startDateTime = LocalDateTime.now()
            val endDateTime = startDateTime.plusDays(subscription.subscriptionPeriod)
            
            // 결제 정보 생성 (간단한 버전)
            val payment = Payment(
                id = UUID.randomUUID().toString(),
                subscriptionId = subscription.id.toString(),
                userId = request.userId,
                platform = request.platform,
                orderId = UUID.randomUUID().toString(),
                transactionId = request.purchaseToken,
                purchaseToken = request.purchaseToken,
                productId = request.productId,
                amount = subscription.pricePerMonth.toBigDecimal().divide(100.toBigDecimal()),
                currency = "KRW",
                status = PaymentStatus.SUCCESS,
                paymentDate = startDateTime
            )
            
            // 회원 구독 정보 생성
            val memberSubscription = MemberSubscription(
                member = member,
                subscription = subscription,
                payment = payment,
                startDateTime = startDateTime,
                endDateTime = endDateTime,
                status = MemberSubscriptionStatus.BEFORE_PAID, // 아직 지급 완료되지 않음
                purchaseToken = request.purchaseToken,
                productId = request.productId
            )
            
            // 회원 구독 정보 저장
            val savedMemberSubscription = memberSubscriptionRepository.save(memberSubscription)
            logger.info("MemberSubscription created: id=${savedMemberSubscription.id}, memberId=${member.id}")
            
            return SubscriptionVerificationResult(
                isValid = true,
                subscription = subscription,
                platformData = verificationResult.rawData + mapOf("memberSubscriptionId" to savedMemberSubscription.id)
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