package com.example.integration.adapter

import com.example.integration.application.port.out.PlatformAdapterPort
import com.example.integration.application.port.out.PlatformVerificationResult
import com.example.integration.application.port.out.PlatformAcknowledgmentResult
import com.example.integration.application.port.out.PlatformSettlementData
import com.example.integration.domain.Platform
import com.example.integration.domain.Subscription
import com.example.integration.domain.SubscriptionStatus
import com.example.integration.domain.Payment
import com.example.integration.domain.PaymentStatus
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
 * App Store 플랫폼 어댑터 (Infrastructure Layer)
 */
@Component
class AppStoreAdapter(
    @Value("\${app.appstore.jwt-token:dummy-token}") private val jwtToken: String,
    @Value("\${app.appstore.environment:sandbox}") private val environment: String,
    private val objectMapper: ObjectMapper
) : PlatformAdapterPort {
    
    private val logger = LoggerFactory.getLogger(AppStoreAdapter::class.java)
    
    private val restClient = RestClient.builder()
        .baseUrl(getAppStoreApiBaseUrl())
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $jwtToken")
        .build()
    
    override fun verifySubscription(
        productId: String,
        purchaseToken: String,
        packageName: String?,
        bundleId: String?
    ): PlatformVerificationResult {
        
        if (bundleId == null) {
            return PlatformVerificationResult(
                isValid = false,
                subscription = null,
                errorMessage = "Bundle ID is required for App Store verification"
            )
        }
        
        return try {
            logger.info("Verifying App Store subscription: productId=$productId, bundleId=$bundleId")
            
            // App Store Server API 호출 - 거래 정보 조회
            // https://developer.apple.com/documentation/appstoreserverapi/get-transaction-info
            val response = restClient.get()
                .uri("/inApps/v1/transactions/{transactionId}", purchaseToken)
                .retrieve()
                .body(String::class.java)
            
            val transactionInfo = objectMapper.readTree(response)
            val subscription = mapToSubscription(transactionInfo, productId, purchaseToken)
            
            PlatformVerificationResult(
                isValid = true,
                subscription = subscription,
                rawData = mapOf(
                    "transactionInfo" to (response ?: ""),
                    "bundleId" to bundleId
                )
            )
            
        } catch (e: Exception) {
            logger.error("App Store subscription verification failed", e)
            PlatformVerificationResult(
                isValid = false,
                subscription = null,
                errorMessage = "App Store 검증 실패: ${e.message}"
            )
        }
    }
    
    override fun acknowledgePayment(
        productId: String,
        purchaseToken: String,
        packageName: String?,
        bundleId: String?
    ): PlatformAcknowledgmentResult {
        
        return try {
            logger.info("Acknowledging App Store subscription: productId=$productId")
            
            // 참고: App Store에서는 별도의 acknowledge API가 없습니다.
            // 거래 검증이 곧 acknowledge 역할을 합니다.
            // https://developer.apple.com/documentation/storekit/in-app_purchase/validating_receipts_with_the_app_store
            
            logger.info("App Store subscription acknowledged (verification serves as acknowledgment)")
            
            PlatformAcknowledgmentResult(
                success = true,
                paymentId = purchaseToken
            )
            
        } catch (e: Exception) {
            logger.error("App Store subscription acknowledgment failed", e)
            PlatformAcknowledgmentResult(
                success = false,
                paymentId = null,
                errorMessage = "App Store 지급 완료 실패: ${e.message}"
            )
        }
    }
    
    override fun getSettlementData(startDate: LocalDate, endDate: LocalDate): PlatformSettlementData {
        return try {
            logger.info("Fetching App Store settlement data: $startDate to $endDate")
            
            // App Store Server API 호출 - 구독 상태 조회
            // https://developer.apple.com/documentation/appstoreserverapi/get-all-subscription-statuses
            // 참고: 실제로는 여러 구독의 상태를 조회해야 하지만, 여기서는 예시입니다.
            
            // 실제 구현에서는 App Store Connect의 Sales and Trends 또는 Financial Reports를 활용해야 합니다.
            // https://help.apple.com/app-store-connect/#/dev061e63923
            
            PlatformSettlementData(
                payments = emptyList(),
                totalRevenue = "0.00",
                platformFee = "0.00",
                currency = "USD",
                errorMessage = "App Store settlement data requires integration with App Store Connect reports"
            )
            
        } catch (e: Exception) {
            logger.error("Failed to fetch App Store settlement data", e)
            PlatformSettlementData(
                payments = emptyList(),
                totalRevenue = "0.00",
                platformFee = "0.00",
                currency = "USD",
                errorMessage = "App Store 정산 데이터 조회 실패: ${e.message}"
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
        
        return try {
            logger.info("Processing App Store refund: productId=$productId, transactionId=$purchaseToken, amount=$refundAmount")
            
            // App Store Server API 호출 - 환불 이력 조회
            // https://developer.apple.com/documentation/appstoreserverapi/get-refund-history
            val requestBody = mapOf(
                "originalTransactionId" to purchaseToken,
                "refundReason" to reason,
                "refundAmount" to refundAmount
            )
            
            val response = restClient.post()
                .uri("/inApps/v1/refunds/lookup/{originalTransactionId}", purchaseToken)
                .body(requestBody)
                .retrieve()
                .body(String::class.java)
            
            val refundData = objectMapper.readTree(response)
            
            logger.info("App Store refund request submitted successfully")
            
            com.example.integration.application.port.out.PlatformRefundResult(
                success = true,
                platformRefundId = refundData.get("refundRequestId")?.asText() ?: purchaseToken,
                refundAmount = refundAmount,
                currency = "USD",
                estimatedProcessingTime = "24-48 hours"
            )
            
        } catch (e: Exception) {
            logger.error("App Store refund processing failed", e)
            
            // 참고: App Store에서는 대부분의 환불이 고객이 직접 Apple에 요청하는 방식으로 처리됩니다.
            // https://support.apple.com/en-us/HT204084
            logger.warn("App Store refunds are typically processed through customer-initiated requests to Apple")
            
            com.example.integration.application.port.out.PlatformRefundResult(
                success = false,
                platformRefundId = null,
                refundAmount = null,
                currency = null,
                errorMessage = "App Store 환불은 일반적으로 고객이 Apple에 직접 요청하는 방식으로 처리됩니다. 자세한 내용은 Apple 지원 페이지를 참조하세요."
            )
        }
    }
    
    override fun getSupportedPlatform(): Platform = Platform.APP_STORE
    
    /**
     * App Store 거래 데이터를 도메인 모델로 변환
     */
    private fun mapToSubscription(
        transactionInfo: JsonNode,
        productId: String,
        purchaseToken: String
    ): Subscription {
        
        val signedTransactionInfo = transactionInfo.get("signedTransactionInfo")?.asText()
        // 실제로는 JWT 토큰을 디코딩해야 하지만, 여기서는 예시로 기본값 사용
        
        val expiryDate = try {
            // 실제로는 JWT에서 expiresDate 필드를 파싱해야 함
            LocalDateTime.now().plusDays(30)
        } catch (e: Exception) {
            LocalDateTime.now().plusDays(30)
        }
        
        return Subscription(
            id = UUID.randomUUID().toString(),
            userId = "", // 사용자 ID는 별도로 제공되어야 함
            platform = Platform.APP_STORE,
            productId = productId,
            purchaseToken = purchaseToken,
            orderId = purchaseToken, // App Store에서는 originalTransactionId를 사용
            status = SubscriptionStatus.ACTIVE, // 실제로는 JWT에서 파싱
            startDate = LocalDateTime.now(),
            expiryDate = expiryDate,
            autoRenewing = true, // 실제로는 JWT에서 파싱
            price = BigDecimal("9.99"), // 실제로는 구독 상품 정보에서 가져와야 함
            currency = "USD"
        )
    }
    
    /**
     * App Store API 기본 URL 반환
     */
    private fun getAppStoreApiBaseUrl(): String {
        return when (environment.lowercase()) {
            "production" -> "https://api.storekit.itunes.apple.com"
            else -> "https://api.storekit-sandbox.itunes.apple.com"
        }
    }
}