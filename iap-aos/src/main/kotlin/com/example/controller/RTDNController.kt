package com.example.controller

import com.example.dto.GooglePlayRTDNRequest
import com.example.service.RTDNEventService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/aos")
class RTDNController(
    private val rtdnEventService: RTDNEventService
) {

    @PostMapping("/rtdn")
    fun handleRTDN(
        @RequestBody rtdnRequest: GooglePlayRTDNRequest
    ): ResponseEntity<Map<String, Any>> {
        
        return try {
            println("Received RTDN notification: ${rtdnRequest.message.messageId}")
            
            val processed = rtdnEventService.processRTDNNotification(rtdnRequest)
            
            if (processed) {
                ResponseEntity.ok(
                    mapOf(
                        "success" to true,
                        "message" to "RTDN notification processed successfully",
                        "messageId" to rtdnRequest.message.messageId
                    )
                )
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    mapOf(
                        "success" to false,
                        "message" to "Failed to process RTDN notification",
                        "messageId" to rtdnRequest.message.messageId
                    )
                )
            }
        } catch (e: Exception) {
            println("Error handling RTDN: ${e.message}")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "success" to false,
                    "message" to "Error processing RTDN: ${e.message}"
                )
            )
        }
    }

    @GetMapping("/rtdn/health")
    fun rtdnHealth(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(
            mapOf(
                "status" to "UP",
                "service" to "RTDN Webhook Service",
                "endpoint" to "/api/aos/rtdn"
            )
        )
    }

    // Google Play Console에서 webhook URL 검증을 위한 엔드포인트
    @GetMapping("/rtdn")
    fun verifyRTDNEndpoint(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(
            mapOf(
                "status" to "ready",
                "message" to "RTDN webhook endpoint is ready to receive notifications"
            )
        )
    }
}