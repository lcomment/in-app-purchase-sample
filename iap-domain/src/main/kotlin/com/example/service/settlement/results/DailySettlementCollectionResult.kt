package com.example.service.settlement.results

import com.example.domain.Platform
import java.time.LocalDate
import java.time.LocalDateTime

data class DailySettlementCollectionResult(
    val date: LocalDate,
    val platformResults: Map<Platform, SettlementCollectionResult>,
    val overallSuccess: Boolean,
    val totalDataCount: Int,
    val processedAt: LocalDateTime
)