package com.example.Chatting_Server_Project.controller;


import com.example.Chatting_Server_Project.DTO.MessageDTO;
import com.example.Chatting_Server_Project.DTO.RequestDTO;
import com.example.Chatting_Server_Project.DTO.ResponseUserDTO;
import com.example.Chatting_Server_Project.service.MessageService;
import com.example.Chatting_Server_Project.service.ParticipantService;
import com.example.Chatting_Server_Project.service.RoomService;
import com.example.Chatting_Server_Project.util.WebSocketHeaderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import static com.example.Chatting_Server_Project.util.WebSocketHeaderUtil.createHeaders;


@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {
    @Value("${SEVER_ID:unknown}")
    private String serverId;

    private final MessageService messageService;
    private final RoomService roomService;
    private final ParticipantService participantService;
    private final SimpMessagingTemplate messagingTemplate;


    @MessageMapping("/chat.join")
    public void joinRoom(RequestDTO requestDTO, SimpMessageHeaderAccessor accessor) {
        log.info("=== {} 에서 처리 ===", serverId);


        String sessionId = accessor.getSessionId();
        String roomId = roomService.findRoom().getRoomId();

        participantService.createUser(requestDTO, roomId);
        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/room-info",
                roomId,
                createHeaders(sessionId)
        );

        ResponseUserDTO response = new ResponseUserDTO();
        messagingTemplate.convertAndSend("/topic/room-info", response);

        //redisPublisher.publish(sessionId, "/queue/room-info", roomId, requestDTO.getUserId());

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
    @SendTo("/topic/chatroom/{roomId}")
    public MessageDTO sendMessage(@DestinationVariable String roomId, MessageDTO messageDTO) {
        log.info("=== {} 에서 메시지 처리 ===", serverId);
        log.info("내용: {}", messageDTO.getContent());

        messageService.addMessage(roomId, messageDTO);
        return messageDTO;
        //redisPublisher.publish(roomId, messageDTO);
    }



}
