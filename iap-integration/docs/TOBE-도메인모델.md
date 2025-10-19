# TOBE 도메인 모델 설계 - Payment/Wallet/Ledger 분리

이 문서는 현재 단순한 구독 시스템에서 복잡한 금융 거래 시스템으로 확장할 때의 도메인 모델 설계를 제시합니다.

## 📋 설계 원칙

### 1. 관심사 분리 (Separation of Concerns)
- **Payment**: 외부 플랫폼과의 결제 처리
- **Wallet**: 사용자 자산 보유 상태
- **Ledger**: 모든 금융 거래의 불변 기록

### 2. 이벤트 소싱 (Event Sourcing)
- 모든 금융 거래를 이벤트로 기록
- 잔액은 이벤트 스트림에서 계산

### 3. 복식부기 원칙 (Double-Entry Bookkeeping)
- 모든 거래는 차변(Debit)과 대변(Credit)으로 구성
- 시스템 전체 잔액 합계는 항상 0

---

## 🏦 1. Payment Domain (결제 도메인)

### Payment (결제)
**책임**: 외부 플랫폼(Google Play, App Store)과의 결제 처리

```kotlin
data class Payment(
    val id: PaymentId,
    val externalPaymentId: String,          // 플랫폼 고유 ID
    val platform: Platform,
    val userId: UserId,
    val productId: String,
    val purchaseToken: String,
    val amount: Money,
    val status: PaymentStatus,
    val paymentMethod: PaymentMethod,       // GOOGLE_PLAY, APP_STORE, CREDIT_CARD
    val paymentDate: LocalDateTime,
    val acknowledgmentState: Boolean = false,
    val metadata: PaymentMetadata,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun acknowledge(): Payment = copy(acknowledgmentState = true)
    fun fail(reason: String): Payment = copy(status = PaymentStatus.FAILED)
    fun isSuccessful(): Boolean = status == PaymentStatus.SUCCESS
}

enum class PaymentStatus {
    PENDING,        // 결제 요청 중
    SUCCESS,        // 결제 성공
    FAILED,         // 결제 실패
    REFUNDED,       // 환불됨
    CANCELLED       // 취소됨
}

enum class PaymentMethod {
    GOOGLE_PLAY,
    APP_STORE,
    CREDIT_CARD,
    BANK_TRANSFER,
    INTERNAL_WALLET  // 내부 지갑 결제
}
```

### PaymentMetadata (결제 메타데이터)
```kotlin
data class PaymentMetadata(
    val orderId: String?,
    val transactionId: String?,
    val receiptData: String?,
    val platformFee: Money?,
    val netAmount: Money,               // 수수료 차감 후 금액
    val exchangeRate: BigDecimal? = null,
    val originalCurrency: String? = null
)
```

---

## 💰 2. Wallet Domain (지갑 도메인)

### Wallet (지갑)
**책임**: 사용자의 가상 자산 보유 상태 관리

```kotlin
data class Wallet(
    val id: WalletId,
    val userId: UserId,
    val type: WalletType,
    val balances: Map<Currency, Balance>,    // 다중 통화 지원
    val status: WalletStatus,
    val limits: WalletLimits,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    fun getBalance(currency: Currency): Balance = 
        balances[currency] ?: Balance.zero(currency)
    
    fun canDebit(amount: Money): Boolean = 
        getBalance(amount.currency).amount >= amount.amount
    
    fun isActive(): Boolean = status == WalletStatus.ACTIVE
    
    companion object {
        fun createForUser(userId: UserId): Wallet = Wallet(
            id = WalletId.generate(),
            userId = userId,
            type = WalletType.PERSONAL,
            balances = mapOf(
                Currency.POINT to Balance.zero(Currency.POINT),
                Currency.CREDIT to Balance.zero(Currency.CREDIT),
                Currency.USD to Balance.zero(Currency.USD)
            ),
            status = WalletStatus.ACTIVE,
            limits = WalletLimits.default(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
}

enum class WalletType {
    PERSONAL,       // 개인 지갑
    BUSINESS,       // 비즈니스 지갑
    SYSTEM,         // 시스템 지갑 (수수료, 적립금 등)
    ESCROW          // 에스크로 지갑
}

enum class WalletStatus {
    ACTIVE,         // 활성
    SUSPENDED,      // 일시정지
    FROZEN,         // 동결
    CLOSED          // 폐쇄
}
```

