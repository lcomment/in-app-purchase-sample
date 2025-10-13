package com.example.service

import com.example.config.AppStoreConfig
import com.example.domain.Platform
import com.example.domain.refund.*
import com.example.repository.IOSSubscriptionRepository
import com.example.repository.IOSPaymentRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * App Store 환불 서비스
 * 
 * App Store Server API를 사용하여 환불을 처리합니다.
 * - 구독 환불
 * - 인앱결제 환불
 * - 환불 상태 추적
 * 
 * 참고: App Store는 자동 환불보다는 고객이 직접 Apple에 요청하는 경우가 많음
 */
@Service
class AppStoreRefundService(
    private val appStoreConfig: AppStoreConfig,
    private val objectMapper: ObjectMapper,
    private val subscriptionRepository: IOSSubscriptionRepository,
    private val paymentRepository: IOSPaymentRepository
) {
    
    private val logger = LoggerFactory.getLogger(AppStoreRefundService::class.java)
    
    companion object {
        // App Store: 일반적으로 90일 (Apple 정책에 따라)
        private const val APP_STORE_REFUND_PERIOD_DAYS = 90L
        
        // 자동 승인 임계값
        private val AUTO_APPROVAL_AMOUNT_THRESHOLD = BigDecimal("50.00")
    }
    
    private val restClient = RestClient.builder()
        .baseUrl("https://api.storekit-sandbox.itunes.apple.com") // 실제로는 config에서 가져와야 함
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${appStoreConfig.appStoreJwtToken()}")
        .build()
    
    /**
     * 환불 요청 처리
     */
    fun processRefundRequest(request: AppStoreRefundRequest): AppStoreRefundResult {
        logger.info("Processing App Store refund request: ${request.id}")
        
        try {
            // 1. 요청 검증
            val validationResult = validateRefundRequest(request)
            if (!validationResult.isValid) {
                return AppStoreRefundResult(
                    success = false,
                    refundRequestId = null,
                    error = validationResult.errors.first(),
                    timestamp = LocalDateTime.now()
                )
            }
            
            // 2. App Store Server API 호출
            val refundResult = executeAppStoreRefund(request)
            
            // 3. 결과 반환
            return AppStoreRefundResult(
                success = refundResult.success,
                refundRequestId = refundResult.refundRequestId,
                refundAmount = request.amount,
                currency = request.currency,
                error = refundResult.error,
                timestamp = LocalDateTime.now(),
                status = refundResult.status,
                estimatedProcessingTime = "24-48 hours"
            )
            
        } catch (e: Exception) {
            logger.error("App Store refund failed: ${request.id}", e)
            return AppStoreRefundResult(
                success = false,
                refundRequestId = null,
                error = "환불 처리 중 오류 발생: ${e.message}",
                timestamp = LocalDateTime.now()
            )
        }
    }
    
    /**
     * 환불 상태 조회
     */
    fun getRefundStatus(refundRequestId: String): AppStoreRefundStatus {
        return try {
            logger.info("Checking App Store refund status: $refundRequestId")
            
            // App Store Server API를 통한 상태 조회
            val response = restClient.get()
                .uri("/v1/refundLookup/$refundRequestId")
                .retrieve()
                .body(String::class.java)
            
            val jsonNode = objectMapper.readTree(response)
            
            AppStoreRefundStatus(
                refundRequestId = refundRequestId,
                status = jsonNode.get("status")?.asText() ?: "UNKNOWN",
                refundDate = jsonNode.get("refundDate")?.asText()?.let { 
                    LocalDateTime.parse(it) 
                },
                amount = jsonNode.get("refundedAmount")?.let { 
                    BigDecimal(it.asDouble()) 
                },
                currency = jsonNode.get("currency")?.asText()
            )
            
        } catch (e: Exception) {
            logger.error("Failed to get App Store refund status: $refundRequestId", e)
            AppStoreRefundStatus(
                refundRequestId = refundRequestId,
                status = "UNKNOWN",
                refundDate = null,
                amount = null,
                currency = null,
                error = e.message
            )
        }
    }
    
    /**
     * 구독 환불 요청
     */
    fun requestSubscriptionRefund(
        originalTransactionId: String,
        reason: AppStoreRefundReason,
        amount: BigDecimal?,
        currency: String = "USD"
    ): AppStoreRefundResult {
        return try {
            logger.info("Requesting subscription refund: $originalTransactionId")
            
            val requestBody = mapOf(
                "originalTransactionId" to originalTransactionId,
                "refundReason" to reason.code,
                "refundAmount" to amount?.toString(),
                "currency" to currency
            )
            
            val response = restClient.post()
                .uri("/v1/refundLookup")
                .body(requestBody)
                .retrieve()
                .body(String::class.java)
            
            val jsonNode = objectMapper.readTree(response)
            
            AppStoreRefundResult(
                success = jsonNode.get("success")?.asBoolean() ?: false,
                refundRequestId = jsonNode.get("refundRequestId")?.asText(),
                refundAmount = amount,
                currency = currency,
                timestamp = LocalDateTime.now(),
                status = "SUBMITTED"
            )
            
        } catch (e: Exception) {
            logger.error("Subscription refund request failed: $originalTransactionId", e)
            AppStoreRefundResult(
                success = false,
                refundRequestId = null,
                error = "구독 환불 요청 실패: ${e.message}",
                timestamp = LocalDateTime.now()
            )
        }
    }
    
    /**
     * 인앱결제 환불 요청
     */
    fun requestInAppPurchaseRefund(
        transactionId: String,
        reason: AppStoreRefundReason,
        amount: BigDecimal?,
        currency: String = "USD"
    ): AppStoreRefundResult {
        return try {
            logger.info("Requesting in-app purchase refund: $transactionId")
            
            val requestBody = mapOf(
                "transactionId" to transactionId,
                "refundReason" to reason.code,
                "refundAmount" to amount?.toString(),
                "currency" to currency
            )
            
            val response = restClient.post()
                .uri("/v1/refundLookup")
                .body(requestBody)
                .retrieve()
                .body(String::class.java)
            
            val jsonNode = objectMapper.readTree(response)
            
            AppStoreRefundResult(
                success = jsonNode.get("success")?.asBoolean() ?: false,
                refundRequestId = jsonNode.get("refundRequestId")?.asText(),
                refundAmount = amount,
                currency = currency,
                timestamp = LocalDateTime.now(),
                status = "SUBMITTED"
            )
            
        } catch (e: Exception) {
            logger.error("In-app purchase refund request failed: $transactionId", e)
            AppStoreRefundResult(
                success = false,
                refundRequestId = null,
                error = "인앱결제 환불 요청 실패: ${e.message}",
                timestamp = LocalDateTime.now()
            )
        }
    }
    
    /**
     * 환불 요청 검증
     */
    private fun validateRefundRequest(request: AppStoreRefundRequest): AppStoreRefundValidation {
        val errors = mutableListOf<String>()
        
        // 기본 필드 검증
        if (request.originalTransactionId.isBlank()) {
            errors.add("거래 ID가 필요합니다")
        }
        
        if (request.amount != null && request.amount <= BigDecimal.ZERO) {
            errors.add("환불 금액은 0보다 커야 합니다")
        }
        
        // 구독 존재 여부 확인 (선택적)
        if (request.type == AppStoreRefundType.SUBSCRIPTION) {
            val subscription = subscriptionRepository.findByPurchaseToken(request.originalTransactionId)
            if (subscription == null) {
                errors.add("해당 구독을 찾을 수 없습니다")
            }
        }
        
        return AppStoreRefundValidation(
            isValid = errors.isEmpty(),
            errors = errors,
            canAutoApprove = errors.isEmpty() && 
                (request.amount ?: BigDecimal.ZERO) <= AUTO_APPROVAL_AMOUNT_THRESHOLD
        )
    }
    
    /**
     * App Store 환불 실행
     */
    private fun executeAppStoreRefund(request: AppStoreRefundRequest): AppStoreRefundResult {
        return when (request.type) {
            AppStoreRefundType.SUBSCRIPTION -> requestSubscriptionRefund(
                request.originalTransactionId,
                request.reason,
                request.amount,
                request.currency
            )
            AppStoreRefundType.IN_APP_PURCHASE -> requestInAppPurchaseRefund(
                request.originalTransactionId,
                request.reason,
                request.amount,
                request.currency
            )
        }
    }
}

