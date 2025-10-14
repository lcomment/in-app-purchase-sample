package com.example.integration.domain

data class Subscription(
    val id: Long = 0,
    val name: String,
    val pricePerMonth: Long,
    val description: String,
    val rentalPeriod: Long,
    val subscriptionPeriod: Long,
    val seriesRentalCountPerDay: Long,
    val questCount: Long,
    val reward: Long,
    val displayable: Boolean,
)