### Balance (잔액)
```kotlin
data class Balance(
    val currency: Currency,
    val amount: BigDecimal,
    val reservedAmount: BigDecimal = BigDecimal.ZERO,  // 예약된 금액
    val lastUpdated: LocalDateTime = LocalDateTime.now()
) {
    val availableAmount: BigDecimal get() = amount - reservedAmount
    
    fun credit(amount: BigDecimal): Balance = 
        copy(amount = this.amount + amount, lastUpdated = LocalDateTime.now())
    
    fun debit(amount: BigDecimal): Balance = 
        copy(amount = this.amount - amount, lastUpdated = LocalDateTime.now())
    
    fun reserve(amount: BigDecimal): Balance = 
        copy(reservedAmount = this.reservedAmount + amount, lastUpdated = LocalDateTime.now())
    
    companion object {
        fun zero(currency: Currency): Balance = Balance(currency, BigDecimal.ZERO)
    }
}

enum class Currency {
    USD,            // 실제 화폐
    KRW,            // 실제 화폐
    POINT,          // 포인트
    CREDIT,         // 크레딧
    COIN            // 코인
}
```

### WalletLimits (지갑 한도)
```kotlin
data class WalletLimits(
    val dailySpendLimit: Map<Currency, BigDecimal>,
    val monthlySpendLimit: Map<Currency, BigDecimal>,
    val maxBalance: Map<Currency, BigDecimal>,
    val minWithdrawalAmount: Map<Currency, BigDecimal>
) {
    companion object {
        fun default(): WalletLimits = WalletLimits(
            dailySpendLimit = mapOf(
                Currency.POINT to BigDecimal("10000"),
                Currency.CREDIT to BigDecimal("5000")
            ),
            monthlySpendLimit = mapOf(
                Currency.POINT to BigDecimal("100000"),
                Currency.CREDIT to BigDecimal("50000")
            ),
            maxBalance = mapOf(
                Currency.POINT to BigDecimal("1000000"),
                Currency.CREDIT to BigDecimal("500000")
            ),
            minWithdrawalAmount = mapOf(
                Currency.USD to BigDecimal("1.00"),
                Currency.KRW to BigDecimal("1000")
            )
        )
    }
}
```

---

## 📚 3. Ledger Domain (원장 도메인)

### LedgerEntry (원장 기록)
**책임**: 모든 금융 거래의 불변 기록 보관

```kotlin
data class LedgerEntry(
    val id: LedgerEntryId,
    val transactionId: TransactionId,
    val accountId: AccountId,               // 계정 ID (지갑 ID와 매핑)
    val entryType: EntryType,
    val amount: Money,
    val balanceAfter: Money,                // 거래 후 잔액
    val description: String,
    val reference: TransactionReference,     // 관련 거래 참조
    val timestamp: LocalDateTime,
    val metadata: LedgerMetadata
) {
    fun isDebit(): Boolean = entryType == EntryType.DEBIT
    fun isCredit(): Boolean = entryType == EntryType.CREDIT
}

enum class EntryType {
    DEBIT,          // 차변 (자산 증가, 부채 감소)
    CREDIT          // 대변 (자산 감소, 부채 증가)
}
```

### Transaction (거래)
**책임**: 복식부기 거래의 논리적 단위