/**
 * App Store 환불 요청
 */
data class AppStoreRefundRequest(
    val id: String = UUID.randomUUID().toString(),
    val originalTransactionId: String,
    val type: AppStoreRefundType,
    val reason: AppStoreRefundReason,
    val amount: BigDecimal?,
    val currency: String = "USD",
    val customerNote: String?
)

/**
 * App Store 환불 타입
 */
enum class AppStoreRefundType {
    SUBSCRIPTION,
    IN_APP_PURCHASE
}

/**
 * App Store 환불 사유
 */
enum class AppStoreRefundReason(val code: String, val description: String) {
    CUSTOMER_REQUEST("0", "고객 요청"),
    TECHNICAL_ISSUE("1", "기술적 문제"),
    BILLING_ERROR("2", "결제 오류"),
    FRAUD_PREVENTION("3", "사기 방지"),
    OTHER("99", "기타")
}

/**
 * App Store 환불 결과
 */
data class AppStoreRefundResult(
    val success: Boolean,
    val refundRequestId: String?,
    val refundAmount: BigDecimal? = null,
    val currency: String? = null,
    val error: String? = null,
    val timestamp: LocalDateTime,
    val status: String? = null,
    val estimatedProcessingTime: String? = null
)

/**
 * App Store 환불 상태
 */
data class AppStoreRefundStatus(
    val refundRequestId: String,
    val status: String,
    val refundDate: LocalDateTime?,
    val amount: BigDecimal?,
    val currency: String?,
    val error: String? = null
)

/**
 * App Store 환불 검증 결과
 */
data class AppStoreRefundValidation(
    val isValid: Boolean,
    val errors: List<String>,
    val canAutoApprove: Boolean
)