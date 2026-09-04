package com.example.Chatting_Server_Project.traffic_test;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

@Component
@Slf4j
@RequiredArgsConstructor
@Getter
public class MessageMetrics {

    private final AtomicLong received = new AtomicLong(); // 서버가 수신한 메시지
    private final AtomicLong saved = new AtomicLong(); // 최종 저장하게된 메시지

    // 1분 후 최종 리포트 파일 저장용 누적 카운터 (초기화 안 함)
    private final AtomicLong totalReceived = new AtomicLong();
    private final AtomicLong totalSaved = new AtomicLong();

    // clientOutboundChannel의 SendTask 1건이 실제로 처리(dequeue~완료)되는 데 걸린 시간(W) 계측용
    private final LongAdder outboundTaskCount = new LongAdder();
    private final LongAdder outboundTaskTotalNanos = new LongAdder();
    private final AtomicLong outboundTaskMaxNanos = new AtomicLong();
    // outbound 큐가 꽉 차서 CallerRunsPolicy로 발행 스레드가 직접 떠맡은(=배압 걸린) 횟수.
    // 이 발행 스레드는 대부분 clientInboundChannel 워커이므로, 이 값이 크면
    // outbound 배압이 inbound 스레드를 잡아먹고 있다는 뜻
    private final AtomicLong outboundRejectedCount = new AtomicLong();
    // clientInboundExecutor 자체의 큐가 찬 횟수 (inbound 자체 처리량 한계, outbound와 별개)
    private final AtomicLong inboundRejectedCount = new AtomicLong();
    // broadcastExecutor 큐까지 찬 경우 — inbound 스레드를 지키기 위해 CallerRunsPolicy 대신
    // 그냥 버림. 이 값이 크면 브로드캐스트 유실이 실제로 발생하고 있다는 뜻이므로 반드시 지켜봐야 함
    private final AtomicLong broadcastDroppedCount = new AtomicLong();

    // ===== 종단(End-to-End) 지연 =====
    // recordOutboundLatency()는 beforeHandle~afterMessageHandled 구간(=큐에서 꺼내진 뒤 실제
    // 처리하는 시간)만 잰다. 큐 대기 시간이 빠져 있어 사용자 체감 지연을 과소평가함.
    // 이 지표는 preSend(=채널 진입 직전, 발행 스레드에서 호출)부터 afterMessageHandled까지,
    // 즉 큐 대기 + 처리 시간을 모두 포함한 실제 체감 지연.
    private final LongAdder e2eCount = new LongAdder();
    private final LongAdder e2eTotalNanos = new LongAdder();
    private final AtomicLong e2eMaxNanos = new AtomicLong();

    // p95 근사용 히스토그램. 수백만 건의 개별 값을 다 저장하면(정렬 비용/메모리) 부담이 크므로
    // 구간(bucket)별 카운트만 누적 — 정확한 p95가 아니라 "해당 구간 상한" 단위의 근사치.
    // 마지막 인덱스는 "3000ms(=sendTimeLimit) 초과" 버킷 — 여기 걸리면 강제 종료 위험 구간.
    private static final long[] E2E_BUCKET_UPPER_NANOS = {
            1_000_000L, 5_000_000L, 10_000_000L, 25_000_000L, 50_000_000L,
            100_000_000L, 250_000_000L, 500_000_000L, 1_000_000_000L,
            2_000_000_000L, 3_000_000_000L
    };
    private final AtomicLongArray e2eBuckets = new AtomicLongArray(E2E_BUCKET_UPPER_NANOS.length + 1);

    // ===== CallerRunsPolicy 처리 결과 검증용 =====
    // outboundRejectedCount(발동 횟수)와 별개로, 그 발송이 예외 없이 끝까지 완료됐는지 확인하기
    // 위한 카운터. afterMessageHandled에서 현재 스레드가 broadcastExecutor 소속(이름이
    // "broadcast-"로 시작)이면서 ex==null인 경우만 증가시킴 — 이 값이 outboundRejectedCount와
    // 거의 같이 늘고 outboundSendFailed가 0이면 "CallerRunsPolicy로 넘어간 발송도 전부 정상
    // 완료됐다"는 근거가 됨
    private final AtomicLong outboundRejectedRunSucceeded = new AtomicLong();
    // 실행 경로(정상 풀 처리든 CallerRunsPolicy든)와 무관하게, 실제 전송 자체가 예외로 실패한 횟수
    private final AtomicLong outboundSendFailed = new AtomicLong();