```kotlin
data class Transaction(
    val id: TransactionId,
    val type: TransactionType,
    val description: String,
    val entries: List<LedgerEntry>,         // 최소 2개 (복식부기)
    val status: TransactionStatus,
    val initiatedBy: UserId,
    val approvedBy: UserId? = null,
    val timestamp: LocalDateTime,
    val metadata: TransactionMetadata
) {
    init {
        require(entries.size >= 2) { "Transaction must have at least 2 entries" }
        require(isBalanced()) { "Transaction must be balanced (sum of debits = sum of credits)" }
    }
    
    private fun isBalanced(): Boolean {
        val debits = entries.filter { it.isDebit() }.sumOf { it.amount.amount }
        val credits = entries.filter { it.isCredit() }.sumOf { it.amount.amount }
        return debits == credits
    }
    
    fun getTotalAmount(): Money = entries.first().amount
}

enum class TransactionType {
    // 결제 관련
    PAYMENT_RECEIVED,           // 결제 수신
    PAYMENT_REFUND,            // 결제 환불
    
    // 구독 관련
    SUBSCRIPTION_PURCHASE,      // 구독 구매
    SUBSCRIPTION_RENEWAL,       // 구독 갱신
    SUBSCRIPTION_UPGRADE,       // 구독 업그레이드
    SUBSCRIPTION_DOWNGRADE,     // 구독 다운그레이드
    
    // 포인트/크레딧 관련
    POINT_EARNED,              // 포인트 적립
    POINT_SPENT,               // 포인트 사용
    CREDIT_PURCHASED,          // 크레딧 구매
    CREDIT_SPENT,              // 크레딧 사용
    CREDIT_EXPIRED,            // 크레딧 만료
    
    // 프로모션 관련
    BONUS_GRANTED,             // 보너스 지급
    COUPON_REDEEMED,           // 쿠폰 사용
    CASHBACK_EARNED,           // 캐시백 적립
    
    // 시스템 관련
    ADJUSTMENT,                // 조정
    COMMISSION_FEE,            // 수수료
    CURRENCY_EXCHANGE,         // 환전
    
    // 출금/입금
    WITHDRAWAL,                // 출금
    DEPOSIT,                   // 입금
    
    // 내부 이체
    INTERNAL_TRANSFER          // 내부 이체
}

enum class TransactionStatus {
    PENDING,            // 처리 대기
    PROCESSING,         // 처리 중
    COMPLETED,          // 완료
    FAILED,             // 실패
    CANCELLED,          // 취소
    REVERSED            // 취소됨
}
```

### Account (계정)
**책임**: 복식부기를 위한 계정 체계

```kotlin
data class Account(
    val id: AccountId,
    val code: String,                   // 계정 코드 (예: 1100, 2100)
    val name: String,                   // 계정명
    val type: AccountType,
    val parentAccountId: AccountId? = null,
    val isActive: Boolean = true,
    val metadata: AccountMetadata
) {
    fun isAsset(): Boolean = type == AccountType.ASSET
    fun isLiability(): Boolean = type == AccountType.LIABILITY
    fun isRevenue(): Boolean = type == AccountType.REVENUE
    fun isExpense(): Boolean = type == AccountType.EXPENSE
}

enum class AccountType {
    ASSET,              // 자산 (사용자 지갑, 현금 등)
    LIABILITY,          // 부채 (미지급금, 예치금 등)
    EQUITY,             // 자본 (자본금 등)
    REVENUE,            // 수익 (구독료 수익, 수수료 수익 등)
    EXPENSE             // 비용 (플랫폼 수수료, 운영비 등)
}
```

---

## 🔄 4. Integration Domain (통합 도메인)

### WalletTransaction (지갑 거래)
**책임**: Payment, Wallet, Ledger 간의 통합 처리

