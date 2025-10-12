# 인앱결제 시스템 도메인 모델 설계

## 개요

AOS(Android)와 iOS 플랫폼의 구독형 인앱결제를 통합 관리하는 시스템의 도메인 모델입니다. Google Play Store와 App Store의 구독 결제를 검증하고, 비즈니스 혜택을 지급하는 도메인을 설계했습니다.

## 핵심 도메인

### Platform (플랫폼)

플랫폼 구분을 위한 열거형 도메인

**속성:**
- `AOS`: Android 플랫폼 (Google Play Store)
- `IOS`: iOS 플랫폼 (App Store)

**비즈니스 규칙:**
- 각 구독은 반드시 하나의 플랫폼에 속해야 함
- 플랫폼별로 다른 검증 방식과 API를 사용함

---

### SubscriptionPlan (구독 상품)

구독 상품 정보를 정의하는 도메인
- Aggregate Root

**속성:**
- `id` (String): 내부 상품 고유 식별자
- `name` (String): 상품명
- `productId` (String): 플랫폼별 상품 ID
- `price` (BigDecimal): 구독 가격
- `currency` (String): 통화 코드
- `duration` (Duration): 구독 기간
- `platform` (Platform): 플랫폼 구분
- `isActive` (Boolean): 활성 상품 여부

**비즈니스 규칙:**
- 가격은 0보다 커야 함
- 구독 기간은 1일 이상이어야 함
- 동일한 플랫폼과 productId 조합은 유일해야 함

---

### SubscriptionStatus (구독 상태)

구독의 현재 상태를 나타내는 열거형 도메인

**속성:**
- `ACTIVE`: 활성 구독 상태
- `EXPIRED`: 만료된 구독
- `CANCELED`: 사용자가 취소한 구독
- `ON_HOLD`: 결제 실패로 일시 정지된 상태
- `IN_GRACE_PERIOD`: 결제 실패 후 유예 기간
- `PAUSED`: 사용자가 일시 정지한 상태

**플랫폼별 매핑:**
- Google Play: `SUBSCRIPTION_STATE_ACTIVE` → `ACTIVE`
- App Store: Status Code `1` → `ACTIVE`

---

### Subscription (구독)

사용자의 구독 정보를 관리하는 핵심 도메인
- Aggregate Root

**속성:**
- `id` (String): 구독 고유 식별자
- `userId` (String): 사용자 식별자
- `planId` (String): 구독 상품 식별자
- `platform` (Platform): 플랫폼 구분
- `purchaseToken` (String): 플랫폼별 구매 토큰
- `orderId` (String): 주문 식별자
- `status` (SubscriptionStatus): 구독 상태
- `startDate` (LocalDateTime): 구독 시작일
- `expiryDate` (LocalDateTime): 만료일
- `autoRenewing` (Boolean): 자동 갱신 여부
- `createdAt` (LocalDateTime): 생성 일시
- `updatedAt` (LocalDateTime): 수정 일시

**비즈니스 규칙:**
- 동일한 purchaseToken으로는 하나의 구독만 생성 가능
- ACTIVE 상태의 구독은 expiryDate가 현재 시간 이후여야 함
- 만료일은 시작일 이후여야 함
- 플랫폼별로 purchaseToken 형식이 다름 (AOS: purchaseToken, iOS: transactionId)

**도메인 메서드:**
- `isActive()`: 구독이 활성 상태인지 확인
- `isExpired()`: 구독이 만료되었는지 확인
- `renew()`: 구독 갱신 처리
- `cancel()`: 구독 취소 처리

---

### PaymentStatus (결제 상태)

결제의 현재 상태를 나타내는 열거형 도메인

**속성:**
- `PENDING`: 결제 대기 중
- `SUCCESS`: 결제 성공
- `FAILED`: 결제 실패
- `REFUNDED`: 전액 환불
- `PARTIALLY_REFUNDED`: 부분 환불
- `DISPUTED`: 결제 분쟁 중

---

### Payment (결제)

구독 결제 정보를 관리하는 도메인
- Aggregate Root

**속성:**
- `id` (String): 결제 고유 식별자
- `subscriptionId` (String): 연결된 구독 식별자
- `userId` (String): 사용자 식별자
- `platform` (Platform): 플랫폼 구분
- `orderId` (String): 주문 식별자
- `transactionId` (String): 트랜잭션 식별자
- `purchaseToken` (String): 구매 토큰
- `productId` (String): 상품 식별자
- `amount` (BigDecimal): 결제 금액
- `currency` (String): 통화 코드
- `status` (PaymentStatus): 결제 상태
- `paymentDate` (LocalDateTime): 결제일
- `acknowledgmentState` (Boolean): 확인 상태
- `createdAt` (LocalDateTime): 생성 일시
- `updatedAt` (LocalDateTime): 수정 일시

**비즈니스 규칙:**
- 결제 금액은 0보다 커야 함
- 결제일은 생성일 이후여야 함
- SUCCESS 상태의 결제만 환불 가능

**도메인 메서드:**
- `acknowledge()`: 결제 확인 처리
- `refund()`: 환불 처리
- `isSuccess()`: 결제 성공 여부 확인

