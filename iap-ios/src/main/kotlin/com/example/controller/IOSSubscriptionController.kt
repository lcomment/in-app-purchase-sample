package com.example.controller

import com.example.dto.IOSSubscriptionVerificationRequest
import com.example.dto.IOSSubscriptionVerificationResponse
import com.example.service.AppStoreSubscriptionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ios/subscription")
class IOSSubscriptionController(
    private val subscriptionService: AppStoreSubscriptionService
) {

    @PostMapping("/verify")
    fun verifySubscription(
        @RequestBody request: IOSSubscriptionVerificationRequest
    ): ResponseEntity<IOSSubscriptionVerificationResponse> {
        val response = subscriptionService.verifySubscription(request)
        
        return if (response.isValid) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    @PostMapping("/purchase")
    fun processPurchase(
        @RequestBody request: IOSSubscriptionVerificationRequest
    ): ResponseEntity<Map<String, Any>> {
        val verificationResponse = subscriptionService.verifySubscription(request)
        
        if (!verificationResponse.isValid) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "success" to false,
                    "message" to verificationResponse.message
                )
            )
        }

        val subscription = subscriptionService.processSubscriptionPurchase(request, verificationResponse)
        
        return if (subscription != null) {
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Subscription processed and saved successfully",
                    "subscription" to mapOf(
                        "id" to subscription.id,
                        "status" to subscription.status.name,
                        "expiryDate" to subscription.expiryDate.toString(),
                        "autoRenewing" to subscription.autoRenewing,
                        "userId" to subscription.userId,
                        "transactionId" to subscription.purchaseToken,
                        "originalTransactionId" to subscription.orderId
                    )
                )
            )
        } else {
            ResponseEntity.badRequest().body(
                mapOf(
                    "success" to false,
                    "message" to "Failed to process subscription"
                )
            )
        }
    }

    @GetMapping("/user/{userId}")
    fun getUserSubscriptions(
        @PathVariable userId: String
    ): ResponseEntity<Map<String, Any>> {
        val subscriptions = subscriptionService.getUserSubscriptions(userId)
        val activeSubscriptions = subscriptionService.getUserActiveSubscriptions(userId)
        
        return ResponseEntity.ok(
            mapOf(
                "userId" to userId,
                "totalSubscriptions" to subscriptions.size,
                "activeSubscriptions" to activeSubscriptions.size,
                "subscriptions" to subscriptions.map { subscription ->
                    mapOf(
                        "id" to subscription.id,
                        "planId" to subscription.planId,
                        "status" to subscription.status.name,
                        "startDate" to subscription.startDate.toString(),
                        "expiryDate" to subscription.expiryDate.toString(),
                        "autoRenewing" to subscription.autoRenewing,
                        "transactionId" to subscription.purchaseToken,
                        "originalTransactionId" to subscription.orderId
                    )
                }
            )
        )
    }

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(
            mapOf(
                "status" to "UP",
                "service" to "iOS Subscription Service"
            )
        )
    }
}