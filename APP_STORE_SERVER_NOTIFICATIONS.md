# App Store Server Notifications v2

## 개요

App Store Server Notifications v2는 Apple App Store에서 구독 상태 변경 시 실시간으로 알림을 받을 수 있는 메커니즘입니다. JWS(JSON Web Signature) 형태로 암호화된 다중 계층 보안 구조를 통해 구독 구매, 갱신, 취소, 환불 등의 이벤트를 안전하게 처리할 수 있습니다.

## 현재 구현한 기능

### 1. 엔드포인트
- **Server Notifications Webhook**: `POST /api/ios/webhook`
- **Health Check**: `GET /api/ios/webhook/health`
- **Endpoint 검증**: `GET /api/ios/webhook`

### 2. 지원하는 알림 타입

#### Subscription Notifications 처리
- `SUBSCRIBED`: 새로운 구독 시작
- `DID_RENEW`: 구독 갱신 성공
- `EXPIRED`: 구독 만료
- `DID_CHANGE_RENEWAL_STATUS`: 자동 갱신 설정 변경
- `DID_FAIL_TO_RENEW`: 갱신 실패
- `GRACE_PERIOD_EXPIRED`: 유예 기간 만료
- `REFUND`: 환불
- `REVOKE`: 구독 취소
- `TEST`: 테스트 알림

#### Notification Subtypes 지원
- `INITIAL_BUY`, `RESUBSCRIBE`: 구독 시작 유형
- `UPGRADE`, `DOWNGRADE`: 구독 변경
- `AUTO_RENEW_ENABLED`, `AUTO_RENEW_DISABLED`: 자동 갱신 상태
- `VOLUNTARY`: 자발적 취소
- `BILLING_RETRY`, `GRACE_PERIOD`: 결제 실패 유형

### 3. 핵심 구현 컴포넌트
- **AppStoreNotificationController**: HTTP 요청 처리
- **AppStoreNotificationService**: 알림 이벤트 처리 로직
- **IOSSubscriptionRepository**: 구독 데이터 저장/조회
- **IOSPaymentRepository**: 결제 이벤트 추적

## 메시지 구조

### 1. App Store Server Notifications v2 요청 구조
```json
{
  "signedPayload": "eyJhbGciOiJFUzI1NiIs..."  // JWS 형태의 암호화된 페이로드
}
```

### 2. SignedPayload 디코딩 후 구조 (responseBodyV2DecodedPayload)
```json
{
  "notificationType": "DID_RENEW",
  "subtype": "BILLING_RECOVERY",
  "notificationUUID": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "appAppleId": 123456789,
    "bundleId": "com.example.app",
    "bundleVersion": "1.0.0",
    "environment": "Sandbox",
    "signedTransactionInfo": "eyJhbGciOiJFUzI1NiIs...",  // 또 다른 JWS
    "signedRenewalInfo": "eyJhbGciOiJFUzI1NiIs..."       // 또 다른 JWS
  },
  "version": "2.0",
  "signedDate": 1640995200000
}
```

### 3. SignedTransactionInfo 디코딩 후 구조 (JWSTransaction)
```json
{
  "originalTransactionId": "1000000123456789",
  "transactionId": "1000000123456790",
  "bundleId": "com.example.app",
  "productId": "premium_monthly",
  "subscriptionGroupIdentifier": "21234567",
  "originalPurchaseDate": 1640995200000,
  "purchaseDate": 1640995200000,
  "expiresDate": 1643673600000,
  "type": "Auto-Renewable Subscription",
  "inAppOwnershipType": "PURCHASED",
  "signedDate": 1640995200000
}
```

### 4. SignedRenewalInfo 디코딩 후 구조 (JWSRenewalInfo)
```json
{
  "originalTransactionId": "1000000123456789",
  "autoRenewProductId": "premium_monthly",
  "productId": "premium_monthly",
  "autoRenewStatus": 1,  // 1: 활성화, 0: 비활성화
  "isInBillingRetryPeriod": false,
  "priceIncreaseStatus": 0,
  "gracePeriodExpiresDate": 1641081600000,
  "signedDate": 1640995200000,
  "environment": "Sandbox"
}
```

## 알림 타입 상세

### 주요 Notification Types

| 타입 | 설명 | 처리 로직 |
|------|------|-----------|
| `SUBSCRIBED` | 새 구독 시작 | 새 구독 생성 |
| `DID_RENEW` | 구독 갱신 성공 | 만료일 연장, RENEWAL 이벤트 생성 |
| `EXPIRED` | 구독 만료 | 상태를 EXPIRED로 변경 |
| `DID_CHANGE_RENEWAL_STATUS` | 자동갱신 설정 변경 | autoRenewing 필드 업데이트 |
| `DID_FAIL_TO_RENEW` | 갱신 실패 | subtype에 따라 상태 변경 |
| `GRACE_PERIOD_EXPIRED` | 유예기간 만료 | 상태를 EXPIRED로 변경 |
| `REFUND` | 환불 | 상태를 CANCELED로 변경 |
| `REVOKE` | 구독 취소 | 상태를 CANCELED로 변경 |

### Notification Subtypes

