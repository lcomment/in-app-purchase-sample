package com.example.dto

data class SubscriptionVerificationRequest(
    val packageName: String,
    val subscriptionId: String,
    val purchaseToken: String,
    val userId: String
)