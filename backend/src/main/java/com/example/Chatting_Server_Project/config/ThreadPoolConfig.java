package com.example.Chatting_Server_Project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "batchFlushExecutor")
    public Executor batchFlushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);        // 기본 일꾼
        executor.setMaxPoolSize(20);         // 최대 일꾼
        executor.setQueueCapacity(500);      // 대기열 (메모리 상황 고려)
        executor.setThreadNamePrefix("Flush-"); // 디버깅 용

        // 서버 종료 시 남은 작업 처리 설정
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }


}