---

### PaymentEventType (결제 이벤트 타입)

결제 관련 이벤트 유형을 정의하는 열거형 도메인

**속성:**
- `PURCHASE`: 최초 구매
- `RENEWAL`: 구독 자동 갱신
- `CANCELLATION`: 구독 취소
- `REFUND`: 환불 발생
- `EXPIRATION`: 구독 만료
- `GRACE_PERIOD_START`: 유예 기간 시작
- `GRACE_PERIOD_END`: 유예 기간 종료
- `PAUSE`: 구독 일시 정지
- `RESUME`: 구독 재개

---

### PaymentEvent (결제 이벤트)

RTDN(Real-Time Developer Notifications) 이벤트를 관리하는 도메인
- Aggregate Root

**속성:**
- `id` (String): 이벤트 고유 식별자
- `subscriptionId` (String): 구독 식별자
- `paymentId` (String, nullable): 결제 식별자
- `eventType` (PaymentEventType): 이벤트 타입
- `platform` (Platform): 플랫폼 구분
- `eventData` (Map<String, Any>): 이벤트 추가 데이터
- `processedAt` (LocalDateTime, nullable): 처리 완료 시간
- `createdAt` (LocalDateTime): 생성 일시

**비즈니스 규칙:**
- 이벤트는 플랫폼별 RTDN을 통해서만 생성됨
- 한번 처리된 이벤트는 재처리되지 않음
- 이벤트 데이터는 플랫폼별로 다른 구조를 가짐

**도메인 메서드:**
- `markAsProcessed()`: 이벤트 처리 완료 표시
- `isProcessed()`: 이벤트 처리 여부 확인

---

### Refund (환불)

결제 환불 정보를 관리하는 도메인
- Aggregate Root

**속성:**
- `id` (String): 환불 고유 식별자
- `paymentId` (String): 원본 결제 식별자
- `subscriptionId` (String): 구독 식별자
- `userId` (String): 사용자 식별자
- `amount` (BigDecimal): 환불 금액
- `currency` (String): 통화 코드
- `reason` (String): 환불 사유
- `platform` (Platform): 플랫폼 구분
- `platformRefundId` (String, nullable): 플랫폼별 환불 식별자
- `refundDate` (LocalDateTime): 환불 처리일
- `createdAt` (LocalDateTime): 생성 일시

**비즈니스 규칙:**
- 환불 금액은 원본 결제 금액을 초과할 수 없음
- 환불 사유는 필수 입력 항목
- 하나의 결제당 최대 하나의 환불만 가능

---

### SubscriptionHistory (구독 이력)

구독 상태 변경 이력을 관리하는 도메인
- Value Object

**속성:**
- `id` (String): 이력 고유 식별자
- `subscriptionId` (String): 구독 식별자
- `previousStatus` (SubscriptionStatus): 이전 상태
- `newStatus` (SubscriptionStatus): 변경된 상태
- `reason` (String): 변경 사유
- `metadata` (Map<String, Any>): 추가 정보
- `createdAt` (LocalDateTime): 생성 일시

**비즈니스 규칙:**
- 모든 구독 상태 변경은 이력으로 기록됨
- 변경 사유는 필수 입력 항목
- 이력은 수정되지 않음 (Immutable)

## 도메인 관계

### Aggregate 관계
- **SubscriptionPlan** ← 1:N → **Subscription**
- **Subscription** ← 1:N → **Payment** 
- **Payment** ← 1:1 → **Refund**
- **Subscription** ← 1:N → **PaymentEvent**
- **Subscription** ← 1:N → **SubscriptionHistory**

### 플랫폼별 특징

#### AOS (Google Play)
- **검증 키**: purchaseToken
- **API**: Google Play Developer API v3 (purchases.subscriptionsv2)
- **알림**: Google Cloud Pub/Sub 또는 HTTP POST
- **인증**: Service Account JSON 키

#### iOS (App Store)
- **검증 키**: transactionId + originalTransactionId
- **API**: App Store Connect API v1
- **알림**: App Store Server Notifications (HTTP POST)
- **인증**: JWT (ES256, Private Key)

## 비즈니스 규칙

### 구독 생성 규칙
1. 플랫폼별 API를 통한 검증 필수
2. 동일한 구매 토큰으로 중복 생성 불가
3. 구독 생성 시 연결된 Payment 엔터티 자동 생성
4. 모든 상태 변경은 SubscriptionHistory에 기록

### 결제 처리 규칙
1. SUCCESS 상태의 결제만 구독 혜택 지급 대상
2. 결제 확인(acknowledgment) 필수
3. 환불 발생 시 구독 상태 변경 및 혜택 회수

### 이벤트 처리 규칙
1. RTDN을 통한 실시간 상태 동기화
2. 이벤트 중복 처리 방지 (idempotency)
3. 처리 실패 시 재시도 메커니즘 필요

이 도메인 모델은 AOS와 iOS의 구독형 인앱결제를 통합적으로 관리하면서도, 각 플랫폼의 특성을 반영하여 설계되었습니다.