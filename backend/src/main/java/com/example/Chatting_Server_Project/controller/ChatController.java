package com.example.Chatting_Server_Project.controller;

import com.example.Chatting_Server_Project.DTO.MessageDTO;
import com.example.Chatting_Server_Project.DTO.RequestDTO;
import com.example.Chatting_Server_Project.DTO.ResponseUserDTO;
import com.example.Chatting_Server_Project.service.MessageService;
import com.example.Chatting_Server_Project.service.ParticipantService;
import com.example.Chatting_Server_Project.service.RoomService;
import com.example.Chatting_Server_Project.traffic_test.MessageMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.concurrent.Executor;

import static com.example.Chatting_Server_Project.util.WebSocketHeaderUtil.createHeaders;

@Controller
@RequiredArgsConstructor
public class ChatController {
    private final MessageService messageService;
    private final RoomService roomService;
    private final ParticipantService participantService;
    private final SimpMessagingTemplate messagingTemplate;

    private final MessageMetrics messageMetrics;

    // outbound 큐가 찬 상태에서 convertAndSend를 직접 부르면 CallerRunsPolicy가 이 inbound
    // 워커 스레드를 붙잡아 팬아웃 전체(N명 write)를 떠맡긴다 —
    // clientInboundExecutor/clientOutboundExecutor
    // 큐가 같은 시간대에 함께 포화되는 원인이었음(pool_stats.csv로 확인, outbound rejected 81.3%).
    // 그래서 발행 자체를 broadcastExecutor에 제출만 하고 바로 리턴하도록 분리
    private final Executor broadcastExecutor;

    @MessageMapping("/chat.join")
    public void joinRoom(RequestDTO requestDTO, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String roomId = roomService.findRoom().getRoomId();

        participantService.createUser(requestDTO, roomId);

        broadcastExecutor.execute(() -> messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/room-info",
                roomId,
                createHeaders(sessionId)));

        ResponseUserDTO response = new ResponseUserDTO(requestDTO.getUserId(), roomId);
        broadcastExecutor.execute(() -> messagingTemplate.convertAndSend("/topic/chatroom/" + roomId, response));
    }

    @MessageMapping("/chat.send/{roomId}")
    public void sendMessage(@DestinationVariable String roomId, MessageDTO messageDTO) {
        messageService.addMessage(roomId, messageDTO);
        messageMetrics.receivedMessage();
        broadcastExecutor.execute(() -> messagingTemplate.convertAndSend("/topic/chatroom/" + roomId, messageDTO));
    }
}
