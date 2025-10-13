package com.example.service

import com.example.domain.*
import com.example.domain.payment.event.*
import com.example.dto.*
import com.example.repository.PaymentRepository
import com.example.repository.PaymentEventRepository
import com.example.repository.SubscriptionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Service
class RTDNEventService(
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
    private val googlePlaySubscriptionService: GooglePlaySubscriptionService,
    private val objectMapper: ObjectMapper
) {

    fun processRTDNNotification(rtdnRequest: GooglePlayRTDNRequest): Boolean {
        return try {
            // Base64 디코딩하여 실제 notification 데이터 추출
            val decodedData = String(Base64.getDecoder().decode(rtdnRequest.message.data))
            val notification = objectMapper.readValue(decodedData, RTDNNotification::class.java)
            
            when {
                notification.subscriptionNotification != null -> {
                    processSubscriptionNotification(notification.subscriptionNotification, notification)
                }
                notification.testNotification != null -> {
                    processTestNotification(notification.testNotification)
                }
                else -> {
                    println("Unknown notification type received")
                    true // 알 수 없는 타입이지만 처리 완료로 표시
                }
            }
        } catch (e: Exception) {
            println("Failed to process RTDN notification: ${e.message}")
            false
        }
    }

    private fun processSubscriptionNotification(
        subscriptionNotification: SubscriptionNotification,
        notification: RTDNNotification
    ): Boolean {
        val notificationType = RTDNNotificationType.fromCode(subscriptionNotification.notificationType)
            ?: return false

        // 기존 구독 조회
        val existingSubscription = subscriptionRepository.findByPurchaseToken(subscriptionNotification.purchaseToken)

        return when (notificationType) {
            RTDNNotificationType.SUBSCRIPTION_PURCHASED -> {
                handleSubscriptionPurchased(subscriptionNotification, notification)
            }
            RTDNNotificationType.SUBSCRIPTION_RENEWED -> {
                handleSubscriptionRenewed(subscriptionNotification, existingSubscription)
            }
            RTDNNotificationType.SUBSCRIPTION_CANCELED -> {
                handleSubscriptionCanceled(subscriptionNotification, existingSubscription)
            }
            RTDNNotificationType.SUBSCRIPTION_EXPIRED -> {
                handleSubscriptionExpired(subscriptionNotification, existingSubscription)
            }
            RTDNNotificationType.SUBSCRIPTION_ON_HOLD -> {
                handleSubscriptionOnHold(subscriptionNotification, existingSubscription)
            }
            RTDNNotificationType.SUBSCRIPTION_IN_GRACE_PERIOD -> {
                handleSubscriptionInGracePeriod(subscriptionNotification, existingSubscription)
            }
            RTDNNotificationType.SUBSCRIPTION_RECOVERED -> {
                handleSubscriptionRecovered(subscriptionNotification, existingSubscription)
            }
            else -> {
                println("Unhandled notification type: ${notificationType.description}")
                true
            }
        }
    }

    private fun handleSubscriptionPurchased(
        subscriptionNotification: SubscriptionNotification,
        notification: RTDNNotification
    ): Boolean {
        // 이미 존재하는 구독인지 확인
        val existingSubscription = subscriptionRepository.findByPurchaseToken(subscriptionNotification.purchaseToken)
        if (existingSubscription != null) {
            println("Subscription already exists for purchaseToken: ${subscriptionNotification.purchaseToken}")
            return true
        }

        // Google Play API로 구독 정보 조회하여 새 구독 생성
        // 실제로는 purchaseToken으로 다시 검증해야 하지만 여기서는 간소화
        return true
    }

    private fun handleSubscriptionRenewed(
        subscriptionNotification: SubscriptionNotification,
        existingSubscription: Subscription?
    ): Boolean {
        if (existingSubscription == null) {
            println("Subscription not found for renewal: ${subscriptionNotification.purchaseToken}")
            return false
        }

        try {
            // Google Play API로 최신 구독 정보 조회
            val verificationRequest = com.example.dto.SubscriptionVerificationRequest(
                packageName = "com.example.mock", // 실제로는 설정에서 가져와야 함
                subscriptionId = subscriptionNotification.subscriptionId,
                purchaseToken = subscriptionNotification.purchaseToken,
                userId = existingSubscription.userId
            )
            
            val verificationResponse = googlePlaySubscriptionService.verifySubscription(verificationRequest)
            
            if (verificationResponse.isValid && verificationResponse.expiryTime != null) {
                // 구독 갱신 처리
                val renewedSubscription = existingSubscription.renew(verificationResponse.expiryTime)
                subscriptionRepository.save(renewedSubscription)

                // PaymentEvent 생성
                createPaymentEvent(existingSubscription.id, PaymentEventType.RENEWAL, Platform.AOS)
                
                println("Subscription renewed successfully: ${existingSubscription.id}")
                return true
            }
        } catch (e: Exception) {
            println("Failed to renew subscription: ${e.message}")
        }
        
        return false
    }

    private fun handleSubscriptionCanceled(
        subscriptionNotification: SubscriptionNotification,
        existingSubscription: Subscription?
    ): Boolean {
        if (existingSubscription == null) {
            println("Subscription not found for cancellation: ${subscriptionNotification.purchaseToken}")
            return false
        }

        // 구독 취소 처리
        val canceledSubscription = existingSubscription.cancel()
        subscriptionRepository.save(canceledSubscription)

        // PaymentEvent 생성
        createPaymentEvent(existingSubscription.id, PaymentEventType.CANCELLATION, Platform.AOS)

        println("Subscription canceled successfully: ${existingSubscription.id}")
        return true
    }

    private fun handleSubscriptionExpired(
        subscriptionNotification: SubscriptionNotification,
        existingSubscription: Subscription?
    ): Boolean {
        if (existingSubscription == null) {
            println("Subscription not found for expiration: ${subscriptionNotification.purchaseToken}")
            return false
        }

        // 구독 만료 처리
        val expiredSubscription = existingSubscription.copy(
            status = SubscriptionStatus.EXPIRED,
            autoRenewing = false,
            updatedAt = LocalDateTime.now()
        )
        subscriptionRepository.save(expiredSubscription)

        // PaymentEvent 생성
        createPaymentEvent(existingSubscription.id, PaymentEventType.EXPIRATION, Platform.AOS)

        println("Subscription expired successfully: ${existingSubscription.id}")
        return true
    }

    private fun handleSubscriptionOnHold(
        subscriptionNotification: SubscriptionNotification,
        existingSubscription: Subscription?
    ): Boolean {
        if (existingSubscription == null) return false

        val onHoldSubscription = existingSubscription.copy(
            status = SubscriptionStatus.ON_HOLD,
            updatedAt = LocalDateTime.now()
        )
        subscriptionRepository.save(onHoldSubscription)

        createPaymentEvent(existingSubscription.id, PaymentEventType.PAUSE, Platform.AOS)
        return true
    }

    private fun handleSubscriptionInGracePeriod(
        subscriptionNotification: SubscriptionNotification,
        existingSubscription: Subscription?
    ): Boolean {
        if (existingSubscription == null) return false

        val gracePeriodSubscription = existingSubscription.copy(
            status = SubscriptionStatus.IN_GRACE_PERIOD,
            updatedAt = LocalDateTime.now()
        )
        subscriptionRepository.save(gracePeriodSubscription)

        createPaymentEvent(existingSubscription.id, PaymentEventType.GRACE_PERIOD_START, Platform.AOS)
        return true
    }

    private fun handleSubscriptionRecovered(
        subscriptionNotification: SubscriptionNotification,
        existingSubscription: Subscription?
    ): Boolean {
        if (existingSubscription == null) return false

        val recoveredSubscription = existingSubscription.copy(
            status = SubscriptionStatus.ACTIVE,
            updatedAt = LocalDateTime.now()
        )
        subscriptionRepository.save(recoveredSubscription)

        createPaymentEvent(existingSubscription.id, PaymentEventType.RESUME, Platform.AOS)
        return true
    }

    private fun processTestNotification(testNotification: TestNotification): Boolean {
        println("Received test notification: ${testNotification.version}")
        return true
    }

    private fun createPaymentEvent(
        subscriptionId: String,
        eventType: PaymentEventType,
        platform: Platform
    ) {
        val paymentEvent = PaymentEvent(
            id = UUID.randomUUID().toString(),
            subscriptionId = subscriptionId,
            eventType = eventType,
            platform = platform,
            createdAt = LocalDateTime.now()
        )
        
        // PaymentEvent 저장
        paymentEventRepository.save(paymentEvent)
        println("PaymentEvent saved: ${paymentEvent.eventType} for subscription: $subscriptionId")
    }
}