package com.example.Chatting_Server_Project.util;

import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;

public class WebSocketHeaderUtil {

    public static MessageHeaders createHeaders(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE); // 메시지 타입 지정
        accessor.setSessionId(sessionId); //받을 사람 지정
        accessor.setLeaveMutable(true);  //spring 내부 처리 허용(헤더 수정 허용)

        return accessor.getMessageHeaders();

    }
}
