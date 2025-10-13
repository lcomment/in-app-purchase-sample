package com.example.repository

import com.example.domain.payment.event.PaymentEvent
import com.example.domain.payment.event.PaymentEventType
import com.example.domain.Platform
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Repository
class PaymentEventRepository {
    
    private val paymentEvents = ConcurrentHashMap<String, PaymentEvent>()
    
    fun save(paymentEvent: PaymentEvent): PaymentEvent {
        paymentEvents[paymentEvent.id] = paymentEvent
        return paymentEvent
    }
    
    fun findById(id: String): PaymentEvent? {
        return paymentEvents[id]
    }
    
    fun findBySubscriptionId(subscriptionId: String): List<PaymentEvent> {
        return paymentEvents.values.filter { it.subscriptionId == subscriptionId }
            .sortedByDescending { it.createdAt }
    }
    
    fun findByPlatform(platform: Platform): List<PaymentEvent> {
        return paymentEvents.values.filter { it.platform == platform }
            .sortedByDescending { it.createdAt }
    }
    
    fun findByEventType(eventType: PaymentEventType): List<PaymentEvent> {
        return paymentEvents.values.filter { it.eventType == eventType }
            .sortedByDescending { it.createdAt }
    }
    
    fun findByDateRange(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<PaymentEvent> {
        return paymentEvents.values.filter { event ->
            event.createdAt >= startDate && event.createdAt <= endDate
        }.sortedByDescending { it.createdAt }
    }
    
    fun findByDateRangeAndPlatform(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        platform: Platform
    ): List<PaymentEvent> {
        return paymentEvents.values.filter { event ->
            event.createdAt >= startDate && 
            event.createdAt <= endDate && 
            event.platform == platform
        }.sortedByDescending { it.createdAt }
    }
    
    fun findByDateRangeAndEventType(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        eventType: PaymentEventType
    ): List<PaymentEvent> {
        return paymentEvents.values.filter { event ->
            event.createdAt >= startDate && 
            event.createdAt <= endDate && 
            event.eventType == eventType
        }.sortedByDescending { it.createdAt }
    }
    
    /**
     * 일일 정산을 위한 특정 날짜의 이벤트 조회
     */
    fun findByDate(date: LocalDate): List<PaymentEvent> {
        val startOfDay = date.atStartOfDay()
        val endOfDay = date.plusDays(1).atStartOfDay().minusNanos(1)
        return findByDateRange(startOfDay, endOfDay)
    }
    
    /**
     * 일일 정산을 위한 특정 날짜와 플랫폼의 이벤트 조회
     */
    fun findByDateAndPlatform(date: LocalDate, platform: Platform): List<PaymentEvent> {
        val startOfDay = date.atStartOfDay()
        val endOfDay = date.plusDays(1).atStartOfDay().minusNanos(1)
        return findByDateRangeAndPlatform(startOfDay, endOfDay, platform)
    }
    
    /**
     * 특정 구독의 이벤트 히스토리 조회 (시간순 정렬)
     */
    fun findSubscriptionEventHistory(subscriptionId: String): List<PaymentEvent> {
        return findBySubscriptionId(subscriptionId)
    }
    
    /**
     * 최근 이벤트 조회 (모니터링용)
     */
    fun findRecentEvents(limit: Int = 100): List<PaymentEvent> {
        return paymentEvents.values
            .sortedByDescending { it.createdAt }
            .take(limit)
    }
    
    /**
     * 특정 플랫폼의 최근 이벤트 조회
     */
    fun findRecentEventsByPlatform(platform: Platform, limit: Int = 100): List<PaymentEvent> {
        return paymentEvents.values
            .filter { it.platform == platform }
            .sortedByDescending { it.createdAt }
            .take(limit)
    }
    
    /**
     * 이벤트 타입별 개수 집계 (통계용)
     */
    fun countByEventTypeAndDateRange(
        eventType: PaymentEventType,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Long {
        return paymentEvents.values.count { event ->
            event.eventType == eventType &&
            event.createdAt >= startDate && 
            event.createdAt <= endDate
        }.toLong()
    }
    
    /**
     * 플랫폼별 이벤트 개수 집계
     */
    fun countByPlatformAndDateRange(
        platform: Platform,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Long {
        return paymentEvents.values.count { event ->
            event.platform == platform &&
            event.createdAt >= startDate && 
            event.createdAt <= endDate
        }.toLong()
    }
    
    /**
     * 일일 이벤트 통계 생성
     */
    fun getDailyEventStatistics(date: LocalDate): DailyEventStatistics {
        val events = findByDate(date)
        
        val eventsByType = events.groupBy { it.eventType }
        val eventsByPlatform = events.groupBy { it.platform }
        
        return DailyEventStatistics(
            date = date,
            totalEvents = events.size,
            purchaseEvents = eventsByType[PaymentEventType.PURCHASE]?.size ?: 0,
            renewalEvents = eventsByType[PaymentEventType.RENEWAL]?.size ?: 0,
            cancellationEvents = eventsByType[PaymentEventType.CANCELLATION]?.size ?: 0,
            refundEvents = eventsByType[PaymentEventType.REFUND]?.size ?: 0,
            androidEvents = eventsByPlatform[Platform.AOS]?.size ?: 0,
            iosEvents = eventsByPlatform[Platform.IOS]?.size ?: 0
        )
    }
    
    fun findAll(): List<PaymentEvent> {
        return paymentEvents.values.sortedByDescending { it.createdAt }
    }
    
    fun deleteById(id: String): Boolean {
        return paymentEvents.remove(id) != null
    }
    
    fun count(): Long {
        return paymentEvents.size.toLong()
    }
}

data class DailyEventStatistics(
    val date: LocalDate,
    val totalEvents: Int,
    val purchaseEvents: Int,
    val renewalEvents: Int,
    val cancellationEvents: Int,
    val refundEvents: Int,
    val androidEvents: Int,
    val iosEvents: Int
) {
    val conversionRate: Double
        get() = if (totalEvents > 0) purchaseEvents.toDouble() / totalEvents else 0.0
    
    val churnRate: Double
        get() = if (totalEvents > 0) cancellationEvents.toDouble() / totalEvents else 0.0
}