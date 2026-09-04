package com.example.Chatting_Server_Project.config;

import com.example.Chatting_Server_Project.traffic_test.MessageMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final MessageMetrics messageMetrics;

    // 외부 Configuration에서 @Bean으로 명시적 분리 등록한 전용 스레드 풀 의존성 주입
    private final ThreadPoolTaskExecutor clientInboundExecutor;
    private final ThreadPoolTaskExecutor clientOutboundExecutor;

    // beforeHandle/afterMessageHandled는 항상 같은 outbound 워커 스레드에서 쌍으로 호출되므로
    // ThreadLocal로 안전하게 측정 가능 (처리 시간만 — 큐 대기 시간은 포함 안 됨)
    private static final ThreadLocal<Long> OUTBOUND_TASK_START_NANOS = new ThreadLocal<>();

    // preSend는 채널 진입 직전 "발행 스레드"(broadcastExecutor 등)에서 호출되고,
    // afterMessageHandled는 "실행 스레드"(다를 수 있음, 큐를 거쳐 옴)에서 호출되므로
    // 스레드가 바뀌어도 값이 살아남는 메시지 헤더로 전달해야 종단(E2E) 지연을 잴 수 있음
    private static final String SUBMIT_NANOS_HEADER = "outboundSubmitNanos";

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    // 개별 세션 소켓이 느려서 자기 몫의 버퍼를 못 비우는 경우, 무한정 쌓이지 않고 강제 종료되도록 상한을 둠.
    // 10초는 너무 길어서 느린 소켓 하나가 outbound 워커 스레드를 그만큼(+감지 지연분) 붙잡고
    // 있었음(max latency 20~30초로 실측) — 3초로 줄여서 막힌 스레드를 더 빨리 회수, 같은
    // 스레드 수로도 실질 처리량을 올림
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(3 * 1000);
        registration.setSendBufferSizeLimit(512 * 1024);
    }

    // configureClientInboundChannel을 명시적으로 설정하지 않으면 Spring이 내부적으로 어떤 실행기를 쓰는지
    // 불명확해서(batchFlushExecutor의 워커 스레드가 SendTask를 처리하는 것으로 의심되는 정황 발견),
    // batchFlushExecutor와 절대 겹치지 않도록 clientInboundChannel 전용 실행기를 명시적으로 분리함.
    //
    // [수정 내역]
    // 메서드 내부에서 new 연산자로 스레드 풀 객체를 직접 생성하여 주입하던 방식을 제거.
    // 스프링 컨테이너 빈 생명주기를 벗어나 자동 구성 로직에 의해 공용 빈(batchFlushExecutor)으로
    // 덮어씌워지는 문제를 방지하기 위해 빈으로 관리되는 전용 실행기(clientInboundExecutor)를 주입받아 매핑.
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(clientInboundExecutor);
    }

    /*
     * // [수정 전 코드 백업]
     * 
     * @Override
     * public void configureClientInboundChannel(ChannelRegistration registration) {
     * ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
     * executor.setCorePoolSize(20);
     * executor.setMaxPoolSize(50);
     * executor.setQueueCapacity(5000);
     * executor.setKeepAliveSeconds(60);
     * executor.setThreadNamePrefix("clientInboundChannel-");
     * executor.setRejectedExecutionHandler((task, exec) -> {
     * messageMetrics.outboundRejected();
     * new ThreadPoolExecutor.CallerRunsPolicy().rejectedExecution(task, exec);
     * });
     * 
     * executor.initialize();
     * registration.taskExecutor(executor);
     * }
     */

    // heap dump로 확인된 OOM 원인 지점: clientOutboundChannel의 공유 워크큐가
    // 무제한(LinkedBlockingQueue)이라
    // 브로드캐스트 팬아웃(SendTask) 생산 속도가 처리 속도를 넘어서면 계속 쌓이다 터짐.
    // -> 큐를 유한하게 제한하고, 꽉 찼을 때는 발행 스레드가 직접 떠맡게 해서(CallerRunsPolicy) 자연스럽게 배압을 걸도록 함.
    // corePoolSize/maxPoolSize/queueCapacity는 실측 전 임시 안전값이며, afterMessageHandled에서
    // 재는
    // MessageMetrics의 outbound latency(W)로 Little's Law 재계산 후 조정 필요.
    //
    // [수정 내역]
    // Inbound 채널과 동일한 이유로 객체 직접 생성 방식을 제거하고,
    // 외부에서 주입받은 전용 빈(clientOutboundExecutor)을 매핑하여 배압(Backpressure)과 스레드 격리성을 강제함.
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(clientOutboundExecutor);
        registration.interceptors(new ExecutorChannelInterceptor() {

            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                return MessageBuilder.fromMessage(message)
                        .setHeader(SUBMIT_NANOS_HEADER, System.nanoTime())
                        .build();
            }

            @Override
            public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
                OUTBOUND_TASK_START_NANOS.set(System.nanoTime());
                return message;
            }

            @Override
            public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler,
                    Exception ex) {
                Long processingStart = OUTBOUND_TASK_START_NANOS.get();
                if (processingStart != null) {
                    messageMetrics.recordOutboundLatency(System.nanoTime() - processingStart);
                    OUTBOUND_TASK_START_NANOS.remove();
                }

                Long submitNanos = (Long) message.getHeaders().get(SUBMIT_NANOS_HEADER);
                if (submitNanos != null) {
                    messageMetrics.recordOutboundEndToEndLatency(System.nanoTime() - submitNanos);
                }

                if (ex == null) {
                    // CallerRunsPolicy가 발동하면 broadcastExecutor 스레드가 이 SendTask를
                    // 직접 실행하므로, 그 경우에만 스레드 이름이 "broadcast-"로 시작함 —
                    // 이를 근거로 "CallerRunsPolicy로 넘어간 발송도 예외 없이 정상 완료됐다"를
                    // 카운트/샘플 로깅함
                    if (Thread.currentThread().getName().startsWith("broadcast-")) {
                        messageMetrics.outboundRejectedRunSucceeded();
                    }
                } else {
                    messageMetrics.outboundSendFailed(ex);
                }
            }
        });
    }

    /*
     * // [수정 전 코드 백업]
     * 
     * @Override
     * public void configureClientOutboundChannel(ChannelRegistration registration)
     * {
     * ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
     * executor.setCorePoolSize(20);
     * executor.setMaxPoolSize(50);
     * executor.setQueueCapacity(5000);
     * executor.setKeepAliveSeconds(60);
     * executor.setThreadNamePrefix("clientOutboundChannel-");
     * executor.setRejectedExecutionHandler((task, exec) -> {
     * messageMetrics.outboundRejected();
     * new ThreadPoolExecutor.CallerRunsPolicy().rejectedExecution(task, exec);
     * });
     * 
     * executor.initialize();
     * registration.taskExecutor(executor);
     * registration.interceptors(new ExecutorChannelInterceptor() {
     * 
     * @Override
     * public Message<?> beforeHandle(Message<?> message, MessageChannel channel,
     * MessageHandler handler) {
     * OUTBOUND_TASK_START_NANOS.set(System.nanoTime());
     * return message;
     * }
     * 
     * @Override
     * public void afterMessageHandled(Message<?> message, MessageChannel channel,
     * MessageHandler handler,
     * Exception ex) {
     * Long start = OUTBOUND_TASK_START_NANOS.get();
     * if (start != null) {
     * messageMetrics.recordOutboundLatency(System.nanoTime() - start);
     * OUTBOUND_TASK_START_NANOS.remove();
     * }
     * }
     * });
     * }
     */
}