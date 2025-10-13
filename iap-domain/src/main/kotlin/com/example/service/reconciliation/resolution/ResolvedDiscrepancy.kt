package com.example.service.reconciliation.resolution

import com.example.domain.settlement.reconciliation.ReconciliationDiscrepancy
import java.time.LocalDateTime

data class ResolvedDiscrepancy(
    val originalDiscrepancy: ReconciliationDiscrepancy,
    val resolutionMethod: String,
    val resolutionDescription: String,
    val resolvedAt: LocalDateTime
)