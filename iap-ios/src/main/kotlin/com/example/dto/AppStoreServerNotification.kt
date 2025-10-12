package com.example.dto

import com.fasterxml.jackson.annotation.JsonProperty

// App Store Server Notifications v2 최상위 요청 구조
data class AppStoreServerNotificationRequest(
    @JsonProperty("signedPayload")
    val signedPayload: String
)

// signedPayload 디코딩 후 얻는 responseBodyV2DecodedPayload 구조
data class AppStoreServerNotification(
    @JsonProperty("notificationType")
    val notificationType: String,
    
    @JsonProperty("subtype")
    val subtype: String? = null,
    
    @JsonProperty("notificationUUID")
    val notificationUUID: String,
    
    @JsonProperty("data")
    val data: NotificationData,
    
    @JsonProperty("version")
    val version: String,
    
    @JsonProperty("signedDate")
    val signedDate: Long
)

data class NotificationData(
    @JsonProperty("appAppleId")
    val appAppleId: Long? = null,
    
    @JsonProperty("bundleId")
    val bundleId: String,
    
    @JsonProperty("bundleVersion")
    val bundleVersion: String? = null,
    
    @JsonProperty("environment")
    val environment: String,
    
    @JsonProperty("signedRenewalInfo")
    val signedRenewalInfo: String? = null,
    
    @JsonProperty("signedTransactionInfo")
    val signedTransactionInfo: String
)

data class TransactionInfo(
    @JsonProperty("originalTransactionId")
    val originalTransactionId: String,
    
    @JsonProperty("transactionId")
    val transactionId: String,
    
    @JsonProperty("webOrderLineItemId")
    val webOrderLineItemId: String? = null,
    
    @JsonProperty("bundleId")
    val bundleId: String,
    
    @JsonProperty("productId")
    val productId: String,
    
    @JsonProperty("subscriptionGroupIdentifier")
    val subscriptionGroupIdentifier: String? = null,
    
    @JsonProperty("originalPurchaseDate")
    val originalPurchaseDate: Long,
    
    @JsonProperty("purchaseDate")
    val purchaseDate: Long,
    
    @JsonProperty("revocationDate")
    val revocationDate: Long? = null,
    
    @JsonProperty("revocationReason")
    val revocationReason: Int? = null,
    
    @JsonProperty("isUpgraded")
    val isUpgraded: Boolean? = null,
    
    @JsonProperty("type")
    val type: String,
    
    @JsonProperty("inAppOwnershipType")
    val inAppOwnershipType: String,
    
    @JsonProperty("signedDate")
    val signedDate: Long,
    
    @JsonProperty("expiresDate")
    val expiresDate: Long? = null,
    
    @JsonProperty("offerType")
    val offerType: Int? = null,
    
    @JsonProperty("offerIdentifier")
    val offerIdentifier: String? = null
)

data class RenewalInfo(
    @JsonProperty("originalTransactionId")
    val originalTransactionId: String,
    
    @JsonProperty("autoRenewProductId")
    val autoRenewProductId: String,
    
    @JsonProperty("productId")
    val productId: String,
    
    @JsonProperty("autoRenewStatus")
    val autoRenewStatus: Int,
    
    @JsonProperty("isInBillingRetryPeriod")
    val isInBillingRetryPeriod: Boolean? = null,
    
    @JsonProperty("priceIncreaseStatus")
    val priceIncreaseStatus: Int? = null,
    
    @JsonProperty("gracePeriodExpiresDate")
    val gracePeriodExpiresDate: Long? = null,
    
    @JsonProperty("offerType")
    val offerType: Int? = null,
    
    @JsonProperty("offerIdentifier")
    val offerIdentifier: String? = null,
    
    @JsonProperty("signedDate")
    val signedDate: Long,
    
    @JsonProperty("environment")
    val environment: String,
    
    @JsonProperty("recentSubscriptionStartDate")
    val recentSubscriptionStartDate: Long? = null,
    
    @JsonProperty("renewalDate")
    val renewalDate: Long? = null
)