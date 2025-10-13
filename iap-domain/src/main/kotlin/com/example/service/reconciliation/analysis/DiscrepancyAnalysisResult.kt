package com.example.service.reconciliation.analysis

import com.example.domain.settlement.reconciliation.ReconciliationDiscrepancy
import com.example.service.reconciliation.results.ReconciliationMatch

data class DiscrepancyAnalysisResult(
    val validMatches: List<ReconciliationMatch>,
    val discrepancies: List<ReconciliationDiscrepancy>
)