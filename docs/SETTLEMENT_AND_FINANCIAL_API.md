# 정산 및 재무 데이터 API 가이드

## 개요

Google Play와 Apple App Store의 정산 및 재무 데이터를 프로그래밍 방식으로 수집하기 위한 API와 구현 방법을 설명합니다. 두 플랫폼은 서로 다른 접근 방식을 제공하므로, 각각의 특성을 이해하고 적절한 구현 전략을 수립해야 합니다.

## Google Play 정산 API

### 1. 현재 상황 (2024년 기준)

Google Play는 **직접적인 Sales/Financial REST API를 제공하지 않습니다**. 대신 다음과 같은 방법들을 사용해야 합니다:

- **Google Cloud Storage 기반 리포트 다운로드** (주요 방식)
- **Play Console 수동 다운로드**
- **Play Developer Reporting API** (성능 데이터만, 매출 데이터 제한적)

### 2. Google Cloud Storage 방식

#### 설정 요구사항
- Google Cloud Storage 버킷 접근 권한
- 서비스 계정 인증 설정
- gsutil 또는 Cloud Storage API 사용

#### 구현 예시
```bash
# 2024년 모든 정산 보고서 다운로드
gsutil cp -r gs://pubsite_prod_rev_[YOUR_BUCKET_ID]/invoice_billing_reports/invoice_billing_report_2024* /local/directory

# 특정 월의 보고서 다운로드
gsutil cp gs://pubsite_prod_rev_[YOUR_BUCKET_ID]/invoice_billing_reports/invoice_billing_report_202410.csv /local/directory
```

#### Kotlin/Java 구현
```kotlin
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions

class GooglePlayReportDownloader(
    private val bucketId: String,
    private val credentialsPath: String
) {
    
    private val storage: Storage = StorageOptions.newBuilder()
        .setCredentials(ServiceAccountCredentials.fromStream(
            FileInputStream(credentialsPath)
        ))
        .build()
        .service
    
    fun downloadEstimatedSalesReport(date: LocalDate): ByteArray {
        val blobName = "sales_reports/sales_${date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))}.csv"
        val blob = storage.get(bucketId, blobName)
        
        return blob?.getContent() 
            ?: throw RuntimeException("Sales report not found for date: $date")
    }
    
    fun downloadFinancialReport(yearMonth: YearMonth): ByteArray {
        val blobName = "invoice_billing_reports/invoice_billing_report_${yearMonth.format(DateTimeFormatter.ofPattern("yyyyMM"))}.csv"
        val blob = storage.get(bucketId, blobName)
        
        return blob?.getContent() 
            ?: throw RuntimeException("Financial report not found for month: $yearMonth")
    }
}
```

### 3. 보고서 유형

#### A. Estimated Sales Report (일일)
- **생성 주기**: 매일
- **내용**: 거래 발생 즉시 추가되는 추정 데이터
- **용도**: 실시간 분석, 트렌드 파악
- **주의사항**: 회계 용도로는 부적합

#### B. Earnings Report (월별)
- **생성 주기**: 매월 초
- **내용**: 정확한 매출, 세금, 수수료 데이터
- **용도**: 정식 정산, 회계 처리
- **포함 데이터**: 총 매출, Google 수수료, 세금, 순 수익

### 4. Google Play 정산 플로우
```
1. 실시간 거래 발생
   ↓
2. RTDN (Real-time Developer Notifications) 수신
   ↓
3. 일일 Estimated Sales Report 생성 (Google Cloud Storage)
   ↓
4. 월말 Earnings Report 생성 (정확한 정산 데이터)
   ↓
5. 월 중순경 실제 정산 입금 (최소 $10 이상)
```

## Apple App Store 정산 API

### 1. App Store Connect API (2024년 기준)

Apple은 **완전한 REST API**를 제공합니다:

- **Sales Reports API**: 판매 데이터 조회
- **Financial Reports API**: 정산 데이터 조회
- **JWT 기반 인증**
- **JSON/CSV 형태 응답**

### 2. 인증 설정

#### JWT 토큰 생성
```kotlin
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import java.security.interfaces.ECPrivateKey

class AppStoreConnectAuth(
    private val keyId: String,        // App Store Connect API Key ID
    private val issuerId: String,     // Issuer ID
    private val privateKey: ECPrivateKey  // ES256 Private Key
) {
    
    fun generateJWT(): String {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(1200) // 20분
        
        return Jwts.builder()
            .setHeaderParam("kid", keyId)
            .setIssuer(issuerId)
            .setAudience("appstoreconnect-v1")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiresAt))
            .signWith(privateKey, SignatureAlgorithm.ES256)
            .compact()
    }
}
```

### 3. Sales Reports API

#### 엔드포인트
```http
GET https://api.appstoreconnect.apple.com/v1/salesReports
```

