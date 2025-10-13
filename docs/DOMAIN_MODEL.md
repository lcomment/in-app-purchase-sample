# 인앱결제 시스템 도메인 모델 문서

## 개요

이 문서는 AOS(Android)와 iOS 플랫폼의 구독형 인앱결제를 처리하는 시스템의 도메인 모델을 설명합니다.

## 아키텍처

```
┌─────────────────┐    ┌─────────────────┐
│   iap-aos       │    │   iap-ios       │
│   (Port: 8080)  │    │   (Port: 8081)  │
└─────────────────┘    └─────────────────┘
         │                       │
         └───────┬───────────────┘
                 │
         ┌─────────────────┐
         │   iap-domain    │
         │  (공통 도메인)   │
         └─────────────────┘
```

## 핵심 도메인 엔터티

### 1. Platform (플랫폼 구분)

```kotlin
enum class Platform {
    AOS,    // Android (Google Play)
    IOS     // iOS (App Store)
}
```

**용도**: 구독이 어느 플랫폼에서 발생했는지 구분

---

### 2. SubscriptionStatus (구독 상태)

```kotlin
enum class SubscriptionStatus {
    ACTIVE,             // 활성 구독
    EXPIRED,            // 만료된 구독
    CANCELED,           // 취소된 구독
    ON_HOLD,            // 일시 정지 (결제 실패 등)
    IN_GRACE_PERIOD,    // 유예 기간 (결제 실패 후 일정 기간)
    PAUSED              // 사용자가 일시 정지
}
```

**매핑 관계**:
- **Google Play**: `SUBSCRIPTION_STATE_ACTIVE` → `ACTIVE`
- **App Store**: Status Code `1` → `ACTIVE`

---

### 3. SubscriptionPlan (구독 상품)

```kotlin
data class SubscriptionPlan(
    val id: String,             // 내부 상품 ID
    val name: String,           // 상품명 (예: "프리미엄 월간")
    val productId: String,      // 플랫폼별 상품 ID
    val price: BigDecimal,      // 가격
    val currency: String,       // 통화 (USD, KRW 등)
    val duration: Duration,     // 구독 기간 (1개월, 1년 등)
    val platform: Platform,    // 플랫폼 구분
    val isActive: Boolean = true // 활성 상품 여부
)
```

**예시**:
```kotlin
SubscriptionPlan(
    id = "premium_monthly_aos",
    name = "프리미엄 월간 구독",
    productId = "premium.monthly",  // Google Play 상품 ID
    price = BigDecimal("9.99"),
    currency = "USD",
    duration = Duration.ofDays(30),
    platform = Platform.AOS
)
```

---

### 4. Subscription (구독 정보) ⭐ 핵심 엔터티

```kotlin
data class Subscription(
    val id: String,                     // 내부 구독 ID
    val userId: String,                 // 사용자 ID
    val planId: String,                 // 구독 상품 ID
    val platform: Platform,            // 플랫폼
    val purchaseToken: String,          // 플랫폼별 구매 토큰
    val orderId: String,                // 주문 ID
    val status: SubscriptionStatus,     // 구독 상태
    val startDate: LocalDateTime,       // 구독 시작일
    val expiryDate: LocalDateTime,      // 만료일
    val autoRenewing: Boolean,          // 자동 갱신 여부
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    // 비즈니스 로직 메서드
    fun isActive(): Boolean = status == SubscriptionStatus.ACTIVE
    
    fun isExpired(): Boolean = LocalDateTime.now().isAfter(expiryDate)
    
    fun renew(newExpiryDate: LocalDateTime): Subscription = 
        copy(expiryDate = newExpiryDate, updatedAt = LocalDateTime.now())
    
    fun cancel(): Subscription = 
        copy(status = SubscriptionStatus.CANCELED, autoRenewing = false, updatedAt = LocalDateTime.now())
}
```

**플랫폼별 키 필드**:
- **AOS**: `purchaseToken` (Google Play의 purchaseToken)
- **iOS**: `purchaseToken` = transactionId, `orderId` = originalTransactionId

---

### 5. PaymentStatus (결제 상태)

```kotlin
enum class PaymentStatus {
    PENDING,                // 결제 대기
    SUCCESS,                // 결제 성공
    FAILED,                 // 결제 실패
    REFUNDED,               // 환불 완료
    PARTIALLY_REFUNDED,     // 부분 환불
    DISPUTED                // 분쟁 중
}
```

