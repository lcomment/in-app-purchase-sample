package com.example.integration.infrastructure.persistence

import com.example.integration.application.port.out.MemberSubscriptionRepositoryPort
import com.example.integration.domain.MemberSubscription
import com.example.integration.domain.MemberSubscriptionStatus
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 회원 구독 정보 In-Memory Repository 구현체 (Infrastructure Layer)
 */
@Repository
class InMemoryMemberSubscriptionRepository : MemberSubscriptionRepositoryPort {
    
    private val memberSubscriptions = ConcurrentHashMap<Long, MemberSubscription>()
    private val purchaseTokenIndex = ConcurrentHashMap<String, Long>()
    private val idGenerator = AtomicLong(1)
    
    override fun save(memberSubscription: MemberSubscription): MemberSubscription {
        val id = if (memberSubscription.id == 0L) {
            idGenerator.getAndIncrement()
        } else {
            memberSubscription.id
        }
        
        val savedSubscription = if (memberSubscription.id == 0L) {
            MemberSubscription(
                id = id,
                member = memberSubscription.member,
                subscription = memberSubscription.subscription,
                payment = memberSubscription.payment,
                startDateTime = memberSubscription.startDateTime,
                endDateTime = memberSubscription.endDateTime,
                status = memberSubscription.status
            )
        } else {
            memberSubscription
        }
        
        memberSubscriptions[id] = savedSubscription
        
        // Purchase Token 인덱스 업데이트
        memberSubscription.payment?.purchaseToken?.let { token ->
            purchaseTokenIndex[token] = id
        }
        
        return savedSubscription
    }
    
    override fun findById(id: Long): MemberSubscription? {
        return memberSubscriptions[id]
    }
    
    override fun findActiveByMemberId(memberId: Long): MemberSubscription? {
        return memberSubscriptions.values
            .firstOrNull { it.member.id == memberId && it.status == MemberSubscriptionStatus.ACTIVE }
    }
    
    override fun findAllByMemberId(memberId: Long): List<MemberSubscription> {
        return memberSubscriptions.values
            .filter { it.member.id == memberId }
            .sortedByDescending { it.startDateTime }
    }
    
    override fun findAllByStatus(status: MemberSubscriptionStatus): List<MemberSubscription> {
        return memberSubscriptions.values
            .filter { it.status == status }
            .sortedByDescending { it.startDateTime }
    }
    
    override fun deleteById(id: Long) {
        val subscription = memberSubscriptions.remove(id)
        subscription?.payment?.purchaseToken?.let { token ->
            purchaseTokenIndex.remove(token)
        }
    }
    
    override fun findByPurchaseToken(purchaseToken: String): MemberSubscription? {
        val id = purchaseTokenIndex[purchaseToken] ?: return null
        return memberSubscriptions[id]
    }
}