```kotlin
data class WalletTransaction(
    val id: WalletTransactionId,
    val paymentId: PaymentId? = null,       // 외부 결제와 연결
    val fromWalletId: WalletId? = null,
    val toWalletId: WalletId? = null,
    val amount: Money,
    val type: WalletTransactionType,
    val description: String,
    val status: WalletTransactionStatus,
    val ledgerTransactionId: TransactionId, // 원장 거래와 연결
    val timestamp: LocalDateTime,
    val metadata: Map<String, Any> = emptyMap()
) {
    fun isTransfer(): Boolean = fromWalletId != null && toWalletId != null
    fun isTopUp(): Boolean = fromWalletId == null && toWalletId != null
    fun isWithdrawal(): Boolean = fromWalletId != null && toWalletId == null
}

enum class WalletTransactionType {
    // 입금/충전
    TOP_UP_FROM_PAYMENT,        // 결제를 통한 충전
    TOP_UP_FROM_BONUS,          // 보너스 지급
    TOP_UP_FROM_REFUND,         // 환불을 통한 충전
    
    // 출금/사용
    SPEND_FOR_SUBSCRIPTION,     // 구독 결제
    SPEND_FOR_CONTENT,          // 콘텐츠 구매
    WITHDRAWAL_TO_BANK,         // 은행 출금
    
    // 이체
    TRANSFER_BETWEEN_WALLETS,   // 지갑 간 이체
    TRANSFER_TO_SYSTEM,         // 시스템 계정으로 이체
    
    // 조정
    ADJUSTMENT_CREDIT,          // 조정 입금
    ADJUSTMENT_DEBIT,           // 조정 출금
    
    // 만료
    EXPIRY_DEDUCTION            // 만료로 인한 차감
}

enum class WalletTransactionStatus {
    PENDING,            // 대기 중
    PROCESSING,         // 처리 중
    COMPLETED,          // 완료
    FAILED,             // 실패
    CANCELLED           // 취소
}
```

---

## 💼 5. Business Services (비즈니스 서비스)

### PaymentToWalletService
**책임**: 결제 성공 시 지갑 충전 처리

```kotlin
@Service
class PaymentToWalletService(
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val walletTransactionRepository: WalletTransactionRepository
) {
    
    @Transactional
    fun processPaymentSuccess(payment: Payment): WalletTransaction {
        // 1. 사용자 지갑 조회
        val wallet = walletRepository.findByUserId(payment.userId)
            ?: throw WalletNotFoundException(payment.userId)
        
        // 2. 충전할 금액 계산 (포인트 적립 비율 적용)
        val pointsToAdd = calculatePointsFromPayment(payment)
        
        // 3. 지갑 거래 생성
        val walletTransaction = WalletTransaction(
            id = WalletTransactionId.generate(),
            paymentId = payment.id,
            toWalletId = wallet.id,
            amount = pointsToAdd,
            type = WalletTransactionType.TOP_UP_FROM_PAYMENT,
            description = "Payment ${payment.id} - Points earned",
            status = WalletTransactionStatus.PROCESSING,
            ledgerTransactionId = TransactionId.generate(),
            timestamp = LocalDateTime.now()
        )
        
        // 4. 원장 거래 생성 (복식부기)
        val ledgerTransaction = createLedgerTransaction(walletTransaction, wallet)
        
        // 5. 지갑 잔액 업데이트
        val updatedWallet = wallet.copy(
            balances = wallet.balances + (pointsToAdd.currency to 
                wallet.getBalance(pointsToAdd.currency).credit(pointsToAdd.amount)),
            updatedAt = LocalDateTime.now()
        )
        
        // 6. 저장
        ledgerService.save(ledgerTransaction)
        walletRepository.save(updatedWallet)
        return walletTransactionRepository.save(
            walletTransaction.copy(status = WalletTransactionStatus.COMPLETED)
        )
    }
    
    private fun calculatePointsFromPayment(payment: Payment): Money {
        // 결제 금액의 10% 포인트 적립
        val pointAmount = payment.amount.amount.multiply(BigDecimal("0.1"))
        return Money(Currency.POINT, pointAmount)
    }
    
    private fun createLedgerTransaction(
        walletTransaction: WalletTransaction, 
        wallet: Wallet
    ): Transaction {
        // 복식부기: 사용자 포인트 자산 증가 = 회사 포인트 부채 증가
        val userPointsAccount = AccountId.userPoints(wallet.userId)
        val companyPointsLiabilityAccount = AccountId.companyPointsLiability()
        
        return Transaction(
            id = walletTransaction.ledgerTransactionId,
            type = TransactionType.POINT_EARNED,
            description = "Points earned from payment ${walletTransaction.paymentId}",
            entries = listOf(
                LedgerEntry(
                    id = LedgerEntryId.generate(),
                    transactionId = walletTransaction.ledgerTransactionId,
                    accountId = userPointsAccount,
                    entryType = EntryType.DEBIT,  // 자산 증가
                    amount = walletTransaction.amount,
                    balanceAfter = wallet.getBalance(walletTransaction.amount.currency)
                        .credit(walletTransaction.amount.amount).let { 
                            Money(it.currency, it.amount) 
                        },
                    description = "User points earned",
                    reference = TransactionReference.payment(walletTransaction.paymentId!!),
                    timestamp = walletTransaction.timestamp,
                    metadata = LedgerMetadata.empty()
                ),
                LedgerEntry(
                    id = LedgerEntryId.generate(),
                    transactionId = walletTransaction.ledgerTransactionId,
                    accountId = companyPointsLiabilityAccount,
                    entryType = EntryType.CREDIT, // 부채 증가
                    amount = walletTransaction.amount,
                    balanceAfter = Money(walletTransaction.amount.currency, BigDecimal.ZERO), // 계산 필요
                    description = "Company points liability",
                    reference = TransactionReference.payment(walletTransaction.paymentId!!),
                    timestamp = walletTransaction.timestamp,
                    metadata = LedgerMetadata.empty()
                )
            ),
            status = TransactionStatus.COMPLETED,
            initiatedBy = wallet.userId,
            timestamp = walletTransaction.timestamp,
            metadata = TransactionMetadata.empty()
        )
    }
}
```

