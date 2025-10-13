# Reconciliation Guide

## Overview

대사(Reconciliation) 프로세스는 플랫폼 정산 데이터와 내부 결제 이벤트 데이터 간의 일치성을 검증하여 데이터 무결성을 보장하는 핵심 프로세스입니다.

## Process Architecture

```
Platform Settlement Data ──┐
                           ├─→ Reconciliation Engine ─→ Reconciliation Result
Internal Payment Events ───┘
```

## Reconciliation Models

### ReconciliationResult

대사 처리의 최종 결과를 나타내는 모델입니다.

```kotlin
data class ReconciliationResult(
    val date: LocalDate,                              // 대사 처리 일자
    val platform: Platform,                           // 대상 플랫폼
    val totalPlatformTransactions: Int,               // 플랫폼 총 거래 건수
    val totalInternalEvents: Int,                     // 내부 총 이벤트 건수
    val matchedTransactions: Int,                     // 매칭된 거래 건수
    val unmatchedPlatformTransactions: List<String>,  // 플랫폼에만 있는 거래 ID
    val unmatchedInternalEvents: List<String>,        // 내부에만 있는 이벤트 ID
    val discrepancies: List<ReconciliationDiscrepancy>, // 불일치 항목들
    val reconciliationStatus: ReconciliationStatus,   // 대사 상태
    val processedAt: LocalDateTime                    // 처리 완료 시간
)
```

### ReconciliationDiscrepancy

대사 과정에서 발견된 불일치 항목을 나타내는 모델입니다.

```kotlin
data class ReconciliationDiscrepancy(
    val transactionId: String,          // 불일치 거래 ID
    val discrepancyType: DiscrepancyType, // 불일치 타입
    val platformData: String?,          // 플랫폼 데이터
    val internalData: String?,          // 내부 데이터
    val description: String             // 불일치 설명
)
```

### DiscrepancyType

불일치 항목의 타입을 정의하는 열거형입니다.

```kotlin
enum class DiscrepancyType(val description: String) {
    MISSING_IN_PLATFORM("플랫폼 데이터 누락"),     // 내부에는 있지만 플랫폼에 없음
    MISSING_IN_INTERNAL("내부 데이터 누락"),      // 플랫폼에는 있지만 내부에 없음
    AMOUNT_MISMATCH("금액 불일치"),              // 금액이 다름
    EVENT_TYPE_MISMATCH("이벤트 타입 불일치"),    // 이벤트 타입이 다름
    TIMING_MISMATCH("시간 불일치")               // 시간 차이가 큼
}
```

### ReconciliationStatus

대사 처리의 최종 상태를 나타내는 열거형입니다.

```kotlin
enum class ReconciliationStatus(val description: String) {
    MATCHED("일치"),                    // 완전 일치
    PARTIAL_MATCH("부분 일치"),         // 대부분 일치 (95% 이상)
    MAJOR_DISCREPANCY("주요 불일치"),   // 상당한 불일치 (80-95%)
    FAILED("실패")                     // 심각한 불일치 (80% 미만)
}
```

## Reconciliation Process Flow

### 1. Data Collection
```
1. 플랫폼 정산 데이터 수집
   - Google Play: Cloud Storage CSV 파일
   - App Store: Sales Reports API
   
2. 내부 결제 이벤트 데이터 조회
   - PaymentEvent Repository에서 해당 일자 데이터 조회
```

### 2. Basic Matching (기본 매칭)
```kotlin
// Transaction ID 기준 1:1 매칭
settlementData.forEach { settlement ->
    val matchingEvent = paymentEvents.find { 
        it.id == settlement.transactionId ||
        it.id == settlement.originalTransactionId  // App Store 용
    }
    
    if (matchingEvent != null) {
        matches.add(ReconciliationMatch(settlement, matchingEvent))
    }
}
```

### 3. Advanced Matching (고급 매칭)
```kotlin
// 패턴 기반 매칭 (시간, 금액, 사용자 등)
unmatchedSettlements.forEach { settlement ->
    val candidates = unmatchedEvents.filter { event ->
        isTimeSimilar(settlement.createdAt, event.createdAt) &&
        isAmountSimilar(settlement.amount, event.amount) &&
        isUserSimilar(settlement.userId, event.userId)
    }
    
    val bestMatch = candidates.maxByOrNull { 
        calculateMatchingScore(settlement, it) 
    }
    
    if (bestMatch != null && score >= MATCHING_THRESHOLD) {
        matches.add(ReconciliationMatch(settlement, bestMatch))
    }
}
```

