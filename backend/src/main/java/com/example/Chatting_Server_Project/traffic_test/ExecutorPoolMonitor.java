package com.example.Chatting_Server_Project.traffic_test;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

// outbound latency가 튈 때 원인이 (a) 스레드 풀이 다 차서 대기했는지 (풀 사이징 문제)
// 아니면 (b) 스레드는 남아있는데 특정 소켓의 write 자체가 오래 걸렸는지(개별 네트워크/클라이언트 문제)를
// 구분하기 위한 계측. clientInbound/clientOutbound/batchFlush/broadcast 네 풀의 activeCount/queue를
// 1초 간격으로 같이 찍어서 MessageMetrics의 outbound latency 스파이크 시점과 대조할 수 있게 함.
// broadcastExecutor 분리 이후로는 inbound가 outbound 포화 시점에도 그대로 낮게 유지되는지가
// 핵심 검증 포인트.
@Component
@Slf4j
@RequiredArgsConstructor
public class ExecutorPoolMonitor {

    private final ThreadPoolTaskExecutor clientInboundExecutor;
    private final ThreadPoolTaskExecutor clientOutboundExecutor;
    private final ThreadPoolTaskExecutor batchFlushExecutor;
    private final ThreadPoolTaskExecutor broadcastExecutor;

    private static final String DIRECTORY_PATH = "C:/chat_logs/logs";
    private static final String FILE_NAME = DIRECTORY_PATH + "/pool_stats.csv";

    // 테스트 구간 전체에서 관측된 최댓값 (최종 요약용)
    private final AtomicInteger inboundPeakActive = new AtomicInteger();
    private final AtomicInteger inboundPeakQueue = new AtomicInteger();
    private final AtomicInteger outboundPeakActive = new AtomicInteger();
    private final AtomicInteger outboundPeakQueue = new AtomicInteger();
    private final AtomicInteger flushPeakActive = new AtomicInteger();
    private final AtomicInteger flushPeakQueue = new AtomicInteger();
    private final AtomicInteger broadcastPeakActive = new AtomicInteger();
    private final AtomicInteger broadcastPeakQueue = new AtomicInteger();

    @Scheduled(fixedRate = 1000)
    public void sampleAndLog() {
        int inboundActive = clientInboundExecutor.getActiveCount();
        int inboundMax = clientInboundExecutor.getMaxPoolSize();
        int inboundQueue = clientInboundExecutor.getThreadPoolExecutor().getQueue().size();

        int outboundActive = clientOutboundExecutor.getActiveCount();
        int outboundMax = clientOutboundExecutor.getMaxPoolSize();
        int outboundQueue = clientOutboundExecutor.getThreadPoolExecutor().getQueue().size();

        int flushActive = batchFlushExecutor.getActiveCount();
        int flushMax = batchFlushExecutor.getMaxPoolSize();
        int flushQueue = batchFlushExecutor.getThreadPoolExecutor().getQueue().size();

        int broadcastActive = broadcastExecutor.getActiveCount();
        int broadcastMax = broadcastExecutor.getMaxPoolSize();
        int broadcastQueue = broadcastExecutor.getThreadPoolExecutor().getQueue().size();

        inboundPeakActive.updateAndGet(prev -> Math.max(prev, inboundActive));
        inboundPeakQueue.updateAndGet(prev -> Math.max(prev, inboundQueue));
        outboundPeakActive.updateAndGet(prev -> Math.max(prev, outboundActive));
        outboundPeakQueue.updateAndGet(prev -> Math.max(prev, outboundQueue));
        flushPeakActive.updateAndGet(prev -> Math.max(prev, flushActive));
        flushPeakQueue.updateAndGet(prev -> Math.max(prev, flushQueue));
        broadcastPeakActive.updateAndGet(prev -> Math.max(prev, broadcastActive));
        broadcastPeakQueue.updateAndGet(prev -> Math.max(prev, broadcastQueue));

        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        String line = String.format("%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d\n",
                time,
                inboundActive, inboundMax, inboundQueue,
                outboundActive, outboundMax, outboundQueue,
                flushActive, flushMax, flushQueue,
                broadcastActive, broadcastMax, broadcastQueue);

        // outbound 풀이 꽉 찬 채로 큐까지 쌓이는 중이면(=스레드 굶주림 의심) 바로 눈에 띄게 warn
        if (outboundActive >= outboundMax && outboundQueue > 0) {
            log.warn("[POOL] outbound 포화 감지: active={}/{}, queue={}", outboundActive, outboundMax, outboundQueue);
        }

        try {
            Files.createDirectories(Paths.get(DIRECTORY_PATH));
            if (!Files.exists(Paths.get(FILE_NAME))) {
                String header = "Timestamp,Inbound_Active,Inbound_Max,Inbound_Queue,"
                        + "Outbound_Active,Outbound_Max,Outbound_Queue,"
                        + "Flush_Active,Flush_Max,Flush_Queue,"
                        + "Broadcast_Active,Broadcast_Max,Broadcast_Queue\n";
                Files.write(Paths.get(FILE_NAME), header.getBytes());
            }
            Files.write(Paths.get(FILE_NAME), line.getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("[POOL] 통계 파일 저장 실패: {}", e.getMessage());
        }
    }

    // [수정 전] initialDelay=100000, fixedDelay=Long.MAX_VALUE로 앱 시작 100초 뒤 딱 한 번만
    // 찍었었는데, 실제 테스트가 100초 이후에도 계속되는 경우(예: 600 VU 2차 테스트) 그 뒤에
    // 발생한 진짜 피크를 이 로그로는 영영 확인할 수 없는 사각지대가 있었음 — 100초 시점엔
    // Outbound active peak=104였는데 12초 뒤 active=120/120(신규 상한)까지 찬 게 WARN으로만
    // 남고 피크 요약에는 반영이 안 됨. AtomicInteger 자체는 계속 갱신되고 있으니(리셋 안 함)
    // 로그만 주기적으로 다시 찍으면 됨 — 20초마다 최신 누적 피크를 재출력
    @Scheduled(initialDelay = 100000, fixedDelay = 20000)
    public void logPeakSummary() {
        log.info(
                "=== [POOL 피크 요약] Inbound active={}/queue={} | Outbound active={}/queue={} | Flush active={}/queue={} | Broadcast active={}/queue={} ===",
                inboundPeakActive.get(), inboundPeakQueue.get(),
                outboundPeakActive.get(), outboundPeakQueue.get(),
                flushPeakActive.get(), flushPeakQueue.get(),
                broadcastPeakActive.get(), broadcastPeakQueue.get());
    }
}