### SubscriptionPurchaseService
**책임**: 구독 구매 시 지갑에서 차감 처리

```kotlin
@Service
class SubscriptionPurchaseService(
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val memberSubscriptionService: MemberSubscriptionService
) {
    
    @Transactional
    fun purchaseSubscriptionWithWallet(
        userId: UserId,
        subscriptionId: Long,
        paymentCurrency: Currency
    ): WalletTransaction {
        
        // 1. 구독 정보 조회
        val subscription = subscriptionService.findById(subscriptionId)
        val subscriptionPrice = Money(paymentCurrency, subscription.pricePerMonth.toBigDecimal())
        
        // 2. 사용자 지갑 조회 및 잔액 확인
        val wallet = walletRepository.findByUserId(userId)
            ?: throw WalletNotFoundException(userId)
        
        if (!wallet.canDebit(subscriptionPrice)) {
            throw InsufficientBalanceException(wallet.id, subscriptionPrice)
        }
        
        // 3. 지갑 거래 생성
        val walletTransaction = WalletTransaction(
            id = WalletTransactionId.generate(),
            fromWalletId = wallet.id,
            amount = subscriptionPrice,
            type = WalletTransactionType.SPEND_FOR_SUBSCRIPTION,
            description = "Subscription purchase: ${subscription.name}",
            status = WalletTransactionStatus.PROCESSING,
            ledgerTransactionId = TransactionId.generate(),
            timestamp = LocalDateTime.now(),
            metadata = mapOf("subscriptionId" to subscriptionId)
        )
        
        // 4. 원장 거래 생성
        val ledgerTransaction = createSubscriptionPurchaseLedgerTransaction(
            walletTransaction, wallet, subscription
        )
        
        // 5. 지갑 잔액 차감
        val updatedWallet = wallet.copy(
            balances = wallet.balances + (subscriptionPrice.currency to 
                wallet.getBalance(subscriptionPrice.currency).debit(subscriptionPrice.amount)),
            updatedAt = LocalDateTime.now()
        )
        
        // 6. 구독 활성화
        memberSubscriptionService.createSubscription(userId, subscription, walletTransaction.id)
        
        // 7. 저장
        ledgerService.save(ledgerTransaction)
        walletRepository.save(updatedWallet)
        return walletTransactionRepository.save(
            walletTransaction.copy(status = WalletTransactionStatus.COMPLETED)
        )
    }
    
    private fun createSubscriptionPurchaseLedgerTransaction(
        walletTransaction: WalletTransaction,
        wallet: Wallet,
        subscription: Subscription
    ): Transaction {
        // 복식부기: 사용자 자산 감소 = 회사 수익 증가
        val userWalletAccount = AccountId.userWallet(wallet.userId, walletTransaction.amount.currency)
        val subscriptionRevenueAccount = AccountId.subscriptionRevenue()
        
        return Transaction(
            id = walletTransaction.ledgerTransactionId,
            type = TransactionType.SUBSCRIPTION_PURCHASE,
            description = "Subscription purchase: ${subscription.name}",
            entries = listOf(
                LedgerEntry(
                    id = LedgerEntryId.generate(),
                    transactionId = walletTransaction.ledgerTransactionId,
                    accountId = subscriptionRevenueAccount,
                    entryType = EntryType.CREDIT, // 수익 증가
                    amount = walletTransaction.amount,
                    balanceAfter = Money(walletTransaction.amount.currency, BigDecimal.ZERO), // 계산 필요
                    description = "Subscription revenue",
                    reference = TransactionReference.subscription(subscription.id),
                    timestamp = walletTransaction.timestamp,
                    metadata = LedgerMetadata.subscription(subscription.id)
                ),
                LedgerEntry(
                    id = LedgerEntryId.generate(),
                    transactionId = walletTransaction.ledgerTransactionId,
                    accountId = userWalletAccount,
                    entryType = EntryType.DEBIT, // 자산 감소
                    amount = walletTransaction.amount,
                    balanceAfter = wallet.getBalance(walletTransaction.amount.currency)
                        .debit(walletTransaction.amount.amount).let {
                            Money(it.currency, it.amount)
                        },
                    description = "User wallet debit for subscription",
                    reference = TransactionReference.subscription(subscription.id),
                    timestamp = walletTransaction.timestamp,
                    metadata = LedgerMetadata.user(wallet.userId)
                )
            ),
            status = TransactionStatus.COMPLETED,
            initiatedBy = wallet.userId,
            timestamp = walletTransaction.timestamp,
            metadata = TransactionMetadata.subscription(subscription.id)
        )
    }
}
```

