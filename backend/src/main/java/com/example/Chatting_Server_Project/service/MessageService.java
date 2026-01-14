package com.example.Chatting_Server_Project.service;


import com.example.Chatting_Server_Project.DTO.MessageDTO;
import com.example.Chatting_Server_Project.entity.MessageEntity;
import com.example.Chatting_Server_Project.repository.MessageRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;



@RequiredArgsConstructor
@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final Queue<MessageEntity> messageBuffer = new ConcurrentLinkedQueue<>();


    public void addMessage(String RoomId, MessageDTO messageDTO) {
        messageDTO.setRoomId(RoomId);
        MessageEntity me = MessageEntity.toMessageEntity(messageDTO);
        messageBuffer.offer(me);

        if(messageBuffer.size() >= 100) {
            flush();
        }
    }

    @Scheduled(fixedRate = 50000)
    public void flushMessages() {
        if(messageBuffer.isEmpty()) return;
        flush();
    }

    public void flush() {

        List<MessageEntity> saveList = new ArrayList<>();
        while(!messageBuffer.isEmpty()) {
            saveList.add(messageBuffer.poll());
        }
        messageRepository.saveAll(saveList);
    }


}