    public void receivedMessage() {
        received.incrementAndGet();
        totalReceived.incrementAndGet(); // 누적분 추가
    }

    public void savedMessage(int count) {
        saved.addAndGet(count);
        totalSaved.addAndGet(count); // 누적분 추가
    }

    public void recordOutboundLatency(long nanos) {
        outboundTaskCount.increment();
        outboundTaskTotalNanos.add(nanos);
        outboundTaskMaxNanos.updateAndGet(prev -> Math.max(prev, nanos));
    }

    public void outboundRejected() {
        outboundRejectedCount.incrementAndGet();
    }

    public void inboundRejected() {
        inboundRejectedCount.incrementAndGet();
    }

    public void broadcastDropped() {
        broadcastDroppedCount.incrementAndGet();
    }

    public void recordOutboundEndToEndLatency(long nanos) {
        e2eCount.increment();
        e2eTotalNanos.add(nanos);
        e2eMaxNanos.updateAndGet(prev -> Math.max(prev, nanos));

        int bucket = E2E_BUCKET_UPPER_NANOS.length; // 기본값: 3000ms 초과 버킷
        for (int i = 0; i < E2E_BUCKET_UPPER_NANOS.length; i++) {
            if (nanos <= E2E_BUCKET_UPPER_NANOS[i]) {
                bucket = i;
                break;
            }
        }
        e2eBuckets.incrementAndGet(bucket);
    }

