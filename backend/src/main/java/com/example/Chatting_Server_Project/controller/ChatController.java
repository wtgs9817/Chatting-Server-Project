package com.example.Chatting_Server_Project.controller;


import com.example.Chatting_Server_Project.DTO.MessageDTO;
import com.example.Chatting_Server_Project.DTO.RequestDTO;
import com.example.Chatting_Server_Project.redis.RedisPublisher;
import com.example.Chatting_Server_Project.service.MessageService;
import com.example.Chatting_Server_Project.service.ParticipantService;
import com.example.Chatting_Server_Project.service.RoomService;
import com.example.Chatting_Server_Project.traffic_test.MessageMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import org.springframework.stereotype.Controller;


@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {
    @Value("${SEVER_ID:unknown}")
    private String serverId;

    private final MessageService messageService;
    private final RoomService roomService;
    private final ParticipantService participantService;
    private final RedisPublisher redisPublisher;

    private final MessageMetrics messageMetrics;



    @MessageMapping("/chat.join")
    public void joinRoom(RequestDTO requestDTO, SimpMessageHeaderAccessor accessor) {

        String sessionId = accessor.getSessionId();
        String roomId = roomService.findRoom().getRoomId();

        participantService.createUser(requestDTO, roomId);
        redisPublisher.publish(sessionId, "/queue/room-info", roomId, requestDTO.getUserId());

        /*
        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/room-info",
                roomId,
                createHeaders(sessionId)
        );
        ResponseUserDTO response= new ResponseUserDTO(requestDTO.getUserId(), roomId);
        messagingTemplate.convertAndSend("/topic/chatroom/" + roomId, response);
        */
    }



    @MessageMapping("/chat.send/{roomId}")
    public void sendMessage(@DestinationVariable String roomId, MessageDTO messageDTO) {
        log.info("=== {} 에서 메시지 처리 ===", serverId);
        log.info("내용: {}", messageDTO.getContent());

        messageMetrics.receivedMessage();
        messageService.addMessage(roomId, messageDTO);
        redisPublisher.publish(roomId, messageDTO);
    }



}
