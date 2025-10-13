package com.example.domain.refund

import com.example.domain.Platform
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 환불이 정산에 미치는 영향 추적
 * 
 * 환불로 인한 재무적 영향을 정확히 추적하고 정산 시스템과 연동합니다.
 */
data class RefundSettlementImpact(
    val id: String,                           // 영향 추적 ID
    val refundId: String,                     // 관련 환불 ID
    val originalSettlementId: String?,        // 원본 정산 ID
    val platform: Platform,                   // 플랫폼
    val impactType: SettlementImpactType,     // 영향 타입
    val adjustmentAmount: BigDecimal,         // 조정 금액 (음수)
    val platformFeeAdjustment: BigDecimal,    // 플랫폼 수수료 조정
    val netAmountAdjustment: BigDecimal,      // 순 금액 조정
    val taxAdjustment: BigDecimal?,           // 세금 조정
    val currency: String,                     // 통화
    val impactDate: LocalDate,                // 영향 발생일
    val processedDate: LocalDate?,            // 처리일
    val reconciliationRequired: Boolean,      // 대사 필요 여부
    val reconciledAt: LocalDateTime?,         // 대사 완료 시간
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * 총 수익 영향 계산 (플랫폼 수수료 포함)
     */
    val totalRevenueImpact: BigDecimal
        get() = adjustmentAmount + platformFeeAdjustment

    /**
     * 대사 완료 여부
     */
    val isReconciled: Boolean
        get() = reconciledAt != null

    /**
     * 처리 대기 중인지 확인
     */
    val isPending: Boolean
        get() = processedDate == null
}

/**
 * 정산 영향 타입
 */
enum class SettlementImpactType(val description: String) {
    FULL_REFUND("전액 환불"),
    PARTIAL_REFUND("부분 환불"),
    CHARGEBACK("차지백"),
    REVENUE_ADJUSTMENT("수익 조정"),
    FEE_ADJUSTMENT("수수료 조정"),
    TAX_ADJUSTMENT("세금 조정")
}

/**
 * 환불 재무 요약
 * 
 * 특정 기간 동안의 환불이 재무에 미친 영향을 요약합니다.
 */
data class RefundFinancialSummary(
    val period: ReportPeriod,                 // 보고 기간
    val platform: Platform?,                  // 플랫폼 (null이면 전체)
    val totalRefunds: Int,                    // 총 환불 건수
    val totalRefundAmount: BigDecimal,        // 총 환불 금액
    val totalPlatformFeeImpact: BigDecimal,   // 총 플랫폼 수수료 영향
    val totalNetRevenueImpact: BigDecimal,    // 총 순 수익 영향
    val averageRefundAmount: BigDecimal,      // 평균 환불 금액
    val refundRate: Double,                   // 환불률 (환불 건수 / 총 거래)
    val refundAmountRate: Double,             // 환불 금액률 (환불 금액 / 총 매출)
    val byReason: Map<RefundReason, RefundReasonSummary>,  // 사유별 요약
    val byStatus: Map<RefundStatus, Int>,     // 상태별 건수
    val currency: String,                     // 기준 통화
    val generatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 환불 사유별 요약
 */
data class RefundReasonSummary(
    val reason: RefundReason,
    val count: Int,
    val totalAmount: BigDecimal,
    val averageAmount: BigDecimal,
    val percentageOfTotal: Double
)

/**
 * 보고 기간
 */
data class ReportPeriod(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val type: PeriodType
) {
    val dayCount: Long
        get() = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1
}

enum class PeriodType(val description: String) {
    DAILY("일간"),
    WEEKLY("주간"),
    MONTHLY("월간"),
    QUARTERLY("분기"),
    YEARLY("연간"),
    CUSTOM("사용자 정의")
}