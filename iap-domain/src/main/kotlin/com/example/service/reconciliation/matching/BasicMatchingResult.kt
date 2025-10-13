package com.example.service.reconciliation.matching

import com.example.domain.settlement.SettlementData
import com.example.domain.payment.event.PaymentEvent
import com.example.service.reconciliation.results.ReconciliationMatch

data class BasicMatchingResult(
    val matches: List<ReconciliationMatch>,
    val unmatchedSettlements: List<SettlementData>,
    val unmatchedEvents: List<PaymentEvent>
)