### 4. Pattern Matching Score Calculation
```kotlin
fun calculateMatchingScore(settlement: SettlementData, event: PaymentEvent): Double {
    var score = 0.0
    
    // 이벤트 타입 매칭 (40% 가중치)
    if (isEventTypeMatching(settlement.eventType, event.eventType)) {
        score += 0.4
    }
    
    // 시간 근접성 (30% 가중치)
    val timeDiff = abs(ChronoUnit.HOURS.between(settlement.createdAt, event.createdAt))
    if (timeDiff <= 24) {
        score += 0.3 * (1.0 - (timeDiff / 24.0))
    }
    
    // 사용자 ID 매칭 (20% 가중치)
    if (settlement.userId != null && settlement.userId == event.subscriptionId) {
        score += 0.2
    }
    
    // 제품 ID 매칭 (10% 가중치)
    if (settlement.productId.contains(event.subscriptionId ?: "")) {
        score += 0.1
    }
    
    return score
}
```

### 5. Status Determination (상태 결정)
```kotlin
fun determineReconciliationStatus(
    platformCount: Int,
    internalCount: Int,
    matchedCount: Int,
    discrepancyCount: Int
): ReconciliationStatus {
    val maxRecords = maxOf(platformCount, internalCount)
    val matchRate = matchedCount.toDouble() / maxRecords
    
    return when {
        matchRate >= 1.0 && discrepancyCount == 0 -> ReconciliationStatus.MATCHED
        matchRate >= 0.95 && discrepancyCount <= 2 -> ReconciliationStatus.PARTIAL_MATCH
        matchRate >= 0.80 -> ReconciliationStatus.MAJOR_DISCREPANCY
        else -> ReconciliationStatus.FAILED
    }
}
```

## Settlement Event Types in Detail

### PURCHASE (구매)
신규 구매 또는 첫 구독 시작을 나타냅니다.

**특징:**
- 새로운 고객의 첫 거래
- 구독 상품의 초기 결제
- 일회성 상품 구매

**플랫폼별 매핑:**
- **Google Play**: `SUBSCRIPTION_PURCHASED`, `SUBSCRIPTION_RECOVERED`
- **App Store**: `INITIAL_BUY`, `DID_RECOVER`

```kotlin
// Example: Google Play 신규 구독
SettlementData(
    eventType = SettlementEventType.PURCHASE,
    amount = BigDecimal("9.99"),
    platformFee = BigDecimal("2.99"),
    netAmount = BigDecimal("7.00")
)
```

### RENEWAL (갱신)
기존 구독의 자동 갱신을 나타냅니다.

**특징:**
- 구독 서비스의 정기 결제
- 자동 갱신 시스템에 의한 거래
- 기존 고객의 지속적인 수익

**플랫폼별 매핑:**
- **Google Play**: `SUBSCRIPTION_RENEWED`
- **App Store**: `DID_RENEW`

### REFUND (환불)
고객 요청 또는 플랫폼 정책에 의한 환불을 나타냅니다.

**특징:**
- 고객 불만족으로 인한 환불
- 플랫폼 정책 위반으로 인한 강제 환불
- 중복 결제 환불

**재무적 영향:**
- ❌ 매출 감소 (음수 금액)
- ❌ 순 매출 감소
- ✅ 플랫폼 수수료 환급 (일반적으로)

```kotlin
// Example: Google Play 환불
SettlementData(
    eventType = SettlementEventType.REFUND,
    amount = BigDecimal("-9.99"), // 음수 금액
    platformFee = BigDecimal("0.00"), // 수수료 환급
    netAmount = BigDecimal("-9.99")
)
```

### CHARGEBACK (차지백)
신용카드 회사를 통한 강제 환불을 나타냅니다.

**특징:**
- 신용카드 분쟁으로 인한 강제 환불
- 사기 거래로 판명된 경우
- 플랫폼이 아닌 금융기관에서 처리

**플랫폼별 매핑:**
- **Google Play**: `SUBSCRIPTION_REVOKED`
- **App Store**: Chargeback 알림 (별도 처리)

## Platform-Specific Event Mapping

