package com.example.service.reconciliation.resolution

import com.example.domain.settlement.reconciliation.ReconciliationDiscrepancy

data class AutoResolutionResult(
    val resolvedDiscrepancies: List<ResolvedDiscrepancy>,
    val unresolvedDiscrepancies: List<ReconciliationDiscrepancy>
)