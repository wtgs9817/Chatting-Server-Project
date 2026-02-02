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

@Component
@Slf4j
@RequiredArgsConstructor
@Getter
public class MessageMetrics {



    private final AtomicLong received = new AtomicLong(); // 서버가 수신한 메시지
    private final AtomicLong saved = new AtomicLong(); //최종 저장하게된 메시지

    // 1분 후 최종 리포트 파일 저장용 누적 카운터 (초기화 안 함)
    private final AtomicLong totalReceived = new AtomicLong();
    private final AtomicLong totalSaved = new AtomicLong();


    public void receivedMessage() {
        received.incrementAndGet();
        totalReceived.incrementAndGet(); // 누적분 추가
    }

    public void savedMessage(int count) {
        saved.addAndGet(count);
        totalSaved.addAndGet(count); // 누적분 추가
    }

    /**
     * [단 1회 실행] 앱 시작 1분(60초) 후, 전체 누적치를 파일로 저장합니다.
     * initialDelay: 60초 대기 후 실행
     * fixedDelay: 실행 완료 후 다음 실행까지의 대기 시간 (최대치로 설정하여 재실행 방지)
     */
    @Scheduled(initialDelay = 180000, fixedDelay = Long.MAX_VALUE)
    public void saveToFileOnce() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));


        // 1. 저장 위치 및 파일명 지정 (예: logs 폴더 안에 서버별로 생성)
        // 서버가 여러 대라면 별도의 ID를 파일명에 붙여주는 것이 좋습니다.


        // [수정] 도커 컨테이너 내부의 경로로 설정 (위의 볼륨 설정과 일치해야 함)
        String directoryPath = "C:/chat_logs/logs";

        // [수정] 파일명에 serverId를 포함하여 app-1, app-2 등이 각자 파일을 만들게 함
        String fileName = directoryPath + "single_final_metrics_" + ".csv";

        long r = totalReceived.get();
        long s = totalSaved.get();

        String logLine = String.format("%s,%d,%d\n", time, r, s);

        try {
            // 2. 폴더가 없으면 자동 생성하는 로직 추가
            Files.createDirectories(Paths.get(directoryPath));

            if (!Files.exists(Paths.get(fileName))) {
                String header = "Timestamp,Total_Received,Total_Saved\n";
                Files.write(Paths.get(fileName), header.getBytes());
            }

            Files.write(Paths.get(fileName), logLine.getBytes(), StandardOpenOption.APPEND);
            log.info("=== [3분 리포트] 저장 위치: {} ===", Paths.get(fileName).toAbsolutePath());
        } catch (IOException e) {
            log.error("파일 저장 실패: {}", e.getMessage());
        }
    }

}