---

## 📊 6. Value Objects & Supporting Types

### Money (화폐)
```kotlin
data class Money(
    val currency: Currency,
    val amount: BigDecimal
) {
    init {
        require(amount >= BigDecimal.ZERO) { "Amount cannot be negative" }
    }
    
    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot add different currencies" }
        return Money(currency, amount + other.amount)
    }
    
    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Cannot subtract different currencies" }
        require(amount >= other.amount) { "Insufficient amount" }
        return Money(currency, amount - other.amount)
    }
    
    operator fun times(multiplier: BigDecimal): Money {
        return Money(currency, amount * multiplier)
    }
    
    fun isZero(): Boolean = amount == BigDecimal.ZERO
    fun isPositive(): Boolean = amount > BigDecimal.ZERO
    
    companion object {
        fun zero(currency: Currency): Money = Money(currency, BigDecimal.ZERO)
        fun of(currency: Currency, amount: String): Money = Money(currency, BigDecimal(amount))
    }
}
```

### ID 타입들
```kotlin
@JvmInline
value class PaymentId(val value: String) {
    companion object {
        fun generate(): PaymentId = PaymentId("PAY_${UUID.randomUUID()}")
        fun from(value: String): PaymentId = PaymentId(value)
    }
}

@JvmInline
value class WalletId(val value: String) {
    companion object {
        fun generate(): WalletId = WalletId("WALLET_${UUID.randomUUID()}")
        fun from(value: String): WalletId = WalletId(value)
    }
}

@JvmInline
value class TransactionId(val value: String) {
    companion object {
        fun generate(): TransactionId = TransactionId("TXN_${UUID.randomUUID()}")
        fun from(value: String): TransactionId = TransactionId(value)
    }
}

@JvmInline
value class AccountId(val value: String) {
    companion object {
        fun userWallet(userId: UserId, currency: Currency): AccountId = 
            AccountId("USER_WALLET_${userId.value}_${currency.name}")
        fun userPoints(userId: UserId): AccountId = 
            AccountId("USER_POINTS_${userId.value}")
        fun companyPointsLiability(): AccountId = 
            AccountId("COMPANY_POINTS_LIABILITY")
        fun subscriptionRevenue(): AccountId = 
            AccountId("SUBSCRIPTION_REVENUE")
    }
}
```

