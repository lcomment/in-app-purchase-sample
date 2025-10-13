package com.example.domain.payment.completion

enum class CompletionMethod {
    SERVER_ACKNOWLEDGE,     // Google Play - 서버에서 직접 Acknowledge
    CLIENT_FINISH,          // App Store - 클라이언트에서 finishTransaction
    AUTOMATIC              // 자동 처리 (일부 케이스)
}