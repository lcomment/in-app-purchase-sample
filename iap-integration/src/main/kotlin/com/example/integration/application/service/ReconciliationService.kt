package com.example.integration.application.service

import com.example.integration.application.port.`in`.ReconciliationUseCase
import com.example.integration.application.port.`in`.ReconciliationRequest
import com.example.integration.application.port.`in`.ReconciliationResult
import com.example.integration.application.port.`in`.DiscrepancyDetail
import com.example.integration.application.port.out.PaymentRepositoryPort
import com.example.integration.application.port.out.ReconciliationRepositoryPort
import com.example.integration.domain.Platform
import com.example.integration.domain.ReconciliationRecord
import com.example.integration.domain.ReconciliationStatus
import com.example.integration.domain.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * 대사 처리 서비스 (Application Layer)
 */
@Service
@Transactional
class ReconciliationService(
    private val platformAdapterFactory: PlatformAdapterFactory,
    private val paymentRepository: PaymentRepositoryPort,
    private val reconciliationRepository: ReconciliationRepositoryPort
) : ReconciliationUseCase {
    
    private val logger = LoggerFactory.getLogger(ReconciliationService::class.java)
    
    override fun performReconciliation(request: ReconciliationRequest): ReconciliationResult {
        logger.info("Performing reconciliation: platform=${request.platform}, date=${request.reconciliationDate}")
        
        try {
            // 기존 대사 기록 확인
            val existingRecord = reconciliationRepository.findByPlatformAndDate(
                request.platform,
                request.reconciliationDate
            )
            
            if (existingRecord != null && existingRecord.status == ReconciliationStatus.MATCHED) {
                logger.info("Reconciliation already completed: ${existingRecord.id}")
                return ReconciliationResult(
                    success = true,
                    reconciliationRecord = existingRecord
                )
            }
            
            // 내부 시스템 데이터 조회
            val internalPayments = paymentRepository.findByPlatformAndDateRange(
                platform = request.platform,
                startDate = request.reconciliationDate,
                endDate = request.reconciliationDate
            ).filter { payment ->
                when {
                    !request.includeRefunds && payment.status == PaymentStatus.REFUNDED -> false
                    else -> true
                }
            }
            
            // 플랫폼 어댑터를 통한 외부 데이터 조회
            val adapter = platformAdapterFactory.getAdapter(request.platform)
            val externalSettlementData = adapter.getSettlementData(
                startDate = request.reconciliationDate,
                endDate = request.reconciliationDate
            )
            
            if (externalSettlementData.errorMessage != null) {
                logger.error("Failed to fetch external settlement data: ${externalSettlementData.errorMessage}")
                return ReconciliationResult(
                    success = false,
                    reconciliationRecord = null,
                    errorMessage = "외부 데이터 조회 실패: ${externalSettlementData.errorMessage}"
                )
            }
            
            val externalPayments = externalSettlementData.payments
            
            // 대사 수행
            val reconciliationResult = performDetailedReconciliation(
                internalPayments = internalPayments,
                externalPayments = externalPayments
            )
            
            // 대사 기록 생성
            val reconciliationRecord = ReconciliationRecord(
                id = existingRecord?.id ?: UUID.randomUUID().toString(),
                platform = request.platform,
                reconciliationDate = request.reconciliationDate,
                internalTransactionCount = internalPayments.size,
                externalTransactionCount = externalPayments.size,
                internalAmount = internalPayments.sumOf { it.amount },
                externalAmount = externalPayments.sumOf { it.amount },
                discrepancyCount = reconciliationResult.discrepancies.size,
                discrepancyAmount = reconciliationResult.discrepancies
                    .mapNotNull { it.internalAmount?.toBigDecimalOrNull() }
                    .sumOf { it },
                currency = internalPayments.firstOrNull()?.currency ?: "USD",
                status = if (reconciliationResult.discrepancies.isEmpty()) 
                    ReconciliationStatus.MATCHED 
                else 
                    ReconciliationStatus.DISCREPANCY_FOUND,
                notes = if (reconciliationResult.discrepancies.isNotEmpty()) 
                    "Found ${reconciliationResult.discrepancies.size} discrepancies" 
                else null
            )
            
            // 대사 기록 저장
            val savedRecord = reconciliationRepository.save(reconciliationRecord)
            
            logger.info("Reconciliation completed: id=${savedRecord.id}, " +
                    "status=${savedRecord.status}, discrepancies=${reconciliationResult.discrepancies.size}")
            
            return ReconciliationResult(
                success = true,
                reconciliationRecord = savedRecord,
                discrepancies = reconciliationResult.discrepancies
            )
            
        } catch (e: Exception) {
            logger.error("Reconciliation failed", e)
            return ReconciliationResult(
                success = false,
                reconciliationRecord = null,
                errorMessage = "대사 처리 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    override fun getReconciliationRecord(platform: Platform, reconciliationDate: LocalDate): ReconciliationRecord? {
        return reconciliationRepository.findByPlatformAndDate(platform, reconciliationDate)
    }
    
    /**
     * 상세 대사 수행
     */
    private fun performDetailedReconciliation(
        internalPayments: List<com.example.integration.domain.Payment>,
        externalPayments: List<com.example.integration.domain.Payment>
    ): DetailedReconciliationResult {
        val discrepancies = mutableListOf<DiscrepancyDetail>()
        
        // 내부 거래별로 외부 거래와 매칭
        val externalPaymentMap = externalPayments.associateBy { it.purchaseToken }
        
        for (internalPayment in internalPayments) {
            val externalPayment = externalPaymentMap[internalPayment.purchaseToken]
            
            when {
                externalPayment == null -> {
                    discrepancies.add(
                        DiscrepancyDetail(
                            transactionId = internalPayment.transactionId,
                            internalAmount = internalPayment.amount.toString(),
                            externalAmount = null,
                            reason = "External transaction not found"
                        )
                    )
                }
                internalPayment.amount.compareTo(externalPayment.amount) != 0 -> {
                    discrepancies.add(
                        DiscrepancyDetail(
                            transactionId = internalPayment.transactionId,
                            internalAmount = internalPayment.amount.toString(),
                            externalAmount = externalPayment.amount.toString(),
                            reason = "Amount mismatch"
                        )
                    )
                }
                internalPayment.status != externalPayment.status -> {
                    discrepancies.add(
                        DiscrepancyDetail(
                            transactionId = internalPayment.transactionId,
                            internalAmount = internalPayment.amount.toString(),
                            externalAmount = externalPayment.amount.toString(),
                            reason = "Status mismatch: internal=${internalPayment.status}, external=${externalPayment.status}"
                        )
                    )
                }
            }
        }
        
        // 외부에만 있는 거래 확인
        val internalPaymentMap = internalPayments.associateBy { it.purchaseToken }
        for (externalPayment in externalPayments) {
            if (!internalPaymentMap.containsKey(externalPayment.purchaseToken)) {
                discrepancies.add(
                    DiscrepancyDetail(
                        transactionId = externalPayment.transactionId,
                        internalAmount = null,
                        externalAmount = externalPayment.amount.toString(),
                        reason = "Internal transaction not found"
                    )
                )
            }
        }
        
        return DetailedReconciliationResult(discrepancies)
    }
}

/**
 * 상세 대사 결과
 */
private data class DetailedReconciliationResult(
    val discrepancies: List<DiscrepancyDetail>
)