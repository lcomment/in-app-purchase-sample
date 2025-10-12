package com.example.controller

import com.example.dto.AppStoreServerNotificationRequest
import com.example.service.AppStoreNotificationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ios")
class AppStoreNotificationController(
    private val appStoreNotificationService: AppStoreNotificationService
) {

    @PostMapping("/webhook")
    fun handleAppStoreNotification(
        @RequestBody notificationRequest: AppStoreServerNotificationRequest
    ): ResponseEntity<Map<String, Any>> {
        
        return try {
            println("Received App Store Server Notification")
            
            val processed = appStoreNotificationService.processServerNotification(notificationRequest)
            
            if (processed) {
                ResponseEntity.ok(
                    mapOf(
                        "success" to true,
                        "message" to "App Store notification processed successfully"
                    )
                )
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    mapOf(
                        "success" to false,
                        "message" to "Failed to process App Store notification"
                    )
                )
            }
        } catch (e: Exception) {
            println("Error handling App Store notification: ${e.message}")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "success" to false,
                    "message" to "Error processing App Store notification: ${e.message}"
                )
            )
        }
    }

    @GetMapping("/webhook/health")
    fun webhookHealth(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(
            mapOf(
                "status" to "UP",
                "service" to "App Store Server Notifications Service",
                "endpoint" to "/api/ios/webhook"
            )
        )
    }

    // App Store Connect에서 webhook URL 검증을 위한 엔드포인트
    @GetMapping("/webhook")
    fun verifyWebhookEndpoint(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(
            mapOf(
                "status" to "ready",
                "message" to "App Store webhook endpoint is ready to receive notifications"
            )
        )
    }
}