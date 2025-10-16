package com.example.integration.infrastructure.web

import com.example.integration.application.port.`in`.WebhookProcessingUseCase
import com.example.integration.domain.Platform
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 웹훅 수신 REST API 컨트롤러 (Infrastructure Layer)
 */
@RestController
@RequestMapping("/api/v1/webhooks")
class WebhookController(
    private val webhookProcessingUseCase: WebhookProcessingUseCase
) {
    
    private val logger = LoggerFactory.getLogger(WebhookController::class.java)
    
    /**
     * Google Play Real-time Developer Notifications 수신
     * 
     * Google Play에서 구독/결제 상태 변경 시 실시간 알림을 수신합니다.
     * Cloud Pub/Sub를 통해 전송되는 알림을 처리합니다.
     * 
     * 설정 가이드:
     * - RTDN 설정: https://developer.android.com/google/play/billing/getting-ready#configure-rtdn
     * - 실시간 알림 추가: https://developer.android.com/google/play/billing/realtime_developer_notifications
     * - RTDN 레퍼런스: https://developer.android.com/google/play/billing/rtdn-reference
     * 
     * 지원하는 알림 타입:
     * - SUBSCRIPTION_RECOVERED (1): 구독 복원
     * - SUBSCRIPTION_RENEWED (2): 구독 갱신  
     * - SUBSCRIPTION_CANCELED (3): 구독 취소
     * - SUBSCRIPTION_PURCHASED (4): 구독 구매
     * - SUBSCRIPTION_ON_HOLD (5): 구독 보류
     * - SUBSCRIPTION_IN_GRACE_PERIOD (6): 구독 유예 기간
     * - SUBSCRIPTION_RESTARTED (7): 구독 재시작
     * - SUBSCRIPTION_PRICE_CHANGE_CONFIRMED (8): 가격 변경 확인
     * - SUBSCRIPTION_DEFERRED (9): 구독 연기
     * - SUBSCRIPTION_PAUSED (10): 구독 일시정지
     * - SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED (11): 일시정지 일정 변경
     * - SUBSCRIPTION_REVOKED (12): 구독 철회
     * - SUBSCRIPTION_EXPIRED (13): 구독 만료
     */
    @PostMapping("/google-play")
    fun handleGooglePlayWebhook(@RequestBody payload: String): ResponseEntity<WebhookResponse> {
        logger.info("Received Google Play webhook")
        
        val result = webhookProcessingUseCase.processWebhook(Platform.GOOGLE_PLAY, payload)
        
        return if (result.success) {
            ResponseEntity.ok(WebhookResponse(
                success = true,
                message = result.message ?: "Webhook processed successfully",
                notificationId = result.webhookNotification?.id
            ))
        } else {
            logger.error("Failed to process Google Play webhook: ${result.errorMessage}")
            ResponseEntity.badRequest().body(WebhookResponse(
                success = false,
                message = result.errorMessage ?: "Failed to process webhook"
            ))
        }
    }
    
    /**
     * App Store Server-to-Server Notifications 수신
     * 
     * App Store에서 구독/인앱구매 상태 변경 시 실시간 알림을 수신합니다.
     * HTTPS POST 요청으로 전송되는 알림을 처리합니다.
     * 
     * 설정 가이드:
     * - 서버 알림 활성화: https://developer.apple.com/documentation/appstoreservernotifications/enabling-app-store-server-notifications
     * - 서버 알림 수신: https://developer.apple.com/documentation/appstoreservernotifications/receiving-app-store-server-notifications
     * - V2 알림 사용: https://developer.apple.com/documentation/appstoreservernotifications/app-store-server-notifications-v2
     * - App Store Connect 설정: https://developer.apple.com/help/app-store-connect/configure-in-app-purchase-settings/enter-server-urls-for-app-store-server-notifications
     * 
     * 지원하는 알림 타입:
     * - INITIAL_BUY: 최초 구매
     * - CANCEL: 취소
     * - RENEWAL: 갱신
     * - INTERACTIVE_RENEWAL: 대화형 갱신
     * - DID_CHANGE_RENEWAL_PREF: 갱신 설정 변경
     * - DID_CHANGE_RENEWAL_STATUS: 갱신 상태 변경
     * - DID_FAIL_TO_RENEW: 갱신 실패
     * - DID_RECOVER: 복원
     * - PRICE_INCREASE_CONSENT: 가격 인상 동의
     * - REFUND: 환불
     * - REVOKE: 철회
     */
    @PostMapping("/app-store")
    fun handleAppStoreWebhook(@RequestBody payload: String): ResponseEntity<WebhookResponse> {
        logger.info("Received App Store webhook")
        
        val result = webhookProcessingUseCase.processWebhook(Platform.APP_STORE, payload)
        
        return if (result.success) {
            ResponseEntity.ok(WebhookResponse(
                success = true,
                message = result.message ?: "Webhook processed successfully",
                notificationId = result.webhookNotification?.id
            ))
        } else {
            logger.error("Failed to process App Store webhook: ${result.errorMessage}")
            ResponseEntity.badRequest().body(WebhookResponse(
                success = false,
                message = result.errorMessage ?: "Failed to process webhook"
            ))
        }
    }
    
    /**
     * 실패한 웹훅 재처리 (관리자용)
     * 
     * 처리에 실패한 웹훅 알림들을 재시도합니다.
     * 최대 재시도 횟수에 도달하지 않은 실패한 알림들을 대상으로 합니다.
     * 
     * 관리 목적:
     * - 일시적 오류로 실패한 웹훅 재처리
     * - 시스템 장애 복구 후 누적된 실패 웹훅 처리
     * - 수동 재처리를 통한 데이터 일관성 보장
     * 
     * 응답 정보:
     * - processedCount: 처리 시도한 총 웹훅 수
     * - successCount: 성공적으로 처리된 웹훅 수
     * - failedCount: 여전히 실패한 웹훅 수
     * - skippedCount: 최대 재시도 횟수 초과로 건너뛴 웹훅 수
     */
    @PostMapping("/retry-failed")
    fun retryFailedWebhooks(): ResponseEntity<WebhookRetryResponse> {
        logger.info("Starting retry process for failed webhooks")
        
        val result = webhookProcessingUseCase.retryFailedWebhooks()
        
        return ResponseEntity.ok(WebhookRetryResponse(
            processedCount = result.processedCount,
            successCount = result.successCount,
            failedCount = result.failedCount,
            skippedCount = result.skippedCount,
            message = "Webhook retry process completed"
        ))
    }
}

/**
 * 웹훅 처리 응답 DTO
 */
data class WebhookResponse(
    val success: Boolean,
    val message: String,
    val notificationId: Long? = null
)

/**
 * 웹훅 재처리 응답 DTO
 */
data class WebhookRetryResponse(
    val processedCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val message: String
)