#### 주요 파라미터
- `filter[frequency]`: DAILY, WEEKLY, MONTHLY, YEARLY
- `filter[reportDate]`: 보고서 날짜 (YYYY-MM-DD)
- `filter[reportSubType]`: SUMMARY, DETAILED
- `filter[reportType]`: SALES, SUBSCRIPTION
- `filter[vendorNumber]`: 벤더 번호

#### 구현 예시
```kotlin
class AppStoreConnectApiClient(
    private val auth: AppStoreConnectAuth,
    private val restClient: RestClient
) {
    
    fun downloadSalesReport(
        reportDate: LocalDate,
        frequency: ReportFrequency,
        vendorNumber: String,
        reportType: ReportType = ReportType.SALES
    ): SalesReportResponse {
        
        val jwt = auth.generateJWT()
        
        return restClient.get()
            .uri { builder ->
                builder.path("/v1/salesReports")
                    .queryParam("filter[frequency]", frequency.name)
                    .queryParam("filter[reportDate]", reportDate.toString())
                    .queryParam("filter[reportSubType]", "SUMMARY")
                    .queryParam("filter[reportType]", reportType.name)
                    .queryParam("filter[vendorNumber]", vendorNumber)
                    .build()
            }
            .header("Authorization", "Bearer $jwt")
            .header("Accept", "application/json")
            .retrieve()
            .body(SalesReportResponse::class.java)
            ?: throw RuntimeException("Failed to download sales report")
    }
    
    fun downloadSubscriptionReport(
        reportDate: LocalDate,
        vendorNumber: String
    ): SubscriptionReportResponse {
        
        return downloadSalesReport(
            reportDate = reportDate,
            frequency = ReportFrequency.DAILY,
            vendorNumber = vendorNumber,
            reportType = ReportType.SUBSCRIPTION
        ) as SubscriptionReportResponse
    }
}

enum class ReportFrequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

enum class ReportType {
    SALES, SUBSCRIPTION, PRE_ORDER
}
```

### 4. Financial Reports API

#### 엔드포인트
```http
GET https://api.appstoreconnect.apple.com/v1/financeReports
```

#### 주요 파라미터
- `filter[regionCode]`: 지역 코드 (WW = Worldwide)
- `filter[reportDate]`: 보고서 날짜 (YYYY-MM)
- `filter[vendorNumber]`: 벤더 번호

#### 구현 예시
```kotlin
fun downloadFinancialReport(
    reportDate: YearMonth,
    vendorNumber: String,
    regionCode: String = "WW"
): FinancialReportResponse {
    
    val jwt = auth.generateJWT()
    
    return restClient.get()
        .uri { builder ->
            builder.path("/v1/financeReports")
                .queryParam("filter[reportDate]", reportDate.toString())
                .queryParam("filter[vendorNumber]", vendorNumber)
                .queryParam("filter[regionCode]", regionCode)
                .build()
        }
        .header("Authorization", "Bearer $jwt")
        .header("Accept", "application/json")
        .retrieve()
        .body(FinancialReportResponse::class.java)
        ?: throw RuntimeException("Failed to download financial report")
}
```

### 5. 보고서 유형

#### A. Sales Reports
- **Summary Sales Report**: 앱 및 인앱 구매 집계 데이터
- **Subscription Report**: 자동 갱신 구독 데이터
- **Pre-Order Report**: 사전 주문 데이터

#### B. Financial Reports
- **월별 생성**: Apple 회계 달력 기준
- **내용**: 월별 수익, 원천징수, 세금 정보
- **포함 데이터**: 제품별, 지역별, 통화별, 가격별 상세 수익

### 6. Apple 정산 플로우
```
1. 실시간 거래 발생
   ↓
2. App Store Server Notifications v2 수신
   ↓
3. 일일 Sales Report 생성 (API로 다운로드 가능)
   ↓
4. 월말 Financial Report 생성 (정확한 정산 데이터)
   ↓
5. 월말 실제 정산 입금 (최소 $150 이상)
```

## 통합 대사처리 구현

### 1. 일일 대사처리 스케줄러

