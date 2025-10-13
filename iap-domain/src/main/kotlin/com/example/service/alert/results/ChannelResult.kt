package com.example.service.alert.results

import com.example.service.alert.configuration.AlertChannel
import java.time.LocalDateTime

data class ChannelResult(
    val channel: AlertChannel,
    val success: Boolean,
    val message: String,
    val sentAt: LocalDateTime,
    val metadata: Map<String, String> = emptyMap()
)