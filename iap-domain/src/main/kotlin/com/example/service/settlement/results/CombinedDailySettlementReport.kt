package com.example.service.settlement.results

import com.example.domain.settlement.DailySettlementSummary
import com.example.domain.settlement.reconciliation.ReconciliationResult
import com.example.domain.settlement.reconciliation.ReconciliationStatus
import java.time.LocalDate
import java.time.LocalDateTime

data class CombinedDailySettlementReport(
    val date: LocalDate,
    val googlePlaySummary: DailySettlementSummary?,
    val appStoreSummary: DailySettlementSummary?,
    val googlePlayReconciliation: ReconciliationResult?,
    val appStoreReconciliation: ReconciliationResult?,
    val combinedMetrics: CombinedDailyMetrics,
    val overallReconciliationStatus: ReconciliationStatus,
    val generatedAt: LocalDateTime
)

data class CombinedDailyMetrics(
    val totalTransactions: Int,
    val totalGrossRevenue: java.math.BigDecimal,
    val totalPlatformFees: java.math.BigDecimal,
    val totalNetRevenue: java.math.BigDecimal,
    val googlePlayShare: java.math.BigDecimal, // 백분율
    val appStoreShare: java.math.BigDecimal     // 백분율
)

data class SettlementPeriodStatistics(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalDays: Int,
    val googlePlayDays: Int,
    val appStoreDays: Int,
    val totalTransactions: Int,
    val totalGrossRevenue: java.math.BigDecimal,
    val totalNetRevenue: java.math.BigDecimal,
    val averageDailyRevenue: java.math.BigDecimal
)