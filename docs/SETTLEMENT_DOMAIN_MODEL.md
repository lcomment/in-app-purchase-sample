# Settlement Domain Model Documentation

## Overview

정산 도메인 모델은 Google Play와 App Store에서 제공하는 정산 데이터를 처리하고, 내부 결제 이벤트와 대사(reconciliation)하여 일치성을 검증하는 핵심 도메인입니다.

## Architecture

```
com.example.domain.settlement/
├── SettlementData.kt           # 정산 데이터 핵심 모델
├── SettlementEventType.kt      # 정산 이벤트 타입 정의
├── DailySettlementSummary.kt   # 일일 정산 요약
└── reconciliation/             # 대사 처리 관련 모델
    ├── ReconciliationResult.kt      # 대사 처리 결과
    ├── ReconciliationDiscrepancy.kt # 불일치 항목
    ├── DiscrepancyType.kt          # 불일치 타입
    └── ReconciliationStatus.kt     # 대사 상태
```

## Core Concepts

### 1. Settlement Data (정산 데이터)
플랫폼에서 제공하는 실제 정산 정보로, 매출과 수수료가 계산된 최종 데이터입니다.

### 2. Reconciliation (대사)
플랫폼 정산 데이터와 내부 결제 이벤트 데이터 간의 일치성을 검증하는 프로세스입니다.

### 3. Settlement Event Types
정산에서 처리하는 다양한 이벤트 타입들을 정의합니다.

## Key Features

- **Multi-Platform Support**: Google Play와 App Store 정산 데이터 통합 처리
- **Automated Reconciliation**: 자동 대사 처리 및 불일치 탐지
- **Financial Accuracy**: 정확한 매출 및 수수료 계산
- **Audit Trail**: 완전한 감사 추적 기능

## Related Documentation

- [Reconciliation Guide](./RECONCILIATION_GUIDE.md) - 대사 처리 프로세스 및 이벤트 타입 가이드

## Domain Models

### SettlementData

정산 데이터의 핵심 모델로, 플랫폼에서 제공하는 정산 정보를 표현합니다.

```kotlin
data class SettlementData(
    val id: String,                        // 고유 정산 ID
    val platform: Platform,                // 플랫폼 (AOS/IOS)
    val settlementDate: LocalDate,         // 정산 일자
    val transactionId: String,             // 거래 ID
    val originalTransactionId: String?,    // 원본 거래 ID (App Store)
    val productId: String,                 // 상품 ID
    val subscriptionId: String?,           // 구독 ID
    val eventType: SettlementEventType,    // 정산 이벤트 타입
    val amount: BigDecimal,                // 총 금액
    val currency: String,                  // 통화
    val platformFee: BigDecimal,           // 플랫폼 수수료
    val netAmount: BigDecimal,             // 순 금액
    val taxAmount: BigDecimal?,            // 세금 (선택)
    val userId: String?,                   // 사용자 ID (선택)
    val countryCode: String?,              // 국가 코드 (선택)
    val createdAt: LocalDateTime,          // 생성 시간
    val platformSettlementId: String?      // 플랫폼별 고유 정산 ID
)
```

#### Field Descriptions

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | String | ✅ | 시스템 내부 고유 정산 ID |
| `platform` | Platform | ✅ | 플랫폼 구분 (AOS/IOS) |
| `settlementDate` | LocalDate | ✅ | 정산이 발생한 날짜 |
| `transactionId` | String | ✅ | 플랫폼에서 제공하는 거래 ID |
| `originalTransactionId` | String? | ❌ | App Store 전용: 원본 거래 ID |
| `productId` | String | ✅ | 구매한 상품의 ID |
| `eventType` | SettlementEventType | ✅ | 정산 이벤트 타입 |
| `amount` | BigDecimal | ✅ | 총 거래 금액 (VAT 포함) |
| `currency` | String | ✅ | ISO 4217 통화 코드 |
| `platformFee` | BigDecimal | ✅ | 플랫폼 수수료 |
| `netAmount` | BigDecimal | ✅ | 개발자에게 지급되는 순 금액 |

### DailySettlementSummary

일일 정산 데이터의 요약 정보를 제공하는 모델입니다.

```kotlin
data class DailySettlementSummary(
    val date: LocalDate,                   // 정산 일자
    val platform: Platform,                // 플랫폼
    val totalTransactions: Int,            // 총 거래 건수
    val totalGrossAmount: BigDecimal,      // 총 매출 (수수료 제외)
    val totalPlatformFee: BigDecimal,      // 총 플랫폼 수수료
    val totalNetAmount: BigDecimal,        // 총 순 매출
    val totalTaxAmount: BigDecimal,        // 총 세금
    val purchaseCount: Int,                // 신규 구매 건수
    val renewalCount: Int,                 // 갱신 건수
    val refundCount: Int,                  // 환불 건수
    val chargebackCount: Int,              // 차지백 건수
    val createdAt: LocalDateTime           // 생성 시간
)
```

