package com.example.integration.infrastructure.webhook

import com.example.integration.domain.Platform
import com.example.integration.domain.WebhookNotification

/**
 * 플랫폼별 웹훅 핸들러 인터페이스
 */
interface WebhookHandler {
    
    /**
     * 해당 플랫폼의 웹훅을 처리할 수 있는지 확인
     */
    fun canHandle(platform: Platform): Boolean
    
    /**
     * 웹훅 페이로드를 파싱하여 WebhookNotification으로 변환
     */
    fun parseNotification(payload: String): WebhookNotification
}