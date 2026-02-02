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
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import static com.example.Chatting_Server_Project.util.WebSocketHeaderUtil.createHeaders;

@Controller
@RequiredArgsConstructor
public class ChatController {
    private final MessageService messageService;
    private final RoomService roomService;
    private final ParticipantService participantService;
    private final SimpMessagingTemplate messagingTemplate;

    private final MessageMetrics messageMetrics;

    @MessageMapping("/chat.join")
    public void joinRoom(RequestDTO requestDTO, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String roomId = roomService.findRoom().getRoomId();

        participantService.createUser(requestDTO, roomId);

        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/room-info",
                roomId,
                createHeaders(sessionId)
        );

        ResponseUserDTO response= new ResponseUserDTO(requestDTO.getUserId(), roomId);
        messagingTemplate.convertAndSend("/topic/chatroom/" + roomId, response);
    }

    @MessageMapping("/chat.send/{roomId}")
    @SendTo("/topic/chatroom/{roomId}")
    public MessageDTO sendMessage(@DestinationVariable String roomId, MessageDTO messageDTO) {
        messageService.addMessage(roomId, messageDTO);
        messageMetrics.receivedMessage();
        return messageDTO;
    }



}