    // 버킷 경계 단위의 근사 p95(ms). 표본이 없으면 0
    public double estimateE2eP95Ms() {
        long total = e2eCount.sum();
        if (total == 0) {
            return 0.0;
        }
        long threshold = (long) Math.ceil(total * 0.95);
        long cumulative = 0;
        for (int i = 0; i < e2eBuckets.length(); i++) {
            cumulative += e2eBuckets.get(i);
            if (cumulative >= threshold) {
                return i < E2E_BUCKET_UPPER_NANOS.length
                        ? E2E_BUCKET_UPPER_NANOS[i] / 1_000_000.0
                        : Double.POSITIVE_INFINITY; // 3000ms 초과 버킷까지 가야 95% 충족 = 위험 신호
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    public void outboundRejectedRunSucceeded() {
        long n = outboundRejectedRunSucceeded.incrementAndGet();
        if (n <= 5) { // 로그 폭주 방지 — 처음 몇 건만 "정상 처리됐다"는 증거로 남김
            log.info("[CallerRunsPolicy 검증] outbound 큐 포화로 {} 스레드가 직접 전송을 예외 없이 완료 ({}번째 샘플)",
                    Thread.currentThread().getName(), n);
        }
    }

    public void outboundSendFailed(Throwable ex) {
        long n = outboundSendFailed.incrementAndGet();
        if (n <= 5) {
            log.error("[전송 실패] {}번째, thread={}", n, Thread.currentThread().getName(), ex);
        }
    }

    /**
     * 앱 시작 100초 후부터 20초 간격으로, 그 시점까지의 누적치를 파일에 이어 씁니다.
     * [수정 전] fixedDelay=Long.MAX_VALUE로 단 1회만 실행했었는데, 실제 테스트가 100초
     * 이후에도 계속되면 그 뒤 구간의 상태(특히 E2E_P95, OutboundRejected)는 영영 기록되지
     * 않는 사각지대가 있었음(ExecutorPoolMonitor.logPeakSummary()와 같은 문제, 같은 이유로
     * 같이 수정). 카운터 자체는 누적형이라 리셋되지 않으므로, 매 호출은 "그 시점까지의
     * 누적 통계"이고 회차를 거듭할수록 표본이 늘어 p95 추정도 더 안정적으로 수렴함.
     * initialDelay: 100초 대기 후 첫 실행
     * fixedDelay: 이후 20초마다 재실행
     */
    @Scheduled(initialDelay = 100000, fixedDelay = 20000)
    public void saveToFileOnce() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 1. 저장 위치 및 파일명 지정 (예: logs 폴더 안에 서버별로 생성)
        // 서버가 여러 대라면 별도의 ID를 파일명에 붙여주는 것이 좋습니다.

        // [수정] 도커 컨테이너 내부의 경로로 설정 (위의 볼륨 설정과 일치해야 함)
        String directoryPath = "C:/chat_logs/logs";

        String fileName = directoryPath + "/newlog00.csv";

        long r = totalReceived.get();
        long s = totalSaved.get();

        long outboundCount = outboundTaskCount.sum();
        double avgOutboundLatencyMs = outboundCount == 0
                ? 0.0
                : (outboundTaskTotalNanos.sum() / (double) outboundCount) / 1_000_000.0;
        double maxOutboundLatencyMs = outboundTaskMaxNanos.get() / 1_000_000.0;
        long rejected = outboundRejectedCount.get();
        long inboundRejected = inboundRejectedCount.get();
        long broadcastDropped = broadcastDroppedCount.get();

        long e2eN = e2eCount.sum();
        double avgE2eMs = e2eN == 0 ? 0.0 : (e2eTotalNanos.sum() / (double) e2eN) / 1_000_000.0;
        double p95E2eMs = estimateE2eP95Ms();
        double maxE2eMs = e2eMaxNanos.get() / 1_000_000.0;
        long rejectedRunOk = outboundRejectedRunSucceeded.get();
        long sendFailed = outboundSendFailed.get();

        String logLine = String.format("%s,%d,%d,%d,%.3f,%.3f,%d,%d,%d,%.3f,%.3f,%.3f,%d,%d\n",
                time, r, s, outboundCount, avgOutboundLatencyMs, maxOutboundLatencyMs, rejected, inboundRejected,
                broadcastDropped, avgE2eMs, p95E2eMs, maxE2eMs, rejectedRunOk, sendFailed);

        // 파일 쓰기가 실패해도(잠금 등) 콘솔에는 항상 남도록 먼저 로그부터 찍음
        log.info(
                "=== [리포트] Received={}, Saved={}, OutboundTasks={}, AvgLatency={}ms, MaxLatency={}ms, OutboundRejected={}, InboundRejected={}, BroadcastDropped={}, E2E_Avg={}ms, E2E_P95={}ms, E2E_Max={}ms, CallerRunsOK={}, SendFailed={} ===",
                r, s, outboundCount, avgOutboundLatencyMs, maxOutboundLatencyMs, rejected, inboundRejected,
                broadcastDropped, avgE2eMs, p95E2eMs, maxE2eMs, rejectedRunOk, sendFailed);

        try {
            // 2. 폴더가 없으면 자동 생성하는 로직 추가
            Files.createDirectories(Paths.get(directoryPath));

            if (!Files.exists(Paths.get(fileName))) {
                String header = "Timestamp,Total_Received,Total_Saved,Outbound_Task_Count,Avg_Outbound_Latency_ms,Max_Outbound_Latency_ms,Outbound_Rejected_Count,Inbound_Rejected_Count,Broadcast_Dropped_Count,E2E_Avg_ms,E2E_P95_ms,E2E_Max_ms,Outbound_CallerRuns_Succeeded,Outbound_Send_Failed\n";
                Files.write(Paths.get(fileName), header.getBytes());
            }

            Files.write(Paths.get(fileName), logLine.getBytes(), StandardOpenOption.APPEND);
            log.info("=== [3분 리포트] 저장 위치: {} ===", Paths.get(fileName).toAbsolutePath());
        } catch (IOException e) {
            log.error("파일 저장 실패: {}", e.getMessage());
        }
    }

}
