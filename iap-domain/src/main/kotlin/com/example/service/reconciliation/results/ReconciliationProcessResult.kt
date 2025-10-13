package com.example.service.reconciliation.results

import com.example.domain.Platform
import com.example.domain.settlement.reconciliation.ReconciliationDiscrepancy
import com.example.domain.settlement.reconciliation.ReconciliationStatus
import com.example.service.reconciliation.resolution.ResolvedDiscrepancy
import java.time.LocalDate
import java.time.LocalDateTime

data class ReconciliationProcessResult(
    val date: LocalDate,
    val platform: Platform,
    val totalSettlementRecords: Int,
    val totalEventRecords: Int,
    val exactMatches: Int,
    val patternMatches: Int,
    val totalMatches: Int,
    val unmatchedSettlements: Int,
    val unmatchedEvents: Int,
    val totalDiscrepancies: Int,
    val autoResolvedDiscrepancies: Int,
    val unresolvedDiscrepancies: Int,
    val processingTimeMs: Long,
    val finalStatus: ReconciliationStatus,
    val detailedMatches: List<ReconciliationMatch>,
    val unresolvedDiscrepancyDetails: List<ReconciliationDiscrepancy>,
    val autoResolutionDetails: List<ResolvedDiscrepancy>,
    val processedAt: LocalDateTime
) {
    val matchingRate: Double
        get() = if (maxOf(totalSettlementRecords, totalEventRecords) > 0) {
            totalMatches.toDouble() / maxOf(totalSettlementRecords, totalEventRecords)
        } else 0.0
    
    val autoResolutionRate: Double
        get() = if (totalDiscrepancies > 0) {
            autoResolvedDiscrepancies.toDouble() / totalDiscrepancies
        } else 1.0
}