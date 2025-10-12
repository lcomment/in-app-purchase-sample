package com.example.service

import com.example.config.AppStoreConfig
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.LocalDateTime

@Service
class AppStoreAcknowledgeService(
    private val appStoreConfig: AppStoreConfig,
    private val restClient: RestClient
) {
    
    /**
     * App Store에서는 Server-to-Server 방식의 명시적인 Acknowledge API가 없음
     * 대신 다음과 같은 방식으로 처리:
     * 
     * 1. 클라이언트에서 SKPaymentQueue.default().finishTransaction() 호출
     * 2. 서버에서는 거래 상태를 추적하여 완료 처리
     * 3. App Store Connect API로 거래 상태 확인
     */
    
    /**
     * App Store Connect API를 통한 거래 상태 확인
     * 실제 Acknowledge는 클라이언트에서 finishTransaction으로 처리
     */
    fun checkTransactionStatus(
        originalTransactionId: String
    ): TransactionStatusResult {
        return try {
            val jwt = appStoreConfig.appStoreJwtToken()
            
            val response = restClient.method(HttpMethod.GET)
                .uri("/inApps/v1/history/{originalTransactionId}", originalTransactionId)
                .header("Authorization", "Bearer $jwt")
                .retrieve()
                .toEntity(String::class.java)
            
            when (response.statusCode) {
                HttpStatus.OK -> {
                    TransactionStatusResult(
                        success = true,
                        isFinished = true,
                        message = "Transaction confirmed"
                    )
                }
                HttpStatus.NOT_FOUND -> {
                    TransactionStatusResult(
                        success = false,
                        isFinished = false,
                        message = "Transaction not found"
                    )
                }
                else -> {
                    TransactionStatusResult(
                        success = false,
                        isFinished = false,
                        message = "Failed to check transaction status"
                    )
                }
            }
        } catch (e: Exception) {
            println("Failed to check transaction status for originalTransactionId: $originalTransactionId - ${e.message}")
            TransactionStatusResult(
                success = false,
                isFinished = false,
                message = "Error checking transaction: ${e.message}"
            )
        }
    }
    
    /**
     * 서버 측에서 거래 완료 상태를 기록
     * App Store는 클라이언트에서 finishTransaction을 호출해야 하므로
     * 서버에서는 상태 추적만 수행
     */
    fun markTransactionAsFinished(
        originalTransactionId: String,
        transactionId: String,
        userId: String,
        productId: String
    ): AcknowledgeResult {
        return try {
            // 실제로는 데이터베이스에 완료 상태 기록
            println("Marking transaction as finished - originalTransactionId: $originalTransactionId")
            
            AcknowledgeResult(
                transactionId = transactionId,
                originalTransactionId = originalTransactionId,
                success = true,
                finishedAt = LocalDateTime.now(),
                message = "Transaction marked as finished on server side"
            )
        } catch (e: Exception) {
            AcknowledgeResult(
                transactionId = transactionId,
                originalTransactionId = originalTransactionId,
                success = false,
                finishedAt = null,
                message = "Failed to mark transaction as finished: ${e.message}"
            )
        }
    }
    
    /**
     * 배치로 여러 거래 완료 처리
     */
    fun batchMarkTransactionsAsFinished(
        transactions: List<TransactionFinishRequest>
    ): BatchTransactionFinishResult {
        val results = mutableListOf<AcknowledgeResult>()
        var successCount = 0
        var failureCount = 0
        
        transactions.forEach { request ->
            val result = markTransactionAsFinished(
                originalTransactionId = request.originalTransactionId,
                transactionId = request.transactionId,
                userId = request.userId,
                productId = request.productId
            )
            
            results.add(result)
            if (result.success) successCount++ else failureCount++
        }
        
        return BatchTransactionFinishResult(
            totalCount = transactions.size,
            successCount = successCount,
            failureCount = failureCount,
            results = results
        )
    }
    
    /**
     * 미완료 거래 조회 및 처리
     * 클라이언트에서 finishTransaction을 놓친 경우를 대비
     */
    fun processPendingTransactions(userId: String): List<AcknowledgeResult> {
        // 실제로는 데이터베이스에서 미완료 거래 조회
        val pendingTransactions = findPendingTransactions(userId)
        
        return pendingTransactions.map { transaction ->
            val statusResult = checkTransactionStatus(transaction.originalTransactionId)
            
            if (statusResult.isFinished) {
                markTransactionAsFinished(
                    originalTransactionId = transaction.originalTransactionId,
                    transactionId = transaction.transactionId,
                    userId = transaction.userId,
                    productId = transaction.productId
                )
            } else {
                AcknowledgeResult(
                    transactionId = transaction.transactionId,
                    originalTransactionId = transaction.originalTransactionId,
                    success = false,
                    finishedAt = null,
                    message = "Transaction still pending completion on client side"
                )
            }
        }
    }
    
    private fun findPendingTransactions(userId: String): List<TransactionFinishRequest> {
        // Mock 데이터 - 실제로는 데이터베이스 조회
        return emptyList()
    }
}

data class TransactionStatusResult(
    val success: Boolean,
    val isFinished: Boolean,
    val message: String
)

data class AcknowledgeResult(
    val transactionId: String,
    val originalTransactionId: String,
    val success: Boolean,
    val finishedAt: LocalDateTime?,
    val message: String
)

data class TransactionFinishRequest(
    val originalTransactionId: String,
    val transactionId: String,
    val userId: String,
    val productId: String
)

data class BatchTransactionFinishResult(
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val results: List<AcknowledgeResult>
) {
    val isAllSuccess: Boolean
        get() = failureCount == 0
    
    val hasFailures: Boolean
        get() = failureCount > 0
}