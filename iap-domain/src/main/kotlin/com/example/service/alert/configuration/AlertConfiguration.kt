package com.example.service.alert.configuration

import com.example.domain.Platform

data class AlertConfiguration(
    val platform: Platform,
    val enabledChannels: List<AlertChannel>,
    val recipients: Map<AlertChannel, List<String>>,
    val thresholds: Map<String, Double>,
    val cooldownMinutes: Int
)

enum class AlertChannel {
    EMAIL,
    SLACK,
    SMS,
    WEBHOOK,
    PUSH_NOTIFICATION
}