### Google Play Event Mapping
```kotlin
fun mapGooglePlayEvent(notificationType: Int): SettlementEventType? {
    return when (notificationType) {
        1 -> SettlementEventType.PURCHASE    // SUBSCRIPTION_RECOVERED
        2 -> SettlementEventType.RENEWAL     // SUBSCRIPTION_RENEWED
        3 -> null                            // SUBSCRIPTION_CANCELED (no settlement)
        4 -> SettlementEventType.PURCHASE    // SUBSCRIPTION_PURCHASED
        12 -> SettlementEventType.CHARGEBACK // SUBSCRIPTION_REVOKED
        else -> null
    }
}
```

### App Store Event Mapping
```kotlin
fun mapAppStoreEvent(notificationType: String): SettlementEventType? {
    return when (notificationType) {
        "INITIAL_BUY" -> SettlementEventType.PURCHASE
        "DID_RENEW" -> SettlementEventType.RENEWAL
        "DID_RECOVER" -> SettlementEventType.PURCHASE
        "REFUND" -> SettlementEventType.REFUND
        else -> null
    }
}
```

## Error Scenarios & Resolution

### Missing Data
```kotlin
// 플랫폼에만 있는 거래 (내부 누락)
if (unmatchedPlatformTransactions.isNotEmpty()) {
    alert("Internal payment event missing", unmatchedPlatformTransactions)
}

// 내부에만 있는 이벤트 (플랫폼 누락)
if (unmatchedInternalEvents.isNotEmpty()) {
    alert("Platform settlement data missing", unmatchedInternalEvents)
}
```

### Automated Resolution

일부 불일치는 자동으로 해결할 수 있습니다:

#### Time Tolerance
```kotlin
// 24시간 이내 시간 차이는 허용
if (timeDifference <= 24.hours && timeDifference <= ACCEPTABLE_TIME_TOLERANCE) {
    resolveAutomatically("Time difference within acceptable range")
}
```

#### Currency Conversion
```kotlin
// 환율 변동으로 인한 소액 차이 허용
if (amountDifference <= CURRENCY_FLUCTUATION_TOLERANCE) {
    resolveAutomatically("Amount difference within currency fluctuation tolerance")
}
```

## Monitoring & Alerting

### Alert Triggers
- **CRITICAL**: ReconciliationStatus.FAILED
- **HIGH**: Major discrepancy 또는 높은 불일치 건수
- **MEDIUM**: Partial match with notable discrepancies
- **LOW**: Successful reconciliation with minor issues

### Performance Metrics
- 대사 처리 성공률 모니터링
- 불일치 발생 패턴 추적
- 처리 시간 성능 측정

## Best Practices

1. **Daily Reconciliation**: 매일 정기적인 대사 실행
2. **Incremental Processing**: 점진적 데이터 처리로 성능 최적화
3. **Error Handling**: 실패한 대사의 수동 처리 큐 관리
4. **Audit Trail**: 모든 대사 과정 로그 보존
5. **Performance Optimization**: 대용량 데이터 처리 최적화

## Validation Examples

### Event Type Validation
```kotlin
fun validateEventType(settlement: SettlementData): List<String> {
    val errors = mutableListOf<String>()
    
    when (settlement.eventType) {
        SettlementEventType.PURCHASE, SettlementEventType.RENEWAL -> {
            if (settlement.amount <= BigDecimal.ZERO) {
                errors.add("Purchase/Renewal amount must be positive")
            }
        }
        
        SettlementEventType.REFUND, SettlementEventType.CHARGEBACK -> {
            if (settlement.amount >= BigDecimal.ZERO) {
                errors.add("Refund/Chargeback amount must be negative")
            }
        }
    }
    
    return errors
}
```

### Platform-Specific Validation
```kotlin
fun validatePlatformSpecificRules(settlement: SettlementData): List<String> {
    val errors = mutableListOf<String>()
    
    when (settlement.platform) {
        Platform.IOS -> {
            // App Store: 갱신/환불은 originalTransactionId 필요
            if (settlement.eventType in listOf(
                SettlementEventType.RENEWAL, 
                SettlementEventType.REFUND
            ) && settlement.originalTransactionId == null) {
                errors.add("iOS renewal/refund requires originalTransactionId")
            }
        }
    }
    
    return errors
}
```