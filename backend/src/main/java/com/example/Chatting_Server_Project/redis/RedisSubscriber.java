package com.example.Chatting_Server_Project.redis;

import com.example.Chatting_Server_Project.DTO.JoinDTO;
import com.example.Chatting_Server_Project.DTO.MessageDTO;
import com.example.Chatting_Server_Project.DTO.ResponseUserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import static com.example.Chatting_Server_Project.util.WebSocketHeaderUtil.createHeaders;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisSubscriber {
    /*
    Redis로부터 받은 메시지를 WebSocket 구독자에게 전송
    Redis -> RedisSubscriber -> SimpMessagingTemplate -> WebSocket 구독자
    */
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;


    public void handleMessages(MessageDTO messageDTO) {
        log.info("=== m2 시작 ===");

        messagingTemplate.convertAndSend("/topic/chatroom/" + messageDTO.getRoomId(), messageDTO);

        log.info("=== m2 끝 ===");
    }

    public void handleRoomInfo(JoinDTO joinDTO) {

        log.info("=== handleRoomInfo 시작 ===");
        log.info("sessionId: {}", joinDTO.getSessionId());
        log.info("roomId: {}", joinDTO.getRoomId());
        log.info("userId: {}", joinDTO.getUserId());


        messagingTemplate.convertAndSendToUser(
                joinDTO.getSessionId(),
                joinDTO.getDestination(),
                joinDTO.getRoomId(),
                createHeaders(joinDTO.getSessionId())
        );
        log.info("=== convertAndSendToUser 완료 ===");

        ResponseUserDTO responseUserDTO = new ResponseUserDTO();
        responseUserDTO.setUserId(joinDTO.getUserId());
        responseUserDTO.setRoomId(joinDTO.getRoomId());

        log.info("=== 브로드캐스트 전송 ===");
        log.info("목적지: /topic/chatroom/{}", responseUserDTO.getRoomId());

        messagingTemplate.convertAndSend("/topic/chatroom/" + responseUserDTO.getRoomId(), responseUserDTO);

        log.info("=== handleRoomInfo 끝 ===");
    }
}
