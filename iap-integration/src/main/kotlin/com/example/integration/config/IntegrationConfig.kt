package com.example.integration.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Integration 모듈 설정
 */
@Configuration
class IntegrationConfig {
    
    /**
     * ObjectMapper Bean 설정
     */
    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
    }
}