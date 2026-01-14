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



    //동기 저장
    public void addMessage(String RoomId, MessageDTO messageDTO) {
        messageDTO.setRoomId(RoomId);
        MessageEntity me = MessageEntity.toMessageEntity(messageDTO);

        messageRepository.save(me);
    }




}
