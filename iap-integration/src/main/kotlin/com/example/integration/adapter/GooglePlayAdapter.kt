package com.example.integration.adapter

import com.example.integration.application.port.out.PlatformAdapterPort
import com.example.integration.application.port.out.PlatformVerificationResult
import com.example.integration.application.port.out.PlatformAcknowledgmentResult
import com.example.integration.application.port.out.PlatformSettlementData
import com.example.integration.domain.Platform
import com.example.integration.domain.Subscription
import com.example.integration.domain.SubscriptionStatus
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Google Play 플랫폼 어댑터 (Infrastructure Layer)
 */
@Component
class GooglePlayAdapter(
    @Value("\${app.google-play.access-token:dummy-token}") private val accessToken: String,
    private val objectMapper: ObjectMapper
) : PlatformAdapterPort {
    
    private val logger = LoggerFactory.getLogger(GooglePlayAdapter::class.java)
    
    private val restClient = RestClient.builder()
        .baseUrl("https://androidpublisher.googleapis.com")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        .build()
    
    override fun verifySubscription(
        productId: String,
        purchaseToken: String,
        packageName: String?,
        bundleId: String?
    ): PlatformVerificationResult {
        
        if (packageName == null) {
            return PlatformVerificationResult(
                isValid = false,
                subscription = null,
                errorMessage = "Package name is required for Google Play verification"
            )
        }
        
        return try {
            logger.info("Verifying Google Play subscription: productId=$productId, packageName=$packageName")
            
            // Google Play Developer API 호출
            // https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2/get
            val response = restClient.get()
                .uri("/androidpublisher/v3/applications/{packageName}/purchases/subscriptionsv2/tokens/{token}", 
                     packageName, purchaseToken)
                .retrieve()
                .body(String::class.java)
            
            val subscriptionData = objectMapper.readTree(response)
            val subscription = mapToSubscription(subscriptionData, productId, purchaseToken)
            
            PlatformVerificationResult(
                isValid = true,
                subscription = subscription,
                rawData = mapOf(
                    "subscriptionState" to (subscriptionData.get("subscriptionState")?.asText() ?: ""),
                    "startTime" to (subscriptionData.get("startTime")?.asText() ?: ""),
                    "latestOrderId" to (subscriptionData.get("latestOrderId")?.asText() ?: "")
                )
            )
            
        } catch (e: Exception) {
            logger.error("Google Play subscription verification failed", e)
            PlatformVerificationResult(
                isValid = false,
                subscription = null,
                errorMessage = "Google Play 검증 실패: ${e.message}"
            )
        }
    }
    
    override fun acknowledgePayment(
        productId: String,
        purchaseToken: String,
        packageName: String?,
        bundleId: String?
    ): PlatformAcknowledgmentResult {
        
        if (packageName == null) {
            return PlatformAcknowledgmentResult(
                success = false,
                paymentId = null,
                errorMessage = "Package name is required for Google Play acknowledgment"
            )
        }
        
        return try {
            logger.info("Acknowledging Google Play subscription: productId=$productId, packageName=$packageName")
            
            // Google Play Developer API 호출 - 구독 확인
            // https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2/acknowledge
            restClient.post()
                .uri("/androidpublisher/v3/applications/{packageName}/purchases/subscriptionsv2/tokens/{token}:acknowledge", 
                     packageName, purchaseToken)
                .retrieve()
                .body(String::class.java)
            
            logger.info("Google Play subscription acknowledged successfully")
            
            PlatformAcknowledgmentResult(
                success = true,
                paymentId = purchaseToken
            )
            
        } catch (e: Exception) {
            logger.error("Google Play subscription acknowledgment failed", e)
            PlatformAcknowledgmentResult(
                success = false,
                paymentId = null,
                errorMessage = "Google Play 지급 완료 실패: ${e.message}"
            )
        }
    }
    
    override fun getSettlementData(startDate: LocalDate, endDate: LocalDate): PlatformSettlementData {
        return try {
            logger.info("Fetching Google Play settlement data: $startDate to $endDate")
            
            // 참고: 실제 Google Play 정산 데이터는 Play Console의 Financial reports에서 제공되며
            // 현재 공개 API로는 직접 접근이 제한적입니다.
            // https://support.google.com/googleplay/android-developer/answer/6135870
            
            // 실제 구현에서는 Google Play Console API 또는 별도의 정산 파일을 통해 데이터를 가져와야 합니다.
            // 여기서는 예시로 빈 데이터를 반환합니다.
            
            PlatformSettlementData(
                payments = emptyList(),
                totalRevenue = "0.00",
                platformFee = "0.00",
                currency = "USD",
                errorMessage = "Google Play settlement data requires manual integration with Play Console reports"
            )
            
        } catch (e: Exception) {
            logger.error("Failed to fetch Google Play settlement data", e)
            PlatformSettlementData(
                payments = emptyList(),
                totalRevenue = "0.00",
                platformFee = "0.00",
                currency = "USD",
                errorMessage = "Google Play 정산 데이터 조회 실패: ${e.message}"
            )
        }
    }
    
    override fun processRefund(
        productId: String,
        purchaseToken: String,
        refundAmount: String?,
        reason: String,
        packageName: String?,
        bundleId: String?
    ): com.example.integration.application.port.out.PlatformRefundResult {
        
        if (packageName == null) {
            return com.example.integration.application.port.out.PlatformRefundResult(
                success = false,
                platformRefundId = null,
                refundAmount = null,
                currency = null,
                errorMessage = "Package name is required for Google Play refund"
            )
        }
        
        return try {
            logger.info("Processing Google Play refund: productId=$productId, packageName=$packageName, amount=$refundAmount")
            
            // Google Play Developer API 호출 - orders.refund
            // https://developers.google.com/android-publisher/api-ref/rest/v3/orders/refund
            restClient.post()
                .uri("/androidpublisher/v3/applications/{packageName}/orders/{orderId}:refund?revoke=true", 
                     packageName, purchaseToken)
                .retrieve()
                .body(String::class.java)
            
            logger.info("Google Play refund processed successfully")
            
            com.example.integration.application.port.out.PlatformRefundResult(
                success = true,
                platformRefundId = purchaseToken,
                refundAmount = refundAmount,
                currency = "USD",
                estimatedProcessingTime = "1-3 business days"
            )
            
        } catch (e: Exception) {
            logger.error("Google Play refund processing failed", e)
            com.example.integration.application.port.out.PlatformRefundResult(
                success = false,
                platformRefundId = null,
                refundAmount = null,
                currency = null,
                errorMessage = "Google Play 환불 처리 실패: ${e.message}"
            )
        }
    }
    
    override fun getSupportedPlatform(): Platform = Platform.GOOGLE_PLAY
    
    /**
     * Google Play 구독 데이터를 구독 상품으로 변환 (새로운 도메인 모델)
     */
    private fun mapToSubscription(
        @Suppress("UNUSED_PARAMETER") subscriptionData: JsonNode,
        productId: String,
        @Suppress("UNUSED_PARAMETER") purchaseToken: String
    ): Subscription {
        // productId를 기반으로 구독 상품 정보를 반환
        // 실제로는 DB에서 productId로 조회해야 하지만, 여기서는 예시 데이터 반환
        return when (productId) {
            "premium_monthly" -> Subscription(
                id = 1,
                name = "프리미엄 월간 구독",
                pricePerMonth = 9990,
                description = "모든 프리미엄 기능 이용 가능",
                rentalPeriod = 30,
                subscriptionPeriod = 30,
                seriesRentalCountPerDay = 10,
                questCount = 100,
                reward = 1000,
                displayable = true
            )
            "premium_yearly" -> Subscription(
                id = 2,
                name = "프리미엄 연간 구독",
                pricePerMonth = 8330,
                description = "연간 구독으로 더 저렴하게",
                rentalPeriod = 365,
                subscriptionPeriod = 365,
                seriesRentalCountPerDay = 15,
                questCount = 1200,
                reward = 12000,
                displayable = true
            )
            else -> Subscription(
                id = 0,
                name = "기본 구독",
                pricePerMonth = 4990,
                description = "기본 기능 이용 가능",
                rentalPeriod = 30,
                subscriptionPeriod = 30,
                seriesRentalCountPerDay = 5,
                questCount = 50,
                reward = 500,
                displayable = true
            )
        }
    }
    
    /**
     * Google Play 상태를 도메인 상태로 매핑
     */
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
    
    /**
     * ISO 8601 타임스탬프를 LocalDateTime으로 변환
     */
    private fun parseIsoTimestamp(timestamp: String): LocalDateTime {
        return try {
            LocalDateTime.parse(timestamp.replace("Z", ""))
        } catch (e: Exception) {
            logger.warn("Failed to parse timestamp: $timestamp", e)
            LocalDateTime.now()
        }
    }
}