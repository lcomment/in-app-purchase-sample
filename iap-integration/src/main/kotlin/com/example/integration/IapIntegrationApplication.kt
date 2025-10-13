package com.example.integration

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * IAP Integration 애플리케이션 메인 클래스
 */
@SpringBootApplication
@EnableScheduling
class IapIntegrationApplication

fun main(args: Array<String>) {
    runApplication<IapIntegrationApplication>(*args)
}