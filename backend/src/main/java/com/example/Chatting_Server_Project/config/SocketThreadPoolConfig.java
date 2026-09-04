package com.example.Chatting_Server_Project.config;

import com.example.Chatting_Server_Project.traffic_test.MessageMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@RequiredArgsConstructor
public class SocketThreadPoolConfig {

    private final MessageMetrics messageMetrics;

    @Bean(name = "clientInboundExecutor")
    public ThreadPoolTaskExecutor clientInboundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 20/50은 최초 설정값 그대로 손 안 대고 있었는데, outbound/broadcast를 실측으로
        // 축소하는 과정에서 이 풀도 세션 내내 계속 active=20(=core)에서 멈추고 큐는 5000 상한
        // 근처에 간 적 없이(최대 2243대), InboundRejected는 이 세션 전체에서 단 한 번도
        // 0을 벗어난 적이 없다는 게 확인됨 — broadcastExecutor를 축소하게 만든 것과 같은 패턴.
        // inbound는 사용자 메시지가 최초로 들어오는 관문이라 outbound/broadcast만큼 극단까지
        // 밀어붙이지 않고 한 단계만 낮춰서 검증.
        executor.setCorePoolSize(15);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(5000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("clientInboundChannel-");
        // 이 풀 자체의 큐가 찬 경우(inbound 자체 처리량 한계)와, outbound 배압으로 인해
        // 발행 스레드가 대신 떠맡는 경우(clientOutboundExecutor 쪽 rejected)를 구분하기 위해
        // 카운터를 분리해서 기록
        executor.setRejectedExecutionHandler((task, exec) -> {
            messageMetrics.inboundRejected();
            new ThreadPoolExecutor.CallerRunsPolicy().rejectedExecution(task, exec);
        });
        executor.initialize();
        return executor;
    }

    @Bean(name = "clientOutboundExecutor")
    public ThreadPoolTaskExecutor clientOutboundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 20/50은 VU 1000 실측(outbound rejected 39~81%)에서 명백히 부족했음이 확인된 값.
        // avg latency(0.368ms)로 Little's Law 역산은 신뢰 불가 — CallerRunsPolicy로 넘어간 작업은
        // 큐를 안 거치고 즉시 완료 처리되어 과부하 상태의 평균을 인위적으로 낮춤.
        // 그래서 정밀 계산 대신 큰 폭으로 올리고 재측정하는 방향으로 조정.
        // -> 120/250으로 600 VU 실측한 결과 POOL 피크 요약상 Outbound active peak=74에 불과했음
        // (큐 5000은 계속 포화됐지만 이는 스레드 부족이 아니라 순간 팬아웃 버스트가 큐 용량을
        // 넘어선 것). E2E_P95=100ms대(sendTimeLimit 3000ms 대비 충분한 여유), SendFailed=0으로
        // 확인됐으므로 실측 피크(74) 대비 안전마진을 둔 값으로 축소하여 재검증.
        // -> 80/120으로 재측정(주기적 리포트로 확인, 100초 스냅샷 사각지대 제거 후)한 결과 active가
        // 상한(120)에 계속 눌려있었음. 그런데도 SendFailed=0, BroadcastDropped=0, WS 비정상종료
        // 0, E2E_P95=100ms대 그대로 — "상한에 눌림"이 실패 신호가 아님을 재확인. 다만 이건
        // "충분하다"가 아니라 "바닥을 아직 못 찾았다"는 뜻이므로, 실패가 실제로 나타나는
        // 지점을 찾기 위해 한 단계 더 축소.
        // -> 50/80 재측정 결과 피크 요약상 active peak=61로 상한(80) 밑에서 멈춤(=80도 아직
        // 바닥 아님). 실패 지표는 여전히 전부 0. 관측 피크(61) 기준으로 한 단계 더 축소해서
        // 바닥 탐색 계속.
        // -> 35/60 재측정 결과 active peak=59로 상한(60)에 거의 눌림(직전 80 상한 때는
        // 61/80로 여유 있었음) — 진짜 필요 동시 활성 스레드가 60~65 부근이라는 첫 신호.
        // 그런데도 SendFailed=0, E2E_Max=232ms로 여전히 멀쩡 — 진짜 바닥은 더 아래에 있을 수
        // 있으므로 한 단계 더 축소해서 실패가 실제로 나오는 지점을 계속 탐색.
        // -> 25/45는 active=45/45, queue=5000으로 확실히 포화(WARN 로그로도 확인). 같은 설정
        // 2회 반복 재현 결과 두 번 다 SendFailed=0, E2E_Max 168ms/274ms로 완전 포화 상태에서도
        // 실패 없음이 일관되게 재현됨(직전 라운드 대비 "줄일수록 좋아지는" 추세는 재현 안 돼서
        // 잡음이었던 것으로 판명). 완전 포화가 실패로 안 이어지므로, 조금씩이 아니라 크게
        // 낮춰서 실제 실패 지점을 효율적으로 탐색.
        // -> 10/15까지 내려도 여전히 SendFailed=0, E2E_Max=329ms로 안 깨짐. 다만 이 시점부터는
        // 로컬 테스트의 한계에 부딪힘 — AvgLatency(처리 시간)가 0.07ms대로 소켓 write 자체가
        // 로컬 환경에서 사실상 공짜라, CallerRunsPolicy의 실제 비용(느린 클라이언트/실제
        // 네트워크 지연 상황에서 스레드가 오래 묶이는 것)이 이 테스트에선 드러나지 않음. 즉
        // 여기서 더 내려도 "진짜 운영 환경의 바닥"이 아니라 "로컬 즉시완료 작업을 최소 몇
        // 스레드로 감당하나"만 재게 됨.
        //
        // [최종 확정] 이번 탐색에서 유일하게 재현성 있던 실전 신호는 35/60(peak=59, 거의 눌림)
        // vs 50/80(peak=61, 여유)이었던 60~65 부근의 전환점. 로컬 테스트가 못 보는 "실제
        // 네트워크에서 소켓 write가 느려지는" 리스크에 대비해 이 관측치에 안전마진을 얹어 확정.
        executor.setCorePoolSize(50);
        executor.setMaxPoolSize(90);
        executor.setQueueCapacity(5000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("clientOutboundChannel-");
        executor.setRejectedExecutionHandler((task, exec) -> {
            messageMetrics.outboundRejected();
            new ThreadPoolExecutor.CallerRunsPolicy().rejectedExecution(task, exec);
        });
        executor.initialize();
        return executor;
    }

