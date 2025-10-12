package com.example.dto

enum class AppStoreNotificationType(val typeName: String, val description: String) {
    SUBSCRIBED("SUBSCRIBED", "새로운 구독 시작"),
    DID_CHANGE_RENEWAL_PREF("DID_CHANGE_RENEWAL_PREF", "자동 갱신 설정 변경"),
    DID_CHANGE_RENEWAL_STATUS("DID_CHANGE_RENEWAL_STATUS", "갱신 상태 변경"),
    DID_FAIL_TO_RENEW("DID_FAIL_TO_RENEW", "갱신 실패"),
    DID_RENEW("DID_RENEW", "구독 갱신 성공"),
    EXPIRED("EXPIRED", "구독 만료"),
    GRACE_PERIOD_EXPIRED("GRACE_PERIOD_EXPIRED", "유예 기간 만료"),
    OFFER_REDEEMED("OFFER_REDEEMED", "오퍼 사용"),
    PRICE_INCREASE("PRICE_INCREASE", "가격 인상"),
    REFUND("REFUND", "환불"),
    REFUND_DECLINED("REFUND_DECLINED", "환불 거절"),
    RENEWAL_EXTENDED("RENEWAL_EXTENDED", "갱신 연장"),
    REVOKE("REVOKE", "구독 취소"),
    TEST("TEST", "테스트 알림");

    companion object {
        fun fromTypeName(typeName: String): AppStoreNotificationType? {
            return values().find { it.typeName == typeName }
        }
    }
}

enum class AppStoreNotificationSubtype(val subtypeName: String, val description: String) {
    INITIAL_BUY("INITIAL_BUY", "최초 구매"),
    RESUBSCRIBE("RESUBSCRIBE", "재구독"),
    DOWNGRADE("DOWNGRADE", "다운그레이드"),
    UPGRADE("UPGRADE", "업그레이드"),
    AUTO_RENEW_ENABLED("AUTO_RENEW_ENABLED", "자동 갱신 활성화"),
    AUTO_RENEW_DISABLED("AUTO_RENEW_DISABLED", "자동 갱신 비활성화"),
    VOLUNTARY("VOLUNTARY", "자발적 취소"),
    BILLING_RETRY("BILLING_RETRY", "결제 재시도"),
    PRICE_INCREASE("PRICE_INCREASE", "가격 인상"),
    GRACE_PERIOD("GRACE_PERIOD", "유예 기간"),
    BILLING_RECOVERY("BILLING_RECOVERY", "결제 복구"),
    PENDING("PENDING", "대기 중"),
    ACCEPTED("ACCEPTED", "승인됨");

    companion object {
        fun fromSubtypeName(subtypeName: String): AppStoreNotificationSubtype? {
            return values().find { it.subtypeName == subtypeName }
        }
    }
}