### SettlementEventType

정산에서 처리하는 이벤트 타입을 정의하는 열거형입니다.

```kotlin
enum class SettlementEventType(val description: String) {
    PURCHASE("구매"),              // 신규 구매
    RENEWAL("갱신"),               // 구독 갱신
    REFUND("환불"),                // 환불 처리
    CHARGEBACK("차지백"),          // 차지백 발생
    TAX_ADJUSTMENT("세금 조정"),    // 세금 조정
    FEE_ADJUSTMENT("수수료 조정")   // 수수료 조정
}
```

#### Event Type Financial Impact

| Event Type | Revenue Impact | Platform Fee | Net Revenue | Notes |
|------------|----------------|--------------|-------------|-------|
| PURCHASE | ➕ Positive | ➕ Charged | ➕ Positive | New revenue |
| RENEWAL | ➕ Positive | ➕ Charged | ➕ Positive | Recurring revenue |
| REFUND | ➖ Negative | ➖ Refunded | ➖ Negative | Revenue reversal |
| CHARGEBACK | ➖ Negative | ➖ Lost | ➖ Negative | Forced reversal |
| TAX_ADJUSTMENT | ±️ Variable | ➖ No change | ±️ Variable | Tax correction |
| FEE_ADJUSTMENT | ➖ No change | ±️ Variable | ±️ Variable | Fee correction |

## Platform-Specific Considerations

### Google Play (AOS)
- `transactionId`: Order ID 또는 Purchase Token
- `originalTransactionId`: 일반적으로 null
- `platformSettlementId`: Google Play Console의 Settlement ID
- **Data Source**: Cloud Storage CSV 파일, Play Developer Reporting API

### App Store (IOS)
- `transactionId`: Transaction ID
- `originalTransactionId`: Original Transaction ID (갱신/환불의 경우 필수)
- `platformSettlementId`: App Store Connect의 Settlement ID
- **Data Source**: App Store Connect API, Reporter Tool

## Usage Examples

### Basic Settlement Data Processing
```kotlin
val settlementData = SettlementData(
    id = "settlement_001",
    platform = Platform.AOS,
    settlementDate = LocalDate.now(),
    transactionId = "txn_123",
    eventType = SettlementEventType.PURCHASE,
    amount = BigDecimal("9.99"),
    currency = "USD",
    platformFee = BigDecimal("2.99"),
    netAmount = BigDecimal("7.00")
)
```

### Daily Settlement Summary
```kotlin
val summary = DailySettlementSummary(
    date = LocalDate.now(),
    platform = Platform.IOS,
    totalTransactions = 150,
    totalGrossAmount = BigDecimal("1499.00"),
    totalPlatformFee = BigDecimal("449.70"),
    totalNetAmount = BigDecimal("1049.30"),
    purchaseCount = 100,
    renewalCount = 50
)
```

## Business Rules

1. **Data Integrity**: 모든 정산 데이터는 플랫폼에서 제공하는 원본 데이터를 기준으로 합니다.
2. **Currency Consistency**: 모든 금액은 플랫폼 기준 통화로 정규화됩니다.
3. **Event Tracking**: 모든 정산 이벤트는 추적 가능한 ID를 가집니다.
4. **Reconciliation**: 일일 대사는 필수이며, 불일치 발생 시 알림이 발송됩니다.

## Data Flow

```
Platform Settlement Data → Settlement Processing → Reconciliation → Financial Reporting
                                    ↓
                            Internal Payment Events
```

## Validation Rules

- **Required Fields**: platform, settlementDate, transactionId, eventType, amount
- **Amount Validation**: amount ≥ 0 (except for refunds)
- **Date Validation**: settlementDate ≤ current date
- **Platform Consistency**: 플랫폼별 필수 필드 검증

## Error Handling

- **Data Quality Issues**: 잘못된 정산 데이터 감지 및 격리
- **Reconciliation Failures**: 대사 실패 시 수동 처리 큐 이동
- **Platform API Errors**: 플랫폼 API 오류 시 재시도 로직

## Monitoring & Alerts

- **Real-time Monitoring**: 정산 데이터 수집 상태 실시간 모니터링
- **Discrepancy Alerts**: 불일치 발생 시 즉시 알림
- **Performance Metrics**: 처리 성능 및 정확도 지표 추적