package com.example.domain.settlement

enum class SettlementEventType(val description: String) {
    PURCHASE("구매"),
    RENEWAL("갱신"),
    REFUND("환불"),
    CHARGEBACK("차지백"),
    TAX_ADJUSTMENT("세금 조정"),
    FEE_ADJUSTMENT("수수료 조정")
}