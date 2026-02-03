package com.example.Chatting_Server_Project.service;


import com.example.Chatting_Server_Project.DTO.MessageDTO;
import com.example.Chatting_Server_Project.entity.MessageEntity;
import com.example.Chatting_Server_Project.repository.JdbcRepository;
import com.example.Chatting_Server_Project.repository.MessageRepository;
import com.example.Chatting_Server_Project.traffic_test.MessageMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;


@RequiredArgsConstructor
@Service
public class MessageService {

    private final JdbcRepository jdbcRepository;
    private final MessageMetrics messageMetrics;
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    private final AtomicInteger tpsCounter = new AtomicInteger(0);



    //동기 저장
    public void addMessage(String RoomId, MessageDTO messageDTO) {
        messageDTO.setRoomId(RoomId);
        if (messageDTO.getCreatedAt() == null) {
            messageDTO.setCreatedAt(LocalDateTime.now());
        }

        MessageEntity me = MessageEntity.toMessageEntity(messageDTO);

        jdbcRepository.insert(me);
        messageMetrics.savedMessage(messageCounter.incrementAndGet());
    }



}