| Subtype | 적용 알림 타입 | 설명 |
|---------|----------------|------|
| `INITIAL_BUY` | SUBSCRIBED | 최초 구매 |
| `RESUBSCRIBE` | SUBSCRIBED | 재구독 |
| `UPGRADE` | DID_CHANGE_RENEWAL_PREF | 업그레이드 |
| `DOWNGRADE` | DID_CHANGE_RENEWAL_PREF | 다운그레이드 |
| `AUTO_RENEW_ENABLED` | DID_CHANGE_RENEWAL_STATUS | 자동 갱신 활성화 |
| `AUTO_RENEW_DISABLED` | DID_CHANGE_RENEWAL_STATUS | 자동 갱신 비활성화 |
| `VOLUNTARY` | DID_FAIL_TO_RENEW | 자발적 취소 |
| `BILLING_RETRY` | DID_FAIL_TO_RENEW | 결제 재시도 중 |
| `GRACE_PERIOD` | DID_FAIL_TO_RENEW | 유예 기간 |

## 처리 흐름

1. **수신**: App Store에서 JWS 형태의 signedPayload 수신
2. **1차 디코딩**: signedPayload JWT 디코딩하여 responseBodyV2DecodedPayload 획득
3. **2차 디코딩**: signedTransactionInfo, signedRenewalInfo JWT 디코딩
4. **타입 분류**: notificationType과 subtype 분석
5. **이벤트 처리**: 알림 타입에 따른 비즈니스 로직 실행
6. **상태 업데이트**: 구독 상태 및 PaymentEvent 업데이트

## 보안 특징

### 1. 다중 계층 JWS 보안
- **signedPayload**: App Store가 서명한 최상위 JWS
- **signedTransactionInfo**: 거래 정보가 담긴 별도 JWS
- **signedRenewalInfo**: 갱신 정보가 담긴 별도 JWS

### 2. 서명 검증 (구현 권장)
- JWS Header의 x5c 파라미터로 인증서 체인 검증
- Apple의 루트 인증서로 서명 유효성 검증
- 알고리즘: ES256 (ECDSA using P-256 and SHA-256)

### 3. 데이터 무결성
- 각 JWS는 독립적으로 검증 가능
- originalTransactionId로 구독 연결성 확인
- 타임스탬프 기반 재플레이 공격 방지

## 설정 요구사항

### 1. App Store Connect
- App Store Server Notifications 활성화
- Webhook URL 설정: `https://your-domain.com/api/ios/webhook`
- Bundle ID 등록 및 구독 상품 설정

### 2. 인증 정보
- App Store Connect API Key (JWT 생성용)
- 서명 검증용 Apple 루트 인증서 (선택사항)

### 3. 네트워크 설정
- HTTPS 필수 (Apple은 HTTP 미지원)
- 응답 시간: 30초 이내 권장
- 응답 코드: 200 (성공), 기타 (재시도)

## 관련 문서

- [App Store Server Notifications V2](https://developer.apple.com/documentation/appstoreservernotifications/app-store-server-notifications-v2)
- [App Store Server Notifications](https://developer.apple.com/documentation/appstoreservernotifications)
- [Enabling App Store Server Notifications](https://developer.apple.com/documentation/appstoreservernotifications/enabling-app-store-server-notifications)
- [Receiving App Store Server Notifications](https://developer.apple.com/documentation/appstoreservernotifications/receiving-app-store-server-notifications)
- [App Store Server Notifications Changelog](https://developer.apple.com/documentation/appstoreservernotifications/app-store-server-notifications-changelog)
- [App Store Connect - Server URLs Configuration](https://developer.apple.com/help/app-store-connect/configure-in-app-purchase-settings/enter-server-urls-for-app-store-server-notifications)
- [JSON Web Signature (JWS) - RFC 7515](https://datatracker.ietf.org/doc/html/rfc7515)

## 예제 코드

### Server Notifications 처리
```kotlin
@PostMapping("/webhook")
fun handleAppStoreNotification(
    @RequestBody notificationRequest: AppStoreServerNotificationRequest
): ResponseEntity<Map<String, Any>> {
    val processed = appStoreNotificationService.processServerNotification(notificationRequest)
    return if (processed) {
        ResponseEntity.ok(mapOf("success" to true))
    } else {
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("success" to false))
    }
}
```

### JWS 페이로드 디코딩
```kotlin
private fun decodeSignedPayload(signedPayload: String): AppStoreServerNotification? {
    return try {
        val parts = signedPayload.split(".")
        if (parts.size != 3) return null
        
        val payload = parts[1]
        val decodedBytes = Base64.getUrlDecoder().decode(payload)
        objectMapper.readValue(decodedBytes, AppStoreServerNotification::class.java)
    } catch (e: Exception) {
        println("Failed to decode signed payload: ${e.message}")
        null
    }
}
```

### 구독 갱신 처리
```kotlin
private fun handleDidRenew(
    transactionInfo: TransactionInfo,
    existingSubscription: Subscription?,
    renewalInfo: RenewalInfo?
): Boolean {
    if (existingSubscription == null) return false
    
    val newExpiryDate = transactionInfo.expiresDate?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
    } ?: return false
    
    val renewedSubscription = existingSubscription.renew(newExpiryDate)
    subscriptionRepository.save(renewedSubscription)
    createPaymentEvent(existingSubscription.id, PaymentEventType.RENEWAL, Platform.IOS)
    
    return true
}
```