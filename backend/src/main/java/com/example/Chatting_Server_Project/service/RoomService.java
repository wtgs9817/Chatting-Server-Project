package com.example.Chatting_Server_Project.service;


import com.example.Chatting_Server_Project.entity.ChatRoomEntity;
import com.example.Chatting_Server_Project.repository.ChatRoomRepository;

import lombok.Getter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentLinkedQueue;


@Service
@RequiredArgsConstructor
@Getter

//일단 채팅방은 한 개를 가지고 테스트할거라 보류
//동시성 문제도 일단 보류
public class RoomService {

    private final ChatRoomRepository chatRoomRepository;
    //private final Map<String, AtomicInteger> room = new ConcurrentHashMap<>();
    private final Queue<ChatRoomEntity> chatRoomQueue = new ConcurrentLinkedQueue<>();


    public ChatRoomEntity findRoom() {
        if(chatRoomQueue.isEmpty()) {
            ChatRoomEntity cr = createRoom();

            return cr;
        }

        ChatRoomEntity chatRoom = chatRoomQueue.peek();
        return chatRoom;
    }


    public ChatRoomEntity createRoom() {

        ChatRoomEntity room = ChatRoomEntity.builder()
                .roomId("채팅방")
                .createdAt(LocalDateTime.now())
                .build();

        chatRoomQueue.offer(room);
        return chatRoomRepository.save(room);
    }
}
