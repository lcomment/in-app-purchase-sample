package com.example.service.settlement.results

import com.example.domain.Platform
import com.example.domain.settlement.DailySettlementSummary
import com.example.domain.settlement.reconciliation.ReconciliationResult

data class SettlementCollectionResult(
    val platform: Platform,
    val success: Boolean,
    val dataCount: Int,
    val summary: DailySettlementSummary?,
    val reconciliation: ReconciliationResult?,
    val errors: List<String>
)