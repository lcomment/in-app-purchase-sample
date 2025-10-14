package com.example.integration.application.port.out

import com.example.integration.domain.MemberSubscription
import com.example.integration.domain.MemberSubscriptionStatus

/**
 * 회원 구독 정보 Repository (출력 포트)
 */
interface MemberSubscriptionRepositoryPort {
    
    /**
     * 회원 구독 정보 저장
     */
    fun save(memberSubscription: MemberSubscription): MemberSubscription
    
    /**
     * 회원 구독 정보 조회 (ID로)
     */
    fun findById(id: Long): MemberSubscription?
    
    /**
     * 회원의 활성 구독 조회
     */
    fun findActiveByMemberId(memberId: Long): MemberSubscription?
    
    /**
     * 회원의 모든 구독 조회
     */
    fun findAllByMemberId(memberId: Long): List<MemberSubscription>
    
    /**
     * 구독 상태로 조회
     */
    fun findAllByStatus(status: MemberSubscriptionStatus): List<MemberSubscription>
    
    /**
     * 회원 구독 정보 삭제
     */
    fun deleteById(id: Long)
    
    /**
     * Purchase Token으로 조회 (중복 결제 방지용)
     */
    fun findByPurchaseToken(purchaseToken: String): MemberSubscription?
}