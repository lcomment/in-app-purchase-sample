package com.example.integration.infrastructure.persistence

import com.example.integration.application.port.out.ReconciliationRepositoryPort
import com.example.integration.domain.ReconciliationRecord
import com.example.integration.domain.Platform
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * 인메모리 대사 저장소 구현체 (Infrastructure Layer)
 * 실제 운영환경에서는 JPA 또는 다른 영속성 기술을 사용
 */
@Repository
class InMemoryReconciliationRepository : ReconciliationRepositoryPort {
    
    private val reconciliationRecords = ConcurrentHashMap<String, ReconciliationRecord>()
    private val platformDateIndex = ConcurrentHashMap<String, String>() // "platform_date" -> recordId
    
    override fun save(reconciliationRecord: ReconciliationRecord): ReconciliationRecord {
        reconciliationRecords[reconciliationRecord.id] = reconciliationRecord
        val key = "${reconciliationRecord.platform}_${reconciliationRecord.reconciliationDate}"
        platformDateIndex[key] = reconciliationRecord.id
        return reconciliationRecord
    }
    
    override fun findByPlatformAndDate(platform: Platform, reconciliationDate: LocalDate): ReconciliationRecord? {
        val key = "${platform}_${reconciliationDate}"
        val recordId = platformDateIndex[key] ?: return null
        return reconciliationRecords[recordId]
    }
    
    override fun findByPlatformAndDateRange(
        platform: Platform,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ReconciliationRecord> {
        return reconciliationRecords.values.filter { record ->
            record.platform == platform &&
            !record.reconciliationDate.isBefore(startDate) &&
            !record.reconciliationDate.isAfter(endDate)
        }
    }
}