---

## 🔍 7. 시나리오별 플로우

### 시나리오 1: Google Play 구독 구매
```
1. 사용자가 Google Play에서 구독 구매
2. Payment 생성 (외부 결제)
3. PaymentToWalletService: 포인트 적립
   - Wallet: 포인트 잔액 증가
   - Ledger: 복식부기 기록 (사용자 자산 증가 = 회사 부채 증가)
4. MemberSubscription 생성 (구독 활성화)
```

### 시나리오 2: 포인트로 구독 업그레이드
```
1. 사용자가 보유 포인트로 구독 업그레이드 요청
2. SubscriptionPurchaseService: 포인트 차감
   - Wallet: 포인트 잔액 감소
   - Ledger: 복식부기 기록 (사용자 자산 감소 = 회사 수익 증가)
3. MemberSubscription 업데이트 (구독 레벨 변경)
```

### 시나리오 3: 환불 처리
```
1. 환불 요청 및 승인
2. RefundService: 원본 결제 환불
3. WalletTransactionService: 관련 포인트 회수
   - Wallet: 포인트 잔액 감소
   - Ledger: 역방향 복식부기 기록
4. MemberSubscription 비활성화
```

---

## 🎯 8. 도입 단계별 로드맵

### Phase 1: 기본 Wallet 도입
- Wallet, Balance 도메인 추가
- 포인트 시스템 기본 구현
- Payment → Wallet 연동

### Phase 2: Ledger 시스템 구축
- LedgerEntry, Transaction 도메인 추가
- 복식부기 기본 구현
- 기본 계정 체계 구축

### Phase 3: 고급 기능 확장
- 다중 통화 지원
- 지갑 간 이체 기능
- 복잡한 프로모션 시스템

### Phase 4: 완전한 금융 시스템
- 실시간 잔액 조회
- 정교한 회계 보고서
- 외부 은행 시스템 연동

---

## 📝 주요 설계 결정사항

### 1. 이벤트 소싱 vs 상태 저장
- **선택**: 하이브리드 (Wallet은 상태, Ledger는 이벤트)
- **이유**: 성능과 정확성의 균형

### 2. 복식부기 적용
- **선택**: 모든 금융 거래에 복식부기 적용
- **이유**: 회계 무결성 보장, 감사 추적성

### 3. 다중 통화 지원
- **선택**: Currency enum으로 가상/실제 화폐 통합 관리
- **이유**: 확장성과 유연성

### 4. 거래 원자성
- **선택**: Payment-Wallet-Ledger 간 분산 트랜잭션
- **이유**: 데이터 일관성 보장

---

*이 TOBE 모델은 현재 단순한 구독 시스템에서 복잡한 금융 시스템으로 진화할 때의 설계 가이드라인을 제시합니다. 실제 도입 시에는 비즈니스 요구사항에 맞춰 단계적으로 적용하는 것을 권장합니다.*