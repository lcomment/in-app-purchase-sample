package com.example.service

import com.example.config.AppStoreConfig
import com.example.domain.*
import com.example.domain.payment.completion.requests.*
import com.example.dto.IOSSubscriptionVerificationRequest
import com.example.dto.IOSSubscriptionVerificationResponse
import com.example.repository.IOSPaymentRepository
import com.example.repository.IOSSubscriptionRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Service
class AppStoreSubscriptionService(
    private val appStoreConfig: AppStoreConfig,
    private val objectMapper: ObjectMapper,
    private val subscriptionRepository: IOSSubscriptionRepository,
    private val paymentRepository: IOSPaymentRepository,
    private val paymentCompletionService: AppStorePaymentCompletionService
) {

    private val restClient = RestClient.builder()
        .baseUrl("https://api.storekit-itunes.apple.com")
        .build()

    fun verifySubscription(request: IOSSubscriptionVerificationRequest): IOSSubscriptionVerificationResponse {
        return try {
            val transactionInfo = getTransactionInfo(request.transactionId)
            val subscriptionStatus = getSubscriptionStatus(request.originalTransactionId)
            
            processTransactionData(transactionInfo, subscriptionStatus, request)
        } catch (e: Exception) {
            IOSSubscriptionVerificationResponse(
                subscriptionId = request.productId,
                userId = request.userId,
                status = SubscriptionStatus.EXPIRED,
                isValid = false,
                startTime = null,
                expiryTime = null,
                autoRenewing = false,
                transactionId = request.transactionId,
                originalTransactionId = request.originalTransactionId,
                message = "Verification failed: ${e.message}"
            )
        }
    }

    private fun getTransactionInfo(transactionId: String): JsonNode? {
        val jwt = appStoreConfig.appStoreJwtToken()
        
        return try {
            val response = restClient.get()
                .uri("/inApps/v1/transactions/$transactionId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
                .retrieve()
                .body(String::class.java)
            
            response?.let { objectMapper.readTree(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun getSubscriptionStatus(originalTransactionId: String): JsonNode? {
        val jwt = appStoreConfig.appStoreJwtToken()
        
        return try {
            val response = restClient.get()
                .uri("/inApps/v1/subscriptions/$originalTransactionId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
                .retrieve()
                .body(String::class.java)
            
            response?.let { objectMapper.readTree(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun processTransactionData(
        transactionInfo: JsonNode?,
        subscriptionStatus: JsonNode?,
        request: IOSSubscriptionVerificationRequest
    ): IOSSubscriptionVerificationResponse {
        
        if (transactionInfo == null) {
            return createErrorResponse(request, "Transaction not found")
        }

        val signedTransactionInfo = transactionInfo.get("signedTransactionInfo")?.asText()
        val decodedTransaction = decodeSignedData(signedTransactionInfo)
        
        val bundleId = decodedTransaction?.get("bundleId")?.asText()
        val productId = decodedTransaction?.get("productId")?.asText()
        val purchaseDate = decodedTransaction?.get("purchaseDate")?.asLong()
        val expiresDate = decodedTransaction?.get("expiresDate")?.asLong()

        // Bundle ID 검증
        if (bundleId != appStoreConfig.getBundleId()) {
            return createErrorResponse(request, "Invalid bundle ID")
        }

        // Product ID 검증
        if (productId != request.productId) {
            return createErrorResponse(request, "Product ID mismatch")
        }

        val startTime = purchaseDate?.let { 
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) 
        }
        
        val expiryTime = expiresDate?.let { 
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) 
        }

        // 구독 상태 확인
        val status = determineSubscriptionStatus(subscriptionStatus, expiryTime)
        val isValid = isSubscriptionValid(status, expiryTime)
        val autoRenewing = getAutoRenewStatus(subscriptionStatus)

        return IOSSubscriptionVerificationResponse(
            subscriptionId = request.productId,
            userId = request.userId,
            status = status,
            isValid = isValid,
            startTime = startTime,
            expiryTime = expiryTime,
            autoRenewing = autoRenewing,
            transactionId = request.transactionId,
            originalTransactionId = request.originalTransactionId,
            message = if (isValid) "Subscription verified successfully" else "Subscription is not valid"
        )
    }

    private fun decodeSignedData(signedData: String?): JsonNode? {
        if (signedData == null) return null
        
        try {
            // JWT 형태의 signed data를 디코딩
            val parts = signedData.split(".")
            if (parts.size != 3) return null
            
            val payload = parts[1]
            val decodedBytes = Base64.getUrlDecoder().decode(payload)
            return objectMapper.readTree(decodedBytes)
        } catch (e: Exception) {
            return null
        }
    }

    private fun determineSubscriptionStatus(subscriptionStatus: JsonNode?, expiryTime: LocalDateTime?): SubscriptionStatus {
        val statusData = subscriptionStatus?.get("data")?.get(0)
        val lastTransactions = statusData?.get("lastTransactions")?.get(0)
        val status = lastTransactions?.get("status")?.asInt()
        
        return when (status) {
            1 -> SubscriptionStatus.ACTIVE
            2 -> SubscriptionStatus.EXPIRED  
            3 -> SubscriptionStatus.IN_GRACE_PERIOD
            4 -> SubscriptionStatus.ON_HOLD
            5 -> SubscriptionStatus.PAUSED
            else -> {
                // 상태가 없으면 만료일로 판단
                if (expiryTime?.isAfter(LocalDateTime.now()) == true) {
                    SubscriptionStatus.ACTIVE
                } else {
                    SubscriptionStatus.EXPIRED
                }
            }
        }
    }

    private fun getAutoRenewStatus(subscriptionStatus: JsonNode?): Boolean {
        return subscriptionStatus?.get("data")?.get(0)
            ?.get("subscriptionGroupIdentifier")?.asText() != null
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

    private fun createErrorResponse(
        request: IOSSubscriptionVerificationRequest, 
        message: String
    ): IOSSubscriptionVerificationResponse {
        return IOSSubscriptionVerificationResponse(
            subscriptionId = request.productId,
            userId = request.userId,
            status = SubscriptionStatus.EXPIRED,
            isValid = false,
            startTime = null,
            expiryTime = null,
            autoRenewing = false,
            transactionId = request.transactionId,
            originalTransactionId = request.originalTransactionId,
            message = message
        )
    }

    fun createSubscriptionFromVerification(
        request: IOSSubscriptionVerificationRequest,
        response: IOSSubscriptionVerificationResponse
    ): Subscription? {
        if (!response.isValid) return null

        return Subscription(
            id = UUID.randomUUID().toString(),
            userId = request.userId,
            planId = request.productId,
            platform = Platform.IOS,
            purchaseToken = request.transactionId,
            orderId = request.originalTransactionId,
            status = response.status,
            startDate = response.startTime ?: LocalDateTime.now(),
            expiryDate = response.expiryTime ?: LocalDateTime.now().plusDays(30),
            autoRenewing = response.autoRenewing
        )
    }

    fun processSubscriptionPurchase(
        request: IOSSubscriptionVerificationRequest,
        response: IOSSubscriptionVerificationResponse
    ): Subscription? {
        if (!response.isValid) return null

        // 중복 구매 방지 (transactionId 또는 originalTransactionId로 확인)
        if (subscriptionRepository.existsByPurchaseToken(request.transactionId) ||
            subscriptionRepository.existsByOriginalTransactionId(request.originalTransactionId)) {
            return subscriptionRepository.findByPurchaseToken(request.transactionId) 
                ?: subscriptionRepository.findByOriginalTransactionId(request.originalTransactionId)
        }

        // 구독 생성 및 저장
        val subscription = createSubscriptionFromVerification(request, response)
            ?: return null
        
        val savedSubscription = subscriptionRepository.save(subscription)

        // 결제 기록 생성 및 저장
        val payment = createPaymentFromSubscription(savedSubscription, request)
        val savedPayment = paymentRepository.save(payment)

        // 지급 완료 처리 (서버 측에서 상태 기록, 클라이언트에서 finishTransaction 필요)
        val completionRequest = SubscriptionCompletionRequest(
            platform = Platform.IOS,
            subscriptionId = request.productId,
            purchaseToken = request.transactionId,
            originalTransactionId = request.originalTransactionId,
            userId = request.userId
        )
        
        val completionResult = paymentCompletionService.completeSubscriptionPayment(completionRequest)
        
        if (completionResult.success) {
            // 서버 측에서는 완료 상태로 기록
            val acknowledgedPayment = savedPayment.acknowledge()
            paymentRepository.save(acknowledgedPayment)
            
            println("Subscription purchase completed on server side: ${savedSubscription.id}")
            println("Client should call finishTransaction for originalTransactionId: ${request.originalTransactionId}")
        } else {
            println("Failed to mark subscription as completed: ${savedSubscription.id} - ${completionResult.message}")
        }

        return savedSubscription
    }

    private fun createPaymentFromSubscription(
        subscription: Subscription,
        request: IOSSubscriptionVerificationRequest
    ): Payment {
        return Payment(
            id = UUID.randomUUID().toString(),
            subscriptionId = subscription.id,
            userId = subscription.userId,
            platform = Platform.IOS,
            orderId = subscription.orderId,
            transactionId = subscription.purchaseToken,
            purchaseToken = subscription.purchaseToken,
            productId = request.productId,
            amount = BigDecimal("9.99"), // Mock price - 실제로는 App Store Connect API에서 가져와야 함
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