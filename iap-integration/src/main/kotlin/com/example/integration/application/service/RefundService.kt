package com.example.integration.application.service

import com.example.integration.application.port.`in`.RefundUseCase
import com.example.integration.application.port.`in`.RefundRequest
import com.example.integration.application.port.`in`.RefundResult
import com.example.integration.application.port.out.RefundRepositoryPort
import com.example.integration.application.port.out.PaymentRepositoryPort
import com.example.integration.domain.Platform
import com.example.integration.domain.Refund
import com.example.integration.domain.RefundStatus
import com.example.integration.domain.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * 환불 처리 서비스 (Application Layer)
 */
@Service
@Transactional
class RefundService(
    private val platformAdapterFactory: PlatformAdapterFactory,
    private val refundRepository: RefundRepositoryPort,
    private val paymentRepository: PaymentRepositoryPort
) : RefundUseCase {
    
    private val logger = LoggerFactory.getLogger(RefundService::class.java)
    
    override fun requestRefund(request: RefundRequest): RefundResult {
        logger.info("Processing refund request: paymentId=${request.paymentId}, platform=${request.platform}")
        
        try {
            // 1. 결제 정보 확인
            val payment = paymentRepository.findById(request.paymentId)
            if (payment == null) {
                return RefundResult(
                    success = false,
                    refund = null,
                    errorMessage = "결제 정보를 찾을 수 없습니다"
                )
            }
            
            // 2. 환불 가능 여부 확인
            if (payment.status == PaymentStatus.REFUNDED) {
                return RefundResult(
                    success = false,
                    refund = null,
                    errorMessage = "이미 환불된 결제입니다"
                )
            }
            
            // 3. 중복 환불 요청 확인
            val existingRefunds = refundRepository.findByPaymentId(request.paymentId)
            val pendingRefund = existingRefunds.find { 
                it.status in listOf(RefundStatus.REQUESTED, RefundStatus.APPROVED, RefundStatus.PROCESSING) 
            }
            
            if (pendingRefund != null) {
                return RefundResult(
                    success = false,
                    refund = null,
                    errorMessage = "이미 처리 중인 환불 요청이 있습니다"
                )
            }
            
            // 4. 환불 금액 결정
            val refundAmount = request.amount ?: payment.amount
            if (refundAmount > payment.amount) {
                return RefundResult(
                    success = false,
                    refund = null,
                    errorMessage = "환불 금액이 원래 결제 금액을 초과할 수 없습니다"
                )
            }
            
            // 5. 환불 엔터티 생성
            val refund = Refund(
                id = UUID.randomUUID().toString(),
                paymentId = request.paymentId,
                subscriptionId = request.subscriptionId,
                userId = request.userId,
                platform = request.platform,
                amount = refundAmount,
                currency = request.currency,
                reason = request.reason,
                status = RefundStatus.REQUESTED,
                requestedAt = LocalDateTime.now(),
                customerNote = request.customerNote
            )
            
            // 6. 환불 요청 저장
            val savedRefund = refundRepository.save(refund)
            
            logger.info("Refund request created successfully: refundId=${savedRefund.id}")
            
            return RefundResult(
                success = true,
                refund = savedRefund
            )
            
        } catch (e: Exception) {
            logger.error("Refund request failed", e)
            return RefundResult(
                success = false,
                refund = null,
                errorMessage = "환불 요청 처리 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    override fun approveRefund(refundId: String, approvedBy: String): RefundResult {
        logger.info("Approving refund: refundId=$refundId, approvedBy=$approvedBy")
        
        try {
            val refund = refundRepository.findById(refundId)
            if (refund == null) {
                return RefundResult(
                    success = false,
                    refund = null,
                    errorMessage = "환불 요청을 찾을 수 없습니다"
                )
            }
            
            if (refund.status != RefundStatus.REQUESTED) {
                return RefundResult(
                    success = false,
                    refund = refund,
                    errorMessage = "승인할 수 없는 상태입니다: ${refund.status}"
                )
            }
            
            val approvedRefund = refund.approve(approvedBy)
            val savedRefund = refundRepository.save(approvedRefund)
            
            logger.info("Refund approved successfully: refundId=$refundId")
            
            return RefundResult(
                success = true,
                refund = savedRefund
            )
            
        } catch (e: Exception) {
            logger.error("Refund approval failed", e)
            return RefundResult(
                success = false,
                refund = null,
                errorMessage = "환불 승인 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    override fun rejectRefund(refundId: String, reason: String): RefundResult {
        logger.info("Rejecting refund: refundId=$refundId, reason=$reason")
        
        try {
            val refund = refundRepository.findById(refundId)
            if (refund == null) {
                return RefundResult(
                    success = false,
                    refund = null,
                    errorMessage = "환불 요청을 찾을 수 없습니다"
                )
            }
            
            if (refund.status != RefundStatus.REQUESTED) {
                return RefundResult(
                    success = false,
                    refund = refund,
                    errorMessage = "거부할 수 없는 상태입니다: ${refund.status}"
                )
            }
            
            val rejectedRefund = refund.reject(reason)
            val savedRefund = refundRepository.save(rejectedRefund)
            
            logger.info("Refund rejected successfully: refundId=$refundId")
            
            return RefundResult(
                success = true,
                refund = savedRefund
            )
            
        } catch (e: Exception) {
            logger.error("Refund rejection failed", e)
            return RefundResult(
                success = false,
                refund = null,
                errorMessage = "환불 거부 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    override fun processRefund(refundId: String): RefundResult {
        logger.info("Processing refund: refundId=$refundId")
        
        try {
            val refund = refundRepository.findById(refundId)
            if (refund == null) {
                return RefundResult(
                    success = false,
                    refund = null,
                    errorMessage = "환불 요청을 찾을 수 없습니다"
                )
            }
            
            if (!refund.isProcessable()) {
                return RefundResult(
                    success = false,
                    refund = refund,
                    errorMessage = "처리할 수 없는 상태입니다: ${refund.status}"
                )
            }
            
            // 처리 중 상태로 변경
            val processingRefund = refund.process()
            refundRepository.save(processingRefund)
            
            // 결제 정보 조회
            val payment = paymentRepository.findById(refund.paymentId)
            if (payment == null) {
                val failedRefund = processingRefund.fail("결제 정보를 찾을 수 없습니다")
                refundRepository.save(failedRefund)
                return RefundResult(
                    success = false,
                    refund = failedRefund,
                    errorMessage = "결제 정보를 찾을 수 없습니다"
                )
            }
            
            // 플랫폼별 환불 처리
            val adapter = platformAdapterFactory.getAdapter(refund.platform)
            val platformResult = adapter.processRefund(
                productId = payment.productId,
                purchaseToken = payment.purchaseToken,
                refundAmount = refund.amount.toString(),
                reason = refund.reason.name,
                packageName = if (refund.platform == Platform.GOOGLE_PLAY) "com.example.app" else null,
                bundleId = if (refund.platform == Platform.APP_STORE) "com.example.app" else null
            )
            
            val finalRefund = if (platformResult.success) {
                // 환불 완료
                val completedRefund = processingRefund.complete(platformResult.platformRefundId ?: "")
                
                // 결제 상태 업데이트
                val refundedPayment = payment.refund()
                paymentRepository.save(refundedPayment)
                
                completedRefund
            } else {
                // 환불 실패
                processingRefund.fail(platformResult.errorMessage ?: "플랫폼 환불 실패")
            }
            
            val savedRefund = refundRepository.save(finalRefund)
            
            logger.info("Refund processing completed: refundId=$refundId, success=${platformResult.success}")
            
            return RefundResult(
                success = platformResult.success,
                refund = savedRefund,
                errorMessage = if (!platformResult.success) platformResult.errorMessage else null
            )
            
        } catch (e: Exception) {
            logger.error("Refund processing failed", e)
            
            // 실패 상태로 업데이트
            try {
                val refund = refundRepository.findById(refundId)
                if (refund != null && refund.status == RefundStatus.PROCESSING) {
                    val failedRefund = refund.fail("처리 중 오류 발생: ${e.message}")
                    refundRepository.save(failedRefund)
                }
            } catch (updateException: Exception) {
                logger.error("Failed to update refund status to failed", updateException)
            }
            
            return RefundResult(
                success = false,
                refund = null,
                errorMessage = "환불 처리 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }
    
    override fun getRefundStatus(refundId: String): Refund? {
        return refundRepository.findById(refundId)
    }
    
    override fun getUserRefunds(userId: String, platform: Platform?): List<Refund> {
        return if (platform != null) {
            refundRepository.findByUserIdAndPlatform(userId, platform)
        } else {
            refundRepository.findByUserId(userId)
        }
    }
}