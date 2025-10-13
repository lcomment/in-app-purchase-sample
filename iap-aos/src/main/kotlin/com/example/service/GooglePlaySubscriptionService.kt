package com.example.service

import com.example.domain.*
import com.example.domain.payment.completion.requests.*
import com.example.dto.SubscriptionVerificationRequest
import com.example.dto.SubscriptionVerificationResponse
import com.example.repository.PaymentRepository
import com.example.repository.SubscriptionRepository
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Service
class GooglePlaySubscriptionService(
    private val androidPublisher: AndroidPublisher,
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentCompletionService: GooglePlayPaymentCompletionService
) {

    fun verifySubscription(request: SubscriptionVerificationRequest): SubscriptionVerificationResponse {
        return try {
            val subscriptionPurchase = androidPublisher.purchases()
                .subscriptionsv2()
                .get(request.packageName, request.purchaseToken)
                .execute()

            val verification = processSubscriptionData(subscriptionPurchase, request)
            verification
        } catch (e: Exception) {
            SubscriptionVerificationResponse(
                subscriptionId = request.subscriptionId,
                userId = request.userId,
                status = SubscriptionStatus.EXPIRED,
                isValid = false,
                startTime = null,
                expiryTime = null,
                autoRenewing = false,
                orderId = null,
                message = "Verification failed: ${e.message}"
            )
        }
    }

    private fun processSubscriptionData(
        subscriptionPurchase: SubscriptionPurchaseV2,
        request: SubscriptionVerificationRequest
    ): SubscriptionVerificationResponse {
        val subscriptionState = subscriptionPurchase.subscriptionState
        val lineItems = subscriptionPurchase.lineItems
        val startTime = subscriptionPurchase.startTime?.let { 
            convertTimestampToLocalDateTime(it) 
        }

        val latestOrderId = subscriptionPurchase.latestOrderId
        val autoRenewing = lineItems?.firstOrNull()?.let { lineItem ->
            lineItem.autoRenewingPlan?.autoRenewEnabled ?: false
        } ?: false

        val lineItem = lineItems?.firstOrNull()
        val expiryTime = lineItem?.expiryTime?.let { 
            convertTimestampToLocalDateTime(it) 
        }

        val status = mapGooglePlayStatusToOurStatus(subscriptionState)
        val isValid = isSubscriptionValid(status, expiryTime)

        return SubscriptionVerificationResponse(
            subscriptionId = request.subscriptionId,
            userId = request.userId,
            status = status,
            isValid = isValid,
            startTime = startTime,
            expiryTime = expiryTime,
            autoRenewing = autoRenewing,
            orderId = latestOrderId,
            message = if (isValid) "Subscription verified successfully" else "Subscription is not valid"
        )
    }

    private fun mapGooglePlayStatusToOurStatus(subscriptionState: String?): SubscriptionStatus {
        return when (subscriptionState) {
            "SUBSCRIPTION_STATE_ACTIVE" -> SubscriptionStatus.ACTIVE
            "SUBSCRIPTION_STATE_CANCELED" -> SubscriptionStatus.CANCELED
            "SUBSCRIPTION_STATE_EXPIRED" -> SubscriptionStatus.EXPIRED
            "SUBSCRIPTION_STATE_ON_HOLD" -> SubscriptionStatus.ON_HOLD
            "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" -> SubscriptionStatus.IN_GRACE_PERIOD
            "SUBSCRIPTION_STATE_PAUSED" -> SubscriptionStatus.PAUSED
            else -> SubscriptionStatus.EXPIRED
        }
    }

    private fun isSubscriptionValid(status: SubscriptionStatus, expiryTime: LocalDateTime?): Boolean {
        return when (status) {
            SubscriptionStatus.ACTIVE -> {
                expiryTime?.isAfter(LocalDateTime.now()) ?: false
            }
            SubscriptionStatus.IN_GRACE_PERIOD -> true
            else -> false
        }
    }

    private fun convertTimestampToLocalDateTime(timestamp: String): LocalDateTime {
        return try {
            val instant = Instant.parse(timestamp)
            instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
        } catch (e: Exception) {
            LocalDateTime.now()
        }
    }

    fun createSubscriptionFromVerification(
        request: SubscriptionVerificationRequest,
        response: SubscriptionVerificationResponse
    ): Subscription? {
        if (!response.isValid) return null

        return Subscription(
            id = UUID.randomUUID().toString(),
            userId = request.userId,
            planId = request.subscriptionId,
            platform = Platform.AOS,
            purchaseToken = request.purchaseToken,
            orderId = response.orderId ?: "",
            status = response.status,
            startDate = response.startTime ?: LocalDateTime.now(),
            expiryDate = response.expiryTime ?: LocalDateTime.now().plusDays(30),
            autoRenewing = response.autoRenewing
        )
    }

    fun processSubscriptionPurchase(
        request: SubscriptionVerificationRequest,
        response: SubscriptionVerificationResponse
    ): Subscription? {
        if (!response.isValid) return null

        // 중복 구매 방지
        if (subscriptionRepository.existsByPurchaseToken(request.purchaseToken)) {
            return subscriptionRepository.findByPurchaseToken(request.purchaseToken)
        }

        // 구독 생성 및 저장
        val subscription = createSubscriptionFromVerification(request, response)
            ?: return null
        
        val savedSubscription = subscriptionRepository.save(subscription)

        // 결제 기록 생성 및 저장
        val payment = createPaymentFromSubscription(savedSubscription, request)
        val savedPayment = paymentRepository.save(payment)

        // 지급 완료 처리 (Acknowledge)
        val completionRequest = SubscriptionCompletionRequest(
            platform = Platform.AOS,
            packageName = request.packageName,
            subscriptionId = request.subscriptionId,
            purchaseToken = request.purchaseToken,
            userId = request.userId
        )
        
        val completionResult = paymentCompletionService.completeSubscriptionPayment(completionRequest)
        
        if (completionResult.success) {
            // 결제 상태를 Acknowledged로 업데이트
            val acknowledgedPayment = savedPayment.acknowledge()
            paymentRepository.save(acknowledgedPayment)
            
            println("Successfully acknowledged subscription: ${savedSubscription.id}")
        } else {
            println("Failed to acknowledge subscription: ${savedSubscription.id} - ${completionResult.message}")
        }

        return savedSubscription
    }

    private fun createPaymentFromSubscription(
        subscription: Subscription,
        request: SubscriptionVerificationRequest
    ): Payment {
        return Payment(
            id = UUID.randomUUID().toString(),
            subscriptionId = subscription.id,
            userId = subscription.userId,
            platform = Platform.AOS,
            orderId = subscription.orderId,
            transactionId = subscription.orderId,
            purchaseToken = subscription.purchaseToken,
            productId = request.subscriptionId,
            amount = BigDecimal("9.99"), // Mock price - 실제로는 Google Play API에서 가져와야 함
            currency = "USD",
            status = PaymentStatus.SUCCESS,
            paymentDate = subscription.startDate,
            acknowledgmentState = false
        )
    }

    fun getUserActiveSubscriptions(userId: String): List<Subscription> {
        return subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
    }

    fun getUserSubscriptions(userId: String): List<Subscription> {
        return subscriptionRepository.findByUserId(userId)
    }

    fun getSubscriptionById(subscriptionId: String): Subscription? {
        return subscriptionRepository.findById(subscriptionId)
    }
}