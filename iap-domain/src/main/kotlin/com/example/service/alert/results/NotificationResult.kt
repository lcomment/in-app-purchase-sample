package com.example.service.alert.results

import com.example.service.alert.configuration.AlertChannel
import java.time.LocalDateTime

data class NotificationResult(
    val notificationId: String,
    val channelResults: Map<AlertChannel, ChannelResult>,
    val overallSuccess: Boolean,
    val processedAt: LocalDateTime
)