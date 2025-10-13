package com.example.integration.application.service

import com.example.integration.application.port.out.PlatformAdapterPort
import com.example.integration.domain.Platform
import org.springframework.stereotype.Component

/**
 * 플랫폼 어댑터 팩토리 (팩토리 패턴)
 */
@Component
class PlatformAdapterFactory(
    private val adapters: List<PlatformAdapterPort>
) {
    
    private val adapterMap: Map<Platform, PlatformAdapterPort> = 
        adapters.associateBy { it.getSupportedPlatform() }
    
    /**
     * 플랫폼에 맞는 어댑터 반환
     */
    fun getAdapter(platform: Platform): PlatformAdapterPort {
        return adapterMap[platform] 
            ?: throw IllegalArgumentException("Unsupported platform: $platform")
    }
    
    /**
     * 지원하는 플랫폼 목록 반환
     */
    fun getSupportedPlatforms(): Set<Platform> = adapterMap.keys
}