    // ChatController가 브로드캐스트 발행(convertAndSend)을 여기로 제출만 하고 바로 리턴하도록 분리.
    // outbound 큐가 꽉 차서 CallerRunsPolicy가 걸리더라도 그 부담은 이 풀의 스레드가 지고,
    // clientInboundChannel 워커는 절대 붙잡히지 않음.
    //
    // 제출 빈도가 팬아웃 대상 수(N)가 아니라 수신 메시지 건수 기준이라 outbound보다 훨씬 여유롭지만,
    // 혹시 이 풀 자체 큐까지 찬 경우엔 CallerRunsPolicy를 쓰지 않음 — 그러면 결국 이 execute()를
    // 호출한 inbound 스레드가 다시 떠맡게 되어 분리한 의미가 없어지기 때문. 대신 버리고
    // messageMetrics.broadcastDropped()로 카운트만 남김 (드문 상황이어야 정상)
    @Bean(name = "broadcastExecutor")
    public ThreadPoolTaskExecutor broadcastExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // outbound가 막히면 그 부담(CallerRunsPolicy)을 이 풀이 대신 떠안는 구조라, outbound를
        // 키운 것과 비례해서 같이 키움 — 지난 테스트에서 이 풀 큐가 거의 꽉 차서(4990/5000)
        // 브로드캐스트 2,482건이 유실됐던 것을 줄이기 위함
        // -> 100/200으로 600 VU 실측한 결과 POOL 피크 요약상 active peak=100(=core 크기에서
        // 멈춤), queue peak=21/5000으로 여유가 커서 max=200까지 갈 필요가 없었음. BroadcastDropped=0
        // 유지 확인 후 실측 피크(100) 대비 안전마진을 둔 값으로 축소하여 재검증.
        // -> 70/120 재측정 결과 active peak=70(=core에서 멈춤), queue peak=142/5000 — 여전히
        // BroadcastDropped=0 유지하며 core만으로 커버됨. outbound 쪽 바닥을 찾는 이번 라운드와
        // 보조를 맞춰 이 풀도 한 단계 더 축소.
        // -> 50/80 재측정 결과도 active peak=50(=core)에서 그대로 멈춤 — 자바 스레드풀이
        // 큐가 안 찬 상태에서는 core 이상 안 늘리는 특성상, core를 낮추기 전까지는 진짜 필요량을
        // 확인할 수 없음. core를 의미 있게 낮춰서 재검증.
        // -> 35/60도 또 active peak=35(=core)에서 그대로 멈춤 — 100→70→50→35 네 라운드
        // 연속으로 매번 core와 정확히 같음. 큐도 계속 여유(최대 314/5000)였고 BroadcastDropped도
        // 계속 0이라, 이 워크로드에서 broadcast가 진짜 필요로 하는 바닥을 아직 한 번도 못
        // 봤다는 뜻 — 더 과감하게 축소.
        // -> 15/30도 2회 반복 모두 active=15(=core)에서 멈추고 BroadcastDropped=0 유지 —
        // outbound 쪽과 보조를 맞춰 더 과감하게 축소해서 진짜 바닥 탐색.
        // -> 5/10까지 내려도 여전히 BroadcastDropped=0, queue peak=275/5000. 이 풀은 전 구간에서
        // 단 한 번도 압박받은 흔적이 없었음(core를 100→5까지 스무 배 낮추는 동안 큐가 위험
        // 수준까지 찬 적이 없음). outbound와 같은 이유(로컬 테스트 한계)로 진짜 바닥은 못
        // 찾았지만, 이쪽은 애초에 반응 자체가 거의 없었으므로 outbound보다 낮은 안전마진으로
        // 확정.
        //
        // [최종 확정] outbound가 60~65 부근에서 처음 눌리기 시작한 것과 짝을 맞춰, outbound가
        // CallerRunsPolicy로 넘기는 부담을 받아내는 이 풀도 비례해서 확정.
        executor.setCorePoolSize(30);
        executor.setMaxPoolSize(60);
        executor.setQueueCapacity(5000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("broadcast-");
        executor.setRejectedExecutionHandler((task, exec) -> messageMetrics.broadcastDropped());
        executor.initialize();
        return executor;
    }

}
