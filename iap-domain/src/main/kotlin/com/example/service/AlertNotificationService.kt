package com.example.service

import com.example.domain.Platform
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 알림 발송 통합 서비스
 * 
 * 다양한 채널을 통한 알림 발송을 담당합니다.
 * - 이메일, 슬랙, SMS, 웹훅 등 다중 채널 지원
 * - 알림 템플릿 관리
 * - 발송 이력 추적
 * - 실패 시 재시도 로직
 */
@Service
class AlertNotificationService {
    
    // 알림 설정 저장소 (실제로는 데이터베이스 사용)
    private val alertConfigurations = ConcurrentHashMap<String, AlertConfiguration>()
    private val notificationHistory = ConcurrentHashMap<String, List<NotificationLog>>()
    
    init {
        // 기본 알림 설정 초기화
        initializeDefaultAlertConfigurations()
    }
    
    /**
     * 통합 알림 발송
     */
    fun sendNotification(request: NotificationRequest): NotificationResult {
        val notificationId = UUID.randomUUID().toString()
        val results = mutableMapOf<AlertChannel, ChannelResult>()
        
        request.channels.forEach { channel ->
            val channelResult = try {
                when (channel) {
                    AlertChannel.EMAIL -> sendEmailNotification(request)
                    AlertChannel.SLACK -> sendSlackNotification(request)
                    AlertChannel.SMS -> sendSmsNotification(request)
                    AlertChannel.WEBHOOK -> sendWebhookNotification(request)
                    AlertChannel.PUSH_NOTIFICATION -> sendPushNotification(request)
                }
            } catch (e: Exception) {
                ChannelResult(
                    channel = channel,
                    success = false,
                    message = "발송 실패: ${e.message}",
                    sentAt = LocalDateTime.now()
                )
            }
            
            results[channel] = channelResult
        }
        
        // 발송 이력 저장
        val notificationLog = NotificationLog(
            id = notificationId,
            request = request,
            results = results,
            overallSuccess = results.values.all { it.success },
            processedAt = LocalDateTime.now()
        )
        
        storeNotificationLog(notificationLog)
        
        return NotificationResult(
            notificationId = notificationId,
            channelResults = results,
            overallSuccess = results.values.all { it.success },
            processedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 이메일 알림 발송
     */
    private fun sendEmailNotification(request: NotificationRequest): ChannelResult {
        //TODO: 실제 이메일 발송 구현 (JavaMailSender, SendGrid, AWS SES 등)
        println("📧 이메일 알림 발송")
        println("  수신자: ${request.recipients.joinToString(", ")}")
        println("  제목: ${request.title}")
        println("  내용: ${request.message}")
        
        // Mock 성공 응답
        return ChannelResult(
            channel = AlertChannel.EMAIL,
            success = true,
            message = "이메일 발송 완료",
            sentAt = LocalDateTime.now(),
            metadata = mapOf(
                "provider" to "mock-email-service",
                "recipients" to request.recipients.size.toString()
            )
        )
    }
    
    /**
     * 슬랙 알림 발송
     */
    private fun sendSlackNotification(request: NotificationRequest): ChannelResult {
        //TODO: 슬랙 웹훅 API 호출 구현
        println("💬 슬랙 알림 발송")
        println("  채널: #finance-alerts")
        println("  제목: ${request.title}")
        println("  내용: ${request.message}")
        
        val slackPayload = createSlackPayload(request)
        
        //TODO: HTTP POST 요청을 슬랙 웹훅 URL로 발송
        // val response = restTemplate.postForEntity(slackWebhookUrl, slackPayload, String::class.java)
        
        return ChannelResult(
            channel = AlertChannel.SLACK,
            success = true,
            message = "슬랙 메시지 발송 완료",
            sentAt = LocalDateTime.now(),
            metadata = mapOf(
                "channel" to "#finance-alerts",
                "webhook_url" to "https://hooks.slack.com/mock"
            )
        )
    }
    
    /**
     * SMS 알림 발송
     */
    private fun sendSmsNotification(request: NotificationRequest): ChannelResult {
        //TODO: SMS 발송 서비스 구현 (Twilio, AWS SNS 등)
        println("📱 SMS 알림 발송")
        
        // 긴급 알림인 경우에만 SMS 발송
        if (request.priority == NotificationPriority.CRITICAL) {
            request.recipients.forEach { recipient ->
                //TODO: 전화번호로 변환 후 SMS 발송
                println("  SMS 발송: $recipient")
                println("  내용: ${request.title} - ${request.message.take(100)}...")
            }
            
            return ChannelResult(
                channel = AlertChannel.SMS,
                success = true,
                message = "SMS 발송 완료",
                sentAt = LocalDateTime.now(),
                metadata = mapOf(
                    "provider" to "mock-sms-service",
                    "recipients" to request.recipients.size.toString()
                )
            )
        } else {
            return ChannelResult(
                channel = AlertChannel.SMS,
                success = false,
                message = "SMS는 긴급 알림만 발송됩니다",
                sentAt = LocalDateTime.now()
            )
        }
    }
    
    /**
     * 웹훅 알림 발송
     */
    private fun sendWebhookNotification(request: NotificationRequest): ChannelResult {
        //TODO: 외부 시스템 웹훅 호출 구현
        println("🔗 웹훅 알림 발송")
        
        val webhookPayload = createWebhookPayload(request)
        
        //TODO: HTTP POST 요청을 웹훅 URL로 발송
        // val response = restTemplate.postForEntity(webhookUrl, webhookPayload, String::class.java)
        
        return ChannelResult(
            channel = AlertChannel.WEBHOOK,
            success = true,
            message = "웹훅 호출 완료",
            sentAt = LocalDateTime.now(),
            metadata = mapOf(
                "webhook_url" to "https://api.external-system.com/webhooks/alerts",
                "payload_size" to webhookPayload.toString().length.toString()
            )
        )
    }
    
    /**
     * 푸시 알림 발송
     */
    private fun sendPushNotification(request: NotificationRequest): ChannelResult {
        //TODO: 모바일 푸시 알림 발송 구현 (FCM, APNS 등)
        println("📲 푸시 알림 발송")
        println("  제목: ${request.title}")
        println("  내용: ${request.message}")
        
        return ChannelResult(
            channel = AlertChannel.PUSH_NOTIFICATION,
            success = true,
            message = "푸시 알림 발송 완료",
            sentAt = LocalDateTime.now(),
            metadata = mapOf(
                "provider" to "mock-push-service",
                "platform" to "both"
            )
        )
    }
    
    /**
     * 슬랙 페이로드 생성
     */
    private fun createSlackPayload(request: NotificationRequest): Map<String, Any> {
        val color = when (request.priority) {
            NotificationPriority.CRITICAL -> "danger"
            NotificationPriority.HIGH -> "warning"
            NotificationPriority.MEDIUM -> "good"
            NotificationPriority.LOW -> "#439FE0"
        }
        
        return mapOf(
            "text" to request.title,
            "attachments" to listOf(
                mapOf(
                    "color" to color,
                    "fields" to listOf(
                        mapOf(
                            "title" to "플랫폼",
                            "value" to request.metadata["platform"],
                            "short" to true
                        ),
                        mapOf(
                            "title" to "우선순위",
                            "value" to request.priority.description,
                            "short" to true
                        ),
                        mapOf(
                            "title" to "상세 내용",
                            "value" to request.message,
                            "short" to false
                        )
                    ),
                    "ts" to System.currentTimeMillis() / 1000
                )
            )
        )
    }
    
    /**
     * 웹훅 페이로드 생성
     */
    private fun createWebhookPayload(request: NotificationRequest): Map<String, Any> {
        return mapOf(
            "event_type" to "settlement_alert",
            "timestamp" to System.currentTimeMillis(),
            "alert" to mapOf(
                "id" to UUID.randomUUID().toString(),
                "title" to request.title,
                "message" to request.message,
                "priority" to request.priority.name,
                "platform" to request.metadata["platform"],
                "metadata" to request.metadata
            )
        )
    }
    
    /**
     * 알림 설정 초기화
     */
    private fun initializeDefaultAlertConfigurations() {
        // 플랫폼별 기본 설정
        Platform.values().forEach { platform ->
            alertConfigurations["${platform.name}_default"] = AlertConfiguration(
                platform = platform,
                enabledChannels = listOf(AlertChannel.EMAIL, AlertChannel.SLACK),
                recipients = mapOf(
                    AlertChannel.EMAIL to listOf("finance@company.com"),
                    AlertChannel.SLACK to listOf("#finance-alerts")
                ),
                thresholds = mapOf(
                    "matching_rate" to 0.85,
                    "unresolved_count" to 10.0,
                    "amount_threshold" to 1000.0
                ),
                cooldownMinutes = 30
            )
        }
    }
    
    /**
     * 알림 이력 저장
     */
    private fun storeNotificationLog(log: NotificationLog) {
        val key = log.request.metadata["platform"] ?: "general"
        val currentHistory = notificationHistory[key] ?: emptyList()
        notificationHistory[key] = (currentHistory + log).takeLast(100) // 최근 100개만 보관
    }
    
    /**
     * 알림 이력 조회
     */
    fun getNotificationHistory(platform: Platform?, days: Int = 7): List<NotificationLog> {
        val cutoffTime = LocalDateTime.now().minusDays(days.toLong())
        
        return if (platform != null) {
            notificationHistory[platform.name]?.filter { it.processedAt.isAfter(cutoffTime) } ?: emptyList()
        } else {
            notificationHistory.values.flatten().filter { it.processedAt.isAfter(cutoffTime) }
        }.sortedByDescending { it.processedAt }
    }
    
    /**
     * 알림 설정 업데이트
     */
    fun updateAlertConfiguration(platform: Platform, configuration: AlertConfiguration) {
        alertConfigurations["${platform.name}_default"] = configuration
        println("알림 설정 업데이트 완료: ${platform.name}")
    }
    
    /**
     * 알림 설정 조회
     */
    fun getAlertConfiguration(platform: Platform): AlertConfiguration? {
        return alertConfigurations["${platform.name}_default"]
    }
    
    /**
     * 알림 테스트 발송
     */
    fun sendTestNotification(platform: Platform, channels: List<AlertChannel>): NotificationResult {
        val testRequest = NotificationRequest(
            title = "🧪 테스트 알림 - ${platform.name}",
            message = "이것은 알림 시스템 테스트입니다. 시간: ${LocalDateTime.now()}",
            priority = NotificationPriority.LOW,
            channels = channels,
            recipients = listOf("test@company.com"),
            metadata = mapOf(
                "platform" to platform.name,
                "test" to "true"
            )
        )
        
        return sendNotification(testRequest)
    }
}

// 데이터 클래스들
data class NotificationRequest(
    val title: String,
    val message: String,
    val priority: NotificationPriority,
    val channels: List<AlertChannel>,
    val recipients: List<String>,
    val metadata: Map<String, String> = emptyMap()
)

data class NotificationResult(
    val notificationId: String,
    val channelResults: Map<AlertChannel, ChannelResult>,
    val overallSuccess: Boolean,
    val processedAt: LocalDateTime
)

data class ChannelResult(
    val channel: AlertChannel,
    val success: Boolean,
    val message: String,
    val sentAt: LocalDateTime,
    val metadata: Map<String, String> = emptyMap()
)

data class NotificationLog(
    val id: String,
    val request: NotificationRequest,
    val results: Map<AlertChannel, ChannelResult>,
    val overallSuccess: Boolean,
    val processedAt: LocalDateTime
)

data class AlertConfiguration(
    val platform: Platform,
    val enabledChannels: List<AlertChannel>,
    val recipients: Map<AlertChannel, List<String>>,
    val thresholds: Map<String, Double>,
    val cooldownMinutes: Int
)

enum class NotificationPriority(val description: String) {
    LOW("낮음"),
    MEDIUM("보통"),
    HIGH("높음"),
    CRITICAL("긴급")
}