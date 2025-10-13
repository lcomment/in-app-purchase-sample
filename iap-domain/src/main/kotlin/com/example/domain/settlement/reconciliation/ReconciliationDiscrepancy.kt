package com.example.domain.settlement.reconciliation

data class ReconciliationDiscrepancy(
    val transactionId: String,
    val discrepancyType: DiscrepancyType,
    val platformData: String?,
    val internalData: String?,
    val description: String
)