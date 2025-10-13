package com.example.service.alert.logging

import com.example.service.alert.requests.NotificationRequest
import com.example.service.alert.results.ChannelResult
import com.example.service.alert.configuration.AlertChannel
import java.time.LocalDateTime

data class NotificationLog(
    val id: String,
    val request: NotificationRequest,
    val results: Map<AlertChannel, ChannelResult>,
    val overallSuccess: Boolean,
    val processedAt: LocalDateTime
)