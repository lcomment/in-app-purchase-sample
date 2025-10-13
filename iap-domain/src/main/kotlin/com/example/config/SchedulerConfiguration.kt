package com.example.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.Executor

/**
 * 스케줄러 설정
 * 
 * Spring의 스케줄링 기능을 활성화하고 TaskScheduler를 구성합니다.
 */
@Configuration
@EnableScheduling
class SchedulerConfiguration {
    
    /**
     * 정산 작업 전용 TaskScheduler
     */
    @Bean("settlementTaskScheduler")
    fun settlementTaskScheduler(): TaskScheduler {
        return ThreadPoolTaskScheduler().apply {
            poolSize = 5
            setThreadNamePrefix("settlement-scheduler-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(30)
            initialize()
        }
    }
    
    /**
     * 비동기 작업 실행자
     */
    @Bean("settlementAsyncExecutor")
    fun settlementAsyncExecutor(): Executor {
        return ThreadPoolTaskScheduler().apply {
            poolSize = 3
            setThreadNamePrefix("settlement-async-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(60)
            initialize()
        }
    }
}