```kotlin
@Service
class ReconciliationService(
    private val googlePlayDownloader: GooglePlayReportDownloader,
    private val appStoreApiClient: AppStoreConnectApiClient,
    private val settlementRecordRepository: SettlementRecordRepository
) {
    
    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
    fun performDailyReconciliation() {
        val yesterday = LocalDate.now().minusDays(1)
        
        try {
            // Google Play 데이터 수집
            val googlePlayData = collectGooglePlayData(yesterday)
            
            // App Store 데이터 수집
            val appStoreData = collectAppStoreData(yesterday)
            
            // 내부 시스템 데이터와 대사
            val reconciliationResult = performReconciliation(
                date = yesterday,
                googlePlayData = googlePlayData,
                appStoreData = appStoreData
            )
            
            // 대사 결과 저장 및 알림
            saveReconciliationResult(reconciliationResult)
            
        } catch (e: Exception) {
            logger.error("Daily reconciliation failed for date: $yesterday", e)
            sendAlertNotification("Daily reconciliation failed", e.message)
        }
    }
    
    private fun collectGooglePlayData(date: LocalDate): GooglePlayDailyData {
        val reportData = googlePlayDownloader.downloadEstimatedSalesReport(date)
        return parseGooglePlayCsvData(reportData)
    }
    
    private fun collectAppStoreData(date: LocalDate): AppStoreDailyData {
        val salesReport = appStoreApiClient.downloadSalesReport(
            reportDate = date,
            frequency = ReportFrequency.DAILY,
            vendorNumber = appStoreVendorNumber
        )
        
        val subscriptionReport = appStoreApiClient.downloadSubscriptionReport(
            reportDate = date,
            vendorNumber = appStoreVendorNumber
        )
        
        return AppStoreDailyData(salesReport, subscriptionReport)
    }
}
```

### 2. 월별 정산 보고서 생성

```kotlin
@Scheduled(cron = "0 0 4 5 * *") // 매월 5일 새벽 4시
fun generateMonthlySettlementReport() {
    val lastMonth = YearMonth.now().minusMonths(1)
    
    try {
        // Google Play 월별 정산 데이터
        val googleFinancialData = googlePlayDownloader.downloadFinancialReport(lastMonth)
        
        // App Store 월별 정산 데이터
        val appStoreFinancialData = appStoreApiClient.downloadFinancialReport(
            reportDate = lastMonth,
            vendorNumber = appStoreVendorNumber
        )
        
        // 월별 보고서 생성
        val monthlyReport = createMonthlySettlementReport(
            month = lastMonth,
            googlePlayData = googleFinancialData,
            appStoreData = appStoreFinancialData
        )
        
        // 보고서 저장 및 전송
        saveMonthlyReport(monthlyReport)
        sendMonthlyReportEmail(monthlyReport)
        
    } catch (e: Exception) {
        logger.error("Monthly settlement report generation failed for month: $lastMonth", e)
    }
}
```

## 플랫폼별 비교

| 항목 | Google Play | Apple App Store |
|------|-------------|-----------------|
| **API 지원** | 제한적 (Cloud Storage) | 완전 지원 (REST API) |
| **데이터 형식** | CSV | JSON/CSV |
| **인증 방식** | Service Account | JWT (ES256) |
| **실시간성** | 일일 추정치 | 일일 정확치 |
| **정산 주기** | 월 중순 | 월말 |
| **최소 정산금액** | $10 | $150 |
| **수수료** | 30% (1년 후 15%) | 30% (1년 후 15%) |
| **구현 복잡도** | 높음 (파일 처리) | 중간 (REST API) |

## 구현 권장사항

### 1. 아키텍처 설계
- **통합 인터페이스**: 플랫폼별 차이를 추상화
- **비동기 처리**: 대용량 데이터 처리를 위한 큐 시스템
- **오류 복구**: 재시도 메커니즘 및 알림 시스템

### 2. 데이터 정합성
- **중복 제거**: 동일 거래의 중복 처리 방지
- **타임스탬프 검증**: 보고서 생성 시간 확인
- **금액 검증**: 소수점 처리 및 통화 변환 정확성

### 3. 보안 고려사항
- **인증 정보 보호**: 서비스 계정 키, JWT 키 안전 관리
- **접근 권한 최소화**: 필요한 권한만 부여
- **감사 로그**: 모든 API 호출 및 데이터 접근 기록

### 4. 모니터링 및 알림
- **대사 실패 알림**: 불일치 발생 시 즉시 알림
- **API 호출 실패**: 네트워크 오류, 인증 실패 등
- **데이터 품질 검증**: 예상 범위를 벗어난 데이터 검출

## 관련 문서

### Google Play
- [Play Developer Reporting API](https://developers.google.com/play/developer/reporting)
- [Download sales and payout reports](https://support.google.com/googleplay/android-developer/answer/2482017)
- [Google Cloud Storage API](https://cloud.google.com/storage/docs/apis)

### Apple App Store
- [App Store Connect API](https://developer.apple.com/documentation/appstoreconnectapi)
- [Download Sales and Trends Reports](https://developer.apple.com/documentation/appstoreconnectapi/download_sales_and_trends_reports)
- [Download Finance Reports](https://developer.apple.com/documentation/appstoreconnectapi/download_finance_reports)
- [Sales and Finance](https://developer.apple.com/documentation/appstoreconnectapi/sales-and-finance)

## 결론

Google Play는 파일 기반 접근 방식으로 구현 복잡도가 높지만, Apple App Store는 표준 REST API를 제공하여 상대적으로 구현이 용이합니다. 두 플랫폼 모두 정확한 대사처리를 위해서는 일일 모니터링과 월별 정산 검증이 필수적입니다.