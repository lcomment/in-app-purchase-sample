# Google Play Real-time Developer Notifications (RTDN)

## 개요

Google Play Real-time Developer Notifications (RTDN)는 사용자의 구독 상태가 변경될 때 Google Play에서 실시간으로 알림을 받을 수 있는 메커니즘입니다. Cloud Pub/Sub를 통해 구독 갱신, 취소, 만료 등의 이벤트를 즉시 감지할 수 있습니다.

## 현재 구현한 기능

### 1. 엔드포인트
- **RTDN Webhook**: `POST /api/aos/rtdn`
- **Health Check**: `GET /api/aos/rtdn/health`
- **Endpoint 검증**: `GET /api/aos/rtdn`

### 2. 지원하는 알림 타입

#### SubscriptionNotification 처리
- `SUBSCRIPTION_PURCHASED` (4): 새 구독 구매
- `SUBSCRIPTION_RENEWED` (2): 구독 갱신
- `SUBSCRIPTION_CANCELED` (3): 구독 취소
- `SUBSCRIPTION_EXPIRED` (13): 구독 만료
- `SUBSCRIPTION_ON_HOLD` (5): 구독 일시 정지
- `SUBSCRIPTION_IN_GRACE_PERIOD` (6): 유예 기간 진입
- `SUBSCRIPTION_RECOVERED` (1): 구독 복구

#### TestNotification 처리
- Google Play Console에서 전송하는 테스트 알림

### 3. 핵심 구현 컴포넌트
- **RTDNController**: HTTP 요청 처리
- **RTDNEventService**: 알림 이벤트 처리 로직
- **SubscriptionRepository**: 구독 데이터 저장/조회
- **PaymentRepository**: 결제 이벤트 추적

## 메시지 구조

### 1. RTDN 요청 구조
```json
{
  "message": {
    "data": "eyJwYWNrYWdlTmFtZSI6...",  // Base64 인코딩된 실제 알림 데이터
    "messageId": "msg_12345",
    "publishTime": "2024-01-01T12:00:00.000Z",
    "attributes": {}
  },
  "subscription": "projects/my-project/subscriptions/my-subscription"
}
```

### 2. 디코딩된 알림 데이터 구조
```json
{
  "version": "1.0",
  "packageName": "com.example.app",
  "eventTimeMillis": 1640995200000,
  "subscriptionNotification": {
    "version": "1.0",
    "notificationType": 2,  // SUBSCRIPTION_RENEWED
    "purchaseToken": "ghkljhgkljhgkljhgkljh",
    "subscriptionId": "premium_monthly"
  }
}
```

### 3. 알림 타입 코드

| 코드 | 타입 | 설명 |
|------|------|------|
| 1 | SUBSCRIPTION_RECOVERED | 구독 복구 (결제 문제 해결) |
| 2 | SUBSCRIPTION_RENEWED | 구독 갱신 성공 |
| 3 | SUBSCRIPTION_CANCELED | 구독 취소 |
| 4 | SUBSCRIPTION_PURCHASED | 새 구독 구매 |
| 5 | SUBSCRIPTION_ON_HOLD | 구독 일시 정지 (결제 실패) |
| 6 | SUBSCRIPTION_IN_GRACE_PERIOD | 유예 기간 진입 |
| 7 | SUBSCRIPTION_RESTARTED | 구독 재시작 |
| 8 | SUBSCRIPTION_PRICE_CHANGE_CONFIRMED | 가격 변경 확인 |
| 9 | SUBSCRIPTION_DEFERRED | 구독 연기 |
| 10 | SUBSCRIPTION_PAUSED | 구독 일시 정지 |
| 11 | SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED | 일시 정지 일정 변경 |
| 12 | SUBSCRIPTION_REVOKED | 구독 취소 (환불) |
| 13 | SUBSCRIPTION_EXPIRED | 구독 만료 |

## 처리 흐름

1. **수신**: Cloud Pub/Sub에서 RTDN 메시지 수신
2. **디코딩**: Base64 인코딩된 데이터 디코딩
3. **타입 분류**: subscriptionNotification, testNotification 구분
4. **이벤트 처리**: 알림 타입에 따른 비즈니스 로직 실행
5. **상태 업데이트**: 구독 상태 및 PaymentEvent 업데이트
6. **API 재검증**: 필요시 Google Play Developer API 호출

## 보안 고려사항

### 1. 알림 검증
- **중복 처리 방지**: messageId 기반 중복 검사
- **타임스탬프 검증**: eventTimeMillis 검증
- **패키지명 검증**: packageName 일치 확인

### 2. 데이터 정합성
- RTDN은 상태 변경 알림만 제공
- 완전한 정보 획득을 위해 Google Play Developer API 재호출 필요
- 구독 상태는 API 응답으로 최종 확정

## 설정 요구사항

### 1. Google Cloud Pub/Sub
- Topic 생성 및 권한 설정
- Push 구독 또는 Pull 구독 설정
- 엔드포인트 URL: `https://your-domain.com/api/aos/rtdn`

### 2. Google Play Console
- 실시간 개발자 알림 활성화
- Pub/Sub Topic 연결
- 테스트 알림 발송으로 연결 확인

## 관련 문서

- [Google Play Real-time Developer Notifications](https://developer.android.com/google/play/billing/rtdn-reference)
- [Google Play Developer API - Subscriptions](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptions)
- [Cloud Pub/Sub Documentation](https://cloud.google.com/pubsub/docs)
- [Google Play Console - 실시간 개발자 알림 설정](https://play.google.com/console/developers)

## 예제 코드

### RTDN 메시지 처리
```kotlin
@PostMapping("/rtdn")
fun handleRTDN(@RequestBody rtdnRequest: GooglePlayRTDNRequest): ResponseEntity<Map<String, Any>> {
    val processed = rtdnEventService.processRTDNNotification(rtdnRequest)
    return if (processed) {
        ResponseEntity.ok(mapOf("success" to true))
    } else {
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("success" to false))
    }
}
```

### 구독 갱신 처리
```kotlin
private fun handleSubscriptionRenewed(
    subscriptionNotification: SubscriptionNotification,
    existingSubscription: Subscription?
): Boolean {
    // Google Play API로 최신 구독 정보 조회
    val verificationResponse = googlePlaySubscriptionService.verifySubscription(request)
    
    if (verificationResponse.isValid) {
        val renewedSubscription = existingSubscription.renew(verificationResponse.expiryTime)
        subscriptionRepository.save(renewedSubscription)
        createPaymentEvent(existingSubscription.id, PaymentEventType.RENEWAL, Platform.AOS)
        return true
    }
    return false
}
```