---

### 6. Payment (결제 기록)

```kotlin
data class Payment(
    val id: String,                     // 결제 ID
    val subscriptionId: String,         // 연결된 구독 ID
    val userId: String,                 // 사용자 ID
    val platform: Platform,            // 플랫폼
    val orderId: String,                // 주문 ID
    val transactionId: String,          // 트랜잭션 ID
    val purchaseToken: String,          // 구매 토큰
    val productId: String,              // 상품 ID
    val amount: BigDecimal,             // 결제 금액
    val currency: String,               // 통화
    val status: PaymentStatus,          // 결제 상태
    val paymentDate: LocalDateTime,     // 결제일
    val acknowledgmentState: Boolean,   // 확인 상태
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    // 비즈니스 로직 메서드
    fun acknowledge(): Payment = copy(acknowledgmentState = true, updatedAt = LocalDateTime.now())
    
    fun refund(): Payment = copy(status = PaymentStatus.REFUNDED, updatedAt = LocalDateTime.now())
    
    fun isSuccess(): Boolean = status == PaymentStatus.SUCCESS
}
```

---

### 7. PaymentEvent (결제 이벤트)

```kotlin
enum class PaymentEventType {
    PURCHASE,               // 최초 구매
    RENEWAL,                // 구독 갱신
    CANCELLATION,           // 구독 취소
    REFUND,                 // 환불
    EXPIRATION,             // 만료
    GRACE_PERIOD_START,     // 유예 기간 시작
    GRACE_PERIOD_END,       // 유예 기간 종료
    PAUSE,                  // 일시 정지
    RESUME                  // 재개
}

data class PaymentEvent(
    val id: String,                     // 이벤트 ID
    val subscriptionId: String,         // 구독 ID
    val paymentId: String? = null,      // 결제 ID (있는 경우)
    val eventType: PaymentEventType,    // 이벤트 타입
    val platform: Platform,            // 플랫폼
    val eventData: Map<String, Any> = emptyMap(), // 추가 데이터
    val processedAt: LocalDateTime? = null,       // 처리 완료 시간
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun markAsProcessed(): PaymentEvent = copy(processedAt = LocalDateTime.now())
    
    fun isProcessed(): Boolean = processedAt != null
}
```

**용도**: RTDN(Real-Time Developer Notifications)에서 오는 이벤트 추적

---

### 8. Refund (환불 정보) - Platform-Specific Implementation

플랫폼별 환불 처리는 각각의 모듈에서 독립적으로 구현됩니다:

#### Google Play 환불 (iap-aos 모듈)

```kotlin
data class GooglePlayRefundRequest(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val token: String,
    val productId: String?,
    val type: GooglePlayRefundType,
    val amount: BigDecimal?,
    val currency: String = "USD",
    val reason: String?,
    val customerNote: String?
)

enum class GooglePlayRefundType {
    SUBSCRIPTION,
    IN_APP_PURCHASE
}

data class GooglePlayRefundResult(
    val success: Boolean,
    val refundId: String?,
    val refundAmount: BigDecimal? = null,
    val currency: String? = null,
    val error: String? = null,
    val timestamp: LocalDateTime,
    val metadata: Map<String, String> = emptyMap()
)
```

#### App Store 환불 (iap-ios 모듈)

```kotlin
data class AppStoreRefundRequest(
    val id: String = UUID.randomUUID().toString(),
    val originalTransactionId: String,
    val type: AppStoreRefundType,
    val reason: AppStoreRefundReason,
    val amount: BigDecimal?,
    val currency: String = "USD",
    val customerNote: String?
)

enum class AppStoreRefundType {
    SUBSCRIPTION,
    IN_APP_PURCHASE
}

enum class AppStoreRefundReason(val code: String, val description: String) {
    CUSTOMER_REQUEST("0", "고객 요청"),
    TECHNICAL_ISSUE("1", "기술적 문제"),
    BILLING_ERROR("2", "결제 오류"),
    FRAUD_PREVENTION("3", "사기 방지"),
    OTHER("99", "기타")
}

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
```

**플랫폼별 환불 서비스**:
- **GooglePlayRefundService** (`iap-aos` 모듈): Google Play Developer API 사용
- **AppStoreRefundService** (`iap-ios` 모듈): App Store Server API 사용

---

### 9. SubscriptionHistory (구독 이력)

