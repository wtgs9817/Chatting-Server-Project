package com.example.Chatting_Server_Project.redis;

import com.example.Chatting_Server_Project.DTO.JoinDTO;
import com.example.Chatting_Server_Project.DTO.MessageDTO;
import com.example.Chatting_Server_Project.traffic_test.MessageMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPublisher {
    /*
    클라이언트로부터 받은 메시지를 Redis 채널에 발행
    클라이언트 -> Controller -> RedisPublisher -> Redis -> 모든 서버
    */

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper mapper = new ObjectMapper();
    //Java에서 JSON 데이터 구조를 처리하는 데 가장 널리 사용되는 Jackson 라이브러리의 핵심 클래스
    private final MessageMetrics messageMetrics;

    public void publish(String roomId, MessageDTO message) {
        //writeValueAsString --> 직렬화 메소드
        //readValue --> 역직렬화 메소드

        try {
            message.setRoomId(roomId);
            //String json = mapper.writeValueAsString(message);

            System.out.println("=== Redis 발행 ===");  // 이거 찍히나?
            System.out.println("채널: chat:messages");
            //System.out.println("메시지: " + json);

            redisTemplate.convertAndSend("chat:messages", message);
            messageMetrics.publishedMessage();


        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void publish(String sessionId, String destination, String roomId, String userId) {
        log.info("=== publish 시작: userId={} ===", userId);
        try {
            JoinDTO joinDTO = new JoinDTO(sessionId, destination, roomId, userId);
            log.info("=== JoinDTO 생성 완료 ===");

            //String json = mapper.writeValueAsString(joinDTO);
            //redisTemplate.convertAndSend("room-info", joinDTO);
            redisTemplate.convertAndSend("room:info", joinDTO);
            log.info("=== Redis 전송 완료 ===");
        }
        catch(Exception e) {
            log.error("!!! Redis 전송 중 에러 발생 !!!", e);
            throw new RuntimeException(e);

        }
    }


}
