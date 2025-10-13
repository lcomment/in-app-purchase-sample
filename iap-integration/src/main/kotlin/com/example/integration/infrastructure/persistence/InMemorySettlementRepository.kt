package com.example.integration.infrastructure.persistence

import com.example.integration.application.port.out.SettlementRepositoryPort
import com.example.integration.domain.Settlement
import com.example.integration.domain.Platform
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * 인메모리 정산 저장소 구현체 (Infrastructure Layer)
 * 실제 운영환경에서는 JPA 또는 다른 영속성 기술을 사용
 */
@Repository
class InMemorySettlementRepository : SettlementRepositoryPort {
    
    private val settlements = ConcurrentHashMap<String, Settlement>()
    private val platformDateIndex = ConcurrentHashMap<String, String>() // "platform_date" -> settlementId
    
    override fun save(settlement: Settlement): Settlement {
        settlements[settlement.id] = settlement
        val key = "${settlement.platform}_${settlement.settlementDate}"
        platformDateIndex[key] = settlement.id
        return settlement
    }
    
    override fun findByPlatformAndDate(platform: Platform, settlementDate: LocalDate): Settlement? {
        val key = "${platform}_${settlementDate}"
        val settlementId = platformDateIndex[key] ?: return null
        return settlements[settlementId]
    }
    
    override fun findByPlatformAndDateRange(
        platform: Platform,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Settlement> {
        return settlements.values.filter { settlement ->
            settlement.platform == platform &&
            !settlement.settlementDate.isBefore(startDate) &&
            !settlement.settlementDate.isAfter(endDate)
        }
    }
}