```kotlin
data class SubscriptionHistory(
    val id: String,                     // 이력 ID
    val subscriptionId: String,         // 구독 ID
    val previousStatus: SubscriptionStatus, // 이전 상태
    val newStatus: SubscriptionStatus,      // 새로운 상태
    val reason: String,                     // 변경 사유
    val metadata: Map<String, Any> = emptyMap(), // 추가 정보
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

---

## 도메인 관계도

```
┌─────────────────┐       ┌─────────────────┐
│ SubscriptionPlan│◄──────┤   Subscription  │
│                 │  1:N  │                 │
└─────────────────┘       └─────────────────┘
                                   │ 1:N
                                   ▼
                          ┌─────────────────┐
                          │    Payment      │
                          │                 │
                          └─────────────────┘
                                   │ 1:1
                                   ▼
                          ┌─────────────────┐
                          │Platform-Specific│
                          │  Refund Models  │
                          │  (AOS/iOS 모듈) │
                          └─────────────────┘

┌─────────────────┐       ┌─────────────────┐
│ PaymentEvent    │───────┤   Subscription  │
│                 │  N:1  │                 │
└─────────────────┘       └─────────────────┘
                                   │ 1:N
                                   ▼
                          ┌─────────────────┐
                          │SubscriptionHistory│
                          │                 │
                          └─────────────────┘
```

## 비즈니스 규칙

### 1. 구독 생성 규칙
- **중복 방지**: 동일한 `purchaseToken`으로는 하나의 구독만 생성 가능
- **플랫폼 검증**: AOS는 Google Play API, iOS는 App Store Connect API로 검증 필수
- **자동 Payment 생성**: 구독 생성 시 연결된 Payment 엔터티 자동 생성

### 2. 구독 상태 변경 규칙
- **상태 변경 이력**: 모든 상태 변경은 `SubscriptionHistory`에 기록
- **만료일 검증**: `ACTIVE` 상태는 `expiryDate`가 현재 시간 이후여야 함
- **자동 갱신**: `autoRenewing = true`인 경우 플랫폼에서 자동 갱신 처리

### 3. 플랫폼별 특이사항

#### AOS (Google Play)
- **검증 키**: `purchaseToken`
- **API**: Google Play Developer API `purchases.subscriptionsv2.get`
- **RTDN**: Google Pub/Sub 또는 HTTP POST webhook
- **환불 처리**: `GooglePlayRefundService` (iap-aos 모듈)

#### iOS (App Store)
- **검증 키**: `transactionId` + `originalTransactionId`
- **API**: App Store Connect API `/inApps/v1/transactions/{transactionId}`
- **Server Notifications**: HTTP POST webhook
- **환불 처리**: `AppStoreRefundService` (iap-ios 모듈)

## 사용 예시

### 구독 생성 플로우
```kotlin
// 1. 플랫폼 검증
val verificationResponse = subscriptionService.verifySubscription(request)

// 2. 구독 생성
val subscription = Subscription(
    id = UUID.randomUUID().toString(),
    userId = "user123",
    planId = "premium_monthly",
    platform = Platform.AOS,
    purchaseToken = "abcd1234...",
    orderId = "order_5678",
    status = SubscriptionStatus.ACTIVE,
    startDate = LocalDateTime.now(),
    expiryDate = LocalDateTime.now().plusDays(30),
    autoRenewing = true
)

// 3. 저장 및 Payment 생성
val savedSubscription = subscriptionRepository.save(subscription)
val payment = createPaymentFromSubscription(savedSubscription)
paymentRepository.save(payment)
```

### 구독 갱신 플로우
```kotlin
// RTDN 이벤트 수신
val event = PaymentEvent(
    subscriptionId = subscription.id,
    eventType = PaymentEventType.RENEWAL,
    platform = Platform.AOS
)

// 구독 갱신 처리
val renewedSubscription = subscription.renew(newExpiryDate)
subscriptionRepository.save(renewedSubscription)

// 이력 기록
val history = SubscriptionHistory(
    subscriptionId = subscription.id,
    previousStatus = SubscriptionStatus.ACTIVE,
    newStatus = SubscriptionStatus.ACTIVE,
    reason = "Auto renewal"
)
```

이 도메인 모델은 AOS와 iOS 플랫폼의 구독형 인앱결제를 통합적으로 관리할 수 있도록 설계되었습니다.