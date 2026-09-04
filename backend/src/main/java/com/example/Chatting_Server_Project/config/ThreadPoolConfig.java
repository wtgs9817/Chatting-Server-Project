package com.example.Chatting_Server_Project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadPoolConfig {

    // 반환 타입을 Executor가 아닌 ThreadPoolTaskExecutor로 명시해야 ExecutorPoolMonitor 등에서
    // getActiveCount()/getThreadPoolExecutor() 같은 계측용 메서드로 이 빈을 주입받을 수 있음
    // (스프링은 @Bean 메서드의 선언된 반환 타입으로 빈 타입을 등록하므로, Executor로 선언하면
    // 런타임 타입이 ThreadPoolTaskExecutor여도 ThreadPoolTaskExecutor 타입으로는 주입 불가)
    @Bean(name = "batchFlushExecutor")
    public ThreadPoolTaskExecutor batchFlushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50); // 기본 일꾼
        executor.setMaxPoolSize(100); // 최대 일꾼
        executor.setQueueCapacity(5000); // 대기열 (메모리 상황 고려)
        executor.setThreadNamePrefix("Flush-"); // 디버깅 용

        // 서버 종료 시 남은 작업 처리 설정
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }
}
