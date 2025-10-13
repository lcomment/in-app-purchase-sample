package com.example.service.alert.requests

import com.example.service.alert.configuration.AlertChannel
import com.example.service.alert.priority.NotificationPriority

data class NotificationRequest(
    val title: String,
    val message: String,
    val priority: NotificationPriority,
    val channels: List<AlertChannel>,
    val recipients: List<String>,
    val metadata: Map<String, String> = emptyMap()
)