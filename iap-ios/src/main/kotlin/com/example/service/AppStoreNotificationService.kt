package com.example.service

import com.example.domain.*
import com.example.domain.payment.event.*
import com.example.dto.*
import com.example.repository.IOSPaymentRepository
import com.example.repository.IOSPaymentEventRepository
import com.example.repository.IOSSubscriptionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Service
class AppStoreNotificationService(
    private val subscriptionRepository: IOSSubscriptionRepository,
    private val paymentRepository: IOSPaymentRepository,
    private val paymentEventRepository: IOSPaymentEventRepository,
    private val appStoreSubscriptionService: AppStoreSubscriptionService,
    private val objectMapper: ObjectMapper
) {

    fun processServerNotification(notificationRequest: AppStoreServerNotificationRequest): Boolean {
        return try {
            // SignedPayload JWT 디코딩
            val notification = decodeSignedPayload(notificationRequest.signedPayload)
                ?: return false

            val notificationType = AppStoreNotificationType.fromTypeName(notification.notificationType)
                ?: return false

            // TransactionInfo 디코딩
            val transactionInfo = decodeSignedTransactionInfo(notification.data.signedTransactionInfo)
                ?: return false

            // RenewalInfo 디코딩 (있는 경우)
            val renewalInfo = notification.data.signedRenewalInfo?.let { 
                decodeSignedRenewalInfo(it) 
            }

            processNotification(notificationType, notification, transactionInfo, renewalInfo)
        } catch (e: Exception) {
            println("Failed to process App Store server notification: ${e.message}")
            false
        }
    }

    private fun decodeSignedPayload(signedPayload: String): AppStoreServerNotification? {
        return try {
            // JWT 형태의 signed payload를 디코딩
            val parts = signedPayload.split(".")
            if (parts.size != 3) return null
            
            val payload = parts[1]
            val decodedBytes = Base64.getUrlDecoder().decode(payload)
            objectMapper.readValue(decodedBytes, AppStoreServerNotification::class.java)
        } catch (e: Exception) {
            println("Failed to decode signed payload: ${e.message}")
            null
        }
    }

    private fun decodeSignedTransactionInfo(signedTransactionInfo: String): TransactionInfo? {
        return try {
            val parts = signedTransactionInfo.split(".")
            if (parts.size != 3) return null
            
            val payload = parts[1]
            val decodedBytes = Base64.getUrlDecoder().decode(payload)
            objectMapper.readValue(decodedBytes, TransactionInfo::class.java)
        } catch (e: Exception) {
            println("Failed to decode signed transaction info: ${e.message}")
            null
        }
    }

    private fun decodeSignedRenewalInfo(signedRenewalInfo: String): RenewalInfo? {
        return try {
            val parts = signedRenewalInfo.split(".")
            if (parts.size != 3) return null
            
            val payload = parts[1]
            val decodedBytes = Base64.getUrlDecoder().decode(payload)
            objectMapper.readValue(decodedBytes, RenewalInfo::class.java)
        } catch (e: Exception) {
            println("Failed to decode signed renewal info: ${e.message}")
            null
        }
    }

    private fun processNotification(
        notificationType: AppStoreNotificationType,
        notification: AppStoreServerNotification,
        transactionInfo: TransactionInfo,
        renewalInfo: RenewalInfo?
    ): Boolean {
        // 기존 구독 조회
        val existingSubscription = subscriptionRepository.findByOriginalTransactionId(transactionInfo.originalTransactionId)

        return when (notificationType) {
            AppStoreNotificationType.SUBSCRIBED -> {
                handleSubscribed(transactionInfo, notification)
            }
            AppStoreNotificationType.DID_RENEW -> {
                handleDidRenew(transactionInfo, existingSubscription, renewalInfo)
            }
            AppStoreNotificationType.EXPIRED -> {
                handleExpired(transactionInfo, existingSubscription)
            }
            AppStoreNotificationType.DID_CHANGE_RENEWAL_STATUS -> {
                handleRenewalStatusChanged(transactionInfo, existingSubscription, renewalInfo)
            }
            AppStoreNotificationType.DID_FAIL_TO_RENEW -> {
                handleFailedToRenew(transactionInfo, existingSubscription, notification.subtype)
            }
            AppStoreNotificationType.GRACE_PERIOD_EXPIRED -> {
                handleGracePeriodExpired(transactionInfo, existingSubscription)
            }
            AppStoreNotificationType.REFUND -> {
                handleRefund(transactionInfo, existingSubscription)
            }
            AppStoreNotificationType.REVOKE -> {
                handleRevoke(transactionInfo, existingSubscription)
            }
            AppStoreNotificationType.TEST -> {
                handleTest(notification)
            }
            else -> {
                println("Unhandled notification type: ${notificationType.description}")
                true
            }
        }
    }

    private fun handleSubscribed(
        transactionInfo: TransactionInfo,
        notification: AppStoreServerNotification
    ): Boolean {
        // 이미 존재하는 구독인지 확인
        val existingSubscription = subscriptionRepository.findByOriginalTransactionId(transactionInfo.originalTransactionId)
        if (existingSubscription != null) {
            println("Subscription already exists for originalTransactionId: ${transactionInfo.originalTransactionId}")
            return true
        }

        // 새 구독 생성 (실제로는 App Store Connect API로 재검증 필요)
        return true
    }

    private fun handleDidRenew(
        transactionInfo: TransactionInfo,
        existingSubscription: Subscription?,
        renewalInfo: RenewalInfo?
    ): Boolean {
        if (existingSubscription == null) {
            println("Subscription not found for renewal: ${transactionInfo.originalTransactionId}")
            return false
        }

        try {
            val newExpiryDate = transactionInfo.expiresDate?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
            } ?: return false

            // 구독 갱신 처리
            val renewedSubscription = existingSubscription.renew(newExpiryDate)
            subscriptionRepository.save(renewedSubscription)

            // PaymentEvent 생성
            createPaymentEvent(existingSubscription.id, PaymentEventType.RENEWAL, Platform.IOS)

            println("Subscription renewed successfully: ${existingSubscription.id}")
            return true
        } catch (e: Exception) {
            println("Failed to renew subscription: ${e.message}")
            return false
        }
    }

    private fun handleExpired(
        transactionInfo: TransactionInfo,
        existingSubscription: Subscription?
    ): Boolean {
        if (existingSubscription == null) {
            println("Subscription not found for expiration: ${transactionInfo.originalTransactionId}")
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
        createPaymentEvent(existingSubscription.id, PaymentEventType.EXPIRATION, Platform.IOS)

        println("Subscription expired successfully: ${existingSubscription.id}")
        return true
    }

    private fun handleRenewalStatusChanged(
        transactionInfo: TransactionInfo,
        existingSubscription: Subscription?,
        renewalInfo: RenewalInfo?
    ): Boolean {
        if (existingSubscription == null || renewalInfo == null) return false

        val autoRenewing = renewalInfo.autoRenewStatus == 1
        
        val updatedSubscription = existingSubscription.copy(
            autoRenewing = autoRenewing,
            updatedAt = LocalDateTime.now()
        )
        subscriptionRepository.save(updatedSubscription)

        println("Subscription auto-renewal status changed: ${existingSubscription.id} -> $autoRenewing")
        return true
    }

    private fun handleFailedToRenew(
        transactionInfo: TransactionInfo,
        existingSubscription: Subscription?,
        subtype: String?
    ): Boolean {
        if (existingSubscription == null) return false

        val newStatus = when (subtype) {
            "GRACE_PERIOD" -> SubscriptionStatus.IN_GRACE_PERIOD
            "BILLING_RETRY" -> SubscriptionStatus.ON_HOLD
            else -> SubscriptionStatus.ON_HOLD
        }

        val failedSubscription = existingSubscription.copy(
            status = newStatus,
            updatedAt = LocalDateTime.now()
        )
        subscriptionRepository.save(failedSubscription)

        val eventType = if (newStatus == SubscriptionStatus.IN_GRACE_PERIOD) {
            PaymentEventType.GRACE_PERIOD_START
        } else {
            PaymentEventType.PAUSE
        }
        
        createPaymentEvent(existingSubscription.id, eventType, Platform.IOS)
        return true
    }

    private fun handleGracePeriodExpired(
        transactionInfo: TransactionInfo,
        existingSubscription: Subscription?
    ): Boolean {
        if (existingSubscription == null) return false

        val expiredSubscription = existingSubscription.copy(
            status = SubscriptionStatus.EXPIRED,
            autoRenewing = false,
            updatedAt = LocalDateTime.now()
        )
        subscriptionRepository.save(expiredSubscription)

        createPaymentEvent(existingSubscription.id, PaymentEventType.GRACE_PERIOD_END, Platform.IOS)
        return true
    }

    private fun handleRefund(
        transactionInfo: TransactionInfo,
        existingSubscription: Subscription?
    ): Boolean {
        if (existingSubscription == null) return false

        // 환불 처리 - 구독 취소
        val refundedSubscription = existingSubscription.copy(
            status = SubscriptionStatus.CANCELED,
            autoRenewing = false,
            updatedAt = LocalDateTime.now()
        )
        subscriptionRepository.save(refundedSubscription)

        createPaymentEvent(existingSubscription.id, PaymentEventType.REFUND, Platform.IOS)
        return true
    }

    private fun handleRevoke(
        transactionInfo: TransactionInfo,
        existingSubscription: Subscription?
    ): Boolean {
        if (existingSubscription == null) return false

        val revokedSubscription = existingSubscription.copy(
            status = SubscriptionStatus.CANCELED,
            autoRenewing = false,
            updatedAt = LocalDateTime.now()
        )
        subscriptionRepository.save(revokedSubscription)

        createPaymentEvent(existingSubscription.id, PaymentEventType.CANCELLATION, Platform.IOS)
        return true
    }

    private fun handleTest(notification: AppStoreServerNotification): Boolean {
        println("Received test notification: ${notification.notificationUUID}")
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