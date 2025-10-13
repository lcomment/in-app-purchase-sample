# Refund Process Guide

## Overview

환불 처리는 모바일 인앱 결제 시스템에서 중요한 기능으로, 고객 만족도와 법적 요구사항을 충족하기 위해 필수적입니다. 이 문서는 Google Play와 App Store의 환불 프로세스를 통합적으로 관리하는 방법을 설명합니다.

## Architecture

```
Customer Request → Refund Service → Platform API → Settlement Update → Notification
                      ↓
                 Validation & Approval → Internal Event → Reconciliation
```

## Platform-Specific Refund APIs

### Google Play Developer API (2024)

#### Primary Refund Method: orders.refund

**공식 문서**: [orders.refund API](https://developers.google.com/android-publisher/api-ref/rest/v3/orders/refund)

```http
POST https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{packageName}/orders/{orderId}:refund
```

**주요 특징:**
- **시간 제한**: 3년 이내 주문만 환불 가능
- **즉시 해지**: `revoke=true` 파라미터로 즉시 액세스 종료
- **구독 처리**: 최신 주문 환불 시 구독 자동 취소
- **OAuth 스코프**: `https://www.googleapis.com/auth/androidpublisher`

**Request Parameters:**
- `packageName` (string): 앱 패키지명
- `orderId` (string): 원본 구매 주문 ID
- `revoke` (boolean, optional): 즉시 액세스 종료 여부

**Example Request:**
```bash
curl -X POST \
  'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.example.app/orders/ORDER_ID_123:refund?revoke=true' \
  -H 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  -H 'Content-Type: application/json' \
  --data-raw ''
```

**구독별 동작:**
- 최신 주문 환불: 즉시 구독 해지 + 향후 결제 취소
- 이전 주문 환불: 환불만 처리, 구독은 유지

#### Deprecated Methods (사용 중단)
- `purchases.subscriptions.refund` → `orders.refund` 사용 권장
- `purchases.subscriptions.revoke` → `purchases.subscriptionsv2.revoke` 사용 권장

### App Store Server API (2024)

#### Refund History Retrieval

**공식 문서**: [Get Refund History](https://developer.apple.com/documentation/appstoreserverapi/get-refund-history)

```http
GET https://api.storekit.itunes.apple.com/inApps/v1/refunds/lookup/{originalTransactionId}
```

**주요 특징:**
- **페이지네이션**: 대량 데이터 처리 지원
- **JWT 인증**: JSON Web Signature 기반 인증
- **통합 관리**: 모든 고객 환불 내역 조회 가능

**Response Format:**
```json
{
  "signedTransactions": ["JWS_SIGNED_TRANSACTION_1", "JWS_SIGNED_TRANSACTION_2"],
  "revision": "12345",
  "hasMore": true,
  "bundleId": "com.example.app"
}
```

#### Legacy Version (V1)
**공식 문서**: [Get Refund History V1](https://developer.apple.com/documentation/appstoreserverapi/get-refund-history-v1)
- 최대 50개 환불 내역 조회
- 기본적인 환불 이력 관리

**참고**: Apple은 직접적인 환불 처리 API를 제공하지 않습니다. 환불은 App Store Connect를 통해 수동으로 처리하거나, 고객이 직접 Apple에 요청해야 합니다.

## Refund Process Workflow

### 1. 환불 요청 수신
```kotlin
data class RefundRequest(
    val platform: Platform,
    val transactionId: String,
    val originalTransactionId: String?,
    val reason: RefundReason,
    val amount: BigDecimal?,
    val requestedBy: RefundRequestor,
    val customerNote: String?
)

enum class RefundReason {
    CUSTOMER_REQUEST,
    TECHNICAL_ISSUE,
    BILLING_ERROR,
    FRAUD_PREVENTION,
    REGULATORY_COMPLIANCE
}

enum class RefundRequestor {
    CUSTOMER,
    SUPPORT_TEAM,
    AUTOMATIC_SYSTEM
}
```

### 2. 검증 및 승인 프로세스
```kotlin
class RefundValidationService {
    fun validateRefundRequest(request: RefundRequest): RefundValidationResult {
        // 1. 거래 존재 여부 확인
        // 2. 환불 가능 기간 검증 (Google: 3년, Apple: 정책에 따라)
        // 3. 중복 환불 요청 확인
        // 4. 사업 규칙 적용
        // 5. 승인 권한 확인
    }
}
```

### 3. Platform API 호출
```kotlin
interface RefundProcessor {
    suspend fun processRefund(request: RefundRequest): RefundProcessResult
}

class GooglePlayRefundProcessor : RefundProcessor {
    override suspend fun processRefund(request: RefundRequest): RefundProcessResult {
        // orders.refund API 호출
        val response = androidPublisherService.orders().refund(
            packageName = appPackageName,
            orderId = request.transactionId
        ).setRevoke(true).execute()
        
        return RefundProcessResult(
            success = response.isSuccessful,
            refundId = generateRefundId(),
            processedAt = LocalDateTime.now()
        )
    }
}

class AppStoreRefundProcessor : RefundProcessor {
    override suspend fun processRefund(request: RefundRequest): RefundProcessResult {
        // Apple은 직접 환불 API가 없으므로 수동 처리 또는 알림 발송
        return RefundProcessResult(
            success = false,
            error = "Manual processing required via App Store Connect",
            processedAt = LocalDateTime.now()
        )
    }
}
```

### 4. 내부 시스템 업데이트
```kotlin
class RefundEventHandler {
    fun handleRefundProcessed(refund: ProcessedRefund) {
        // 1. 구독 상태 업데이트
        // 2. PaymentEvent 기록
        // 3. 정산 데이터 조정
        // 4. 고객 알림 발송
        // 5. 감사 로그 생성
    }
}
```

## Business Rules and Policies

### 환불 가능 조건
1. **시간 제한**
   - Google Play: 주문일로부터 3년 이내
   - App Store: Apple 정책에 따라 (일반적으로 90일)

2. **상태 조건**
   - 활성 구독 또는 유효한 구매
   - 이미 환불되지 않은 거래
   - 사기 거래가 아닌 정상 거래

3. **비즈니스 규칙**
   - 고객당 월 환불 한도 설정 가능
   - VIP 고객 우대 정책
   - 기술적 문제로 인한 자동 환불 승인

### 부분 환불 처리

Google Play는 새로운 subscriptionsv2.revoke API를 통해 부분 환불을 지원합니다:

**공식 문서**: [purchases.subscriptionsv2.revoke](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2/revoke)

```kotlin
data class PartialRefundRequest(
    val originalAmount: BigDecimal,
    val refundAmount: BigDecimal,
    val prorationMode: ProrationMode
)

enum class ProrationMode {
    IMMEDIATE_WITH_TIME_PRORATION,
    IMMEDIATE_WITHOUT_PRORATION,
    DEFERRED
}
```

## Data Models

### Core Refund Models

```kotlin
data class RefundTransaction(
    val id: String,
    val platform: Platform,
    val originalTransactionId: String,
    val refundTransactionId: String?,
    val amount: BigDecimal,
    val currency: String,
    val reason: RefundReason,
    val status: RefundStatus,
    val requestedAt: LocalDateTime,
    val processedAt: LocalDateTime?,
    val approvedBy: String?,
    val customerNote: String?,
    val internalNote: String?
)

enum class RefundStatus {
    REQUESTED,
    PENDING_APPROVAL,
    APPROVED,
    PROCESSING,
    COMPLETED,
    FAILED,
    REJECTED,
    CANCELLED
}
```

### Settlement Impact Tracking

```kotlin
data class RefundSettlementImpact(
    val refundId: String,
    val originalSettlementId: String,
    val adjustmentAmount: BigDecimal,
    val platformFeeAdjustment: BigDecimal,
    val netAmountAdjustment: BigDecimal,
    val processedDate: LocalDate,
    val reconciliationRequired: Boolean
)
```

## Integration Points

### 1. Webhook Handling
```kotlin
class RefundWebhookHandler {
    // Google Play RTDN에서 환불 알림 처리
    fun handleGooglePlayRefundNotification(notification: GooglePlayNotification) {
        when (notification.notificationType) {
            SUBSCRIPTION_REVOKED -> handleSubscriptionRevoked(notification)
            // 기타 환불 관련 알림 처리
        }
    }
    
    // App Store Server Notifications에서 환불 알림 처리
    fun handleAppStoreRefundNotification(notification: AppStoreNotification) {
        when (notification.notificationType) {
            REFUND -> handleRefundNotification(notification)
            // 기타 환불 관련 알림 처리
        }
    }
}
```

### 2. Customer Support Integration
```kotlin
interface CustomerSupportIntegration {
    fun createSupportTicket(refund: RefundTransaction): SupportTicket
    fun updateTicketStatus(ticketId: String, status: RefundStatus)
    fun addCustomerCommunication(ticketId: String, message: String)
}
```

### 3. Financial System Integration
```kotlin
interface FinancialSystemIntegration {
    fun recordRefundTransaction(refund: RefundTransaction)
    fun adjustRevenueRecognition(refund: RefundTransaction)
    fun updateTaxCalculations(refund: RefundTransaction)
}
```

## Error Handling and Edge Cases

### Common Error Scenarios

1. **API 호출 실패**
   - 네트워크 타임아웃
   - 인증 오류
   - 서비스 일시 중단

2. **비즈니스 로직 오류**
   - 이미 환불된 거래
   - 환불 불가능한 상태
   - 권한 부족

3. **데이터 일관성 문제**
   - 중복 환불 요청
   - 정산 데이터 불일치
   - 구독 상태 동기화 문제

### Retry and Recovery Strategies

```kotlin
class RefundRetryPolicy {
    fun shouldRetry(error: RefundError): Boolean {
        return when (error.type) {
            NETWORK_ERROR, TEMPORARY_SERVICE_ERROR -> true
            AUTHENTICATION_ERROR, BUSINESS_RULE_VIOLATION -> false
            else -> false
        }
    }
    
    fun calculateRetryDelay(attemptNumber: Int): Duration {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s
        return Duration.ofSeconds((2.0.pow(attemptNumber)).toLong())
    }
}
```

## Monitoring and Analytics

### Key Metrics to Track

1. **환불 비율 (Refund Rate)**
   - 플랫폼별 환불 비율
   - 상품별 환불 비율
   - 시간대별 트렌드

2. **처리 시간 (Processing Time)**
   - 요청부터 완료까지 시간
   - 플랫폼별 API 응답 시간
   - 승인 프로세스 소요 시간

3. **성공률 (Success Rate)**
   - API 호출 성공률
   - 환불 승인률
   - 시스템 처리 성공률

### Dashboard and Alerts

```kotlin
data class RefundMetrics(
    val totalRefunds: Int,
    val refundAmount: BigDecimal,
    val refundRate: Double,
    val averageProcessingTime: Duration,
    val successRate: Double,
    val platform: Platform,
    val timeFrame: TimeFrame
)
```

## Testing and Validation

### Test Scenarios

1. **정상 시나리오**
   - 유효한 구독 환불 요청
   - 부분 환불 처리
   - 즉시 해지 옵션 테스트

2. **예외 시나리오**
   - 만료된 거래 환불 시도
   - 중복 환불 요청
   - 권한 없는 환불 시도

3. **통합 테스트**
   - End-to-end 환불 프로세스
   - 플랫폼별 API 통합
   - 알림 시스템 연동

### Mock Testing

**Google Play Testing**: [Handling refund notifications](https://developer.apple.com/documentation/storekit/in-app_purchase/original_api_for_in-app_purchase/handling_refund_notifications)

**App Store Testing**: [Testing refund requests](https://developer.apple.com/documentation/storekit/testing-refund-requests)

## Security Considerations

### Authentication and Authorization
- JWT 토큰 관리 및 갱신
- API 키 보안 저장
- 역할 기반 접근 제어

### Data Protection
- 개인 정보 보호
- 환불 사유 암호화
- 감사 로그 보안

### Fraud Prevention
- 패턴 기반 사기 탐지
- 환불 남용 방지
- 자동 차단 시스템

## Compliance and Legal Requirements

### 규정 준수 사항
- 소비자 보호법 준수
- 환불 정책 투명성
- 데이터 보존 기간 준수

### 국가별 요구사항
- EU: GDPR 및 소비자 권리 지침
- 미국: 주별 소비자 보호법
- 한국: 전자상거래법 및 소비자보호법

## Implementation Roadmap

### Phase 1: Core Infrastructure
- [ ] 환불 도메인 모델 구현
- [ ] Platform API 통합
- [ ] 기본 검증 로직

### Phase 2: Business Logic
- [ ] 승인 워크플로우
- [ ] 알림 시스템
- [ ] 정산 연동

### Phase 3: Advanced Features
- [ ] 부분 환불 지원
- [ ] 자동화 규칙
- [ ] 고급 분석

### Phase 4: Optimization
- [ ] 성능 최적화
- [ ] 모니터링 고도화
- [ ] 머신러닝 기반 사기 탐지

## Related Documentation

- [Settlement Domain Model](./SETTLEMENT_DOMAIN_MODEL.md)
- [Reconciliation Guide](./RECONCILIATION_GUIDE.md)
- [Domain Model Documentation](./DOMAIN_MODEL.md)

## External References

### Google Play Official Documentation
- [orders.refund API](https://developers.google.com/android-publisher/api-ref/rest/v3/orders/refund) - 주요 환불 API
- [purchases.subscriptionsv2.revoke](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2/revoke) - 부분 환불 지원
- [Google Play Developer API](https://developers.google.com/android-publisher) - 전체 API 문서

### Apple Official Documentation  
- [Get Refund History](https://developer.apple.com/documentation/appstoreserverapi/get-refund-history) - 환불 이력 조회
- [App Store Server API](https://developer.apple.com/documentation/appstoreserverapi) - 전체 API 문서
- [Testing refund requests](https://developer.apple.com/documentation/storekit/testing-refund-requests) - 환불 테스트
- [Handling refund notifications](https://developer.apple.com/documentation/storekit/in-app_purchase/original_api_for_in-app_purchase/handling_refund_notifications) - 환불 알림 처리

### Industry Standards
- [PCI DSS Compliance](https://www.pcisecuritystandards.org/) - 결제 보안 표준
- [ISO 27001](https://www.iso.org/isoiec-27001-information-security.html) - 정보 보안 관리