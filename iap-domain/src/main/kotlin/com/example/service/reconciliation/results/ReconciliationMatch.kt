package com.example.service.reconciliation.results

import com.example.domain.settlement.SettlementData
import com.example.domain.payment.event.PaymentEvent
import com.example.service.reconciliation.MatchType

data class ReconciliationMatch(
    val settlementData: SettlementData,
    val paymentEvent: PaymentEvent,
    val matchType: MatchType,
    val confidence: Double
)