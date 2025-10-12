package com.example.dto

enum class RTDNNotificationType(val code: Int, val description: String) {
    SUBSCRIPTION_RECOVERED(1, "구독 복구"),
    SUBSCRIPTION_RENEWED(2, "구독 갱신"),
    SUBSCRIPTION_CANCELED(3, "구독 취소"),
    SUBSCRIPTION_PURCHASED(4, "구독 구매"),
    SUBSCRIPTION_ON_HOLD(5, "구독 일시정지"),
    SUBSCRIPTION_IN_GRACE_PERIOD(6, "구독 유예기간"),
    SUBSCRIPTION_RESTARTED(7, "구독 재시작"),
    SUBSCRIPTION_PRICE_CHANGE_CONFIRMED(8, "가격 변경 확인"),
    SUBSCRIPTION_DEFERRED(9, "구독 연기"),
    SUBSCRIPTION_PAUSED(10, "구독 일시정지"),
    SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED(11, "일시정지 일정 변경"),
    SUBSCRIPTION_REVOKED(12, "구독 취소"),
    SUBSCRIPTION_EXPIRED(13, "구독 만료");

    companion object {
        fun fromCode(code: Int): RTDNNotificationType? {
            return values().find { it.code == code }
        }
    }
}