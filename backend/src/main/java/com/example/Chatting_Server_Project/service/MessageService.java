package com.example.Chatting_Server_Project.service;


import com.example.Chatting_Server_Project.DTO.MessageDTO;
import com.example.Chatting_Server_Project.entity.MessageEntity;
import com.example.Chatting_Server_Project.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicBoolean flushCheck = new AtomicBoolean(false);
    private final Queue<MessageEntity> messageBuffer = new ConcurrentLinkedQueue<>();


    public void addMessage(String RoomId, MessageDTO messageDTO) {
        messageDTO.setRoomId(RoomId);
        MessageEntity messageEntity = MessageEntity.toMessageEntity(messageDTO);
        messageBuffer.offer(messageEntity);
        int cnt = count.incrementAndGet();

        if(cnt >= 200 && flushCheck.compareAndSet(false, true)) {
            flush();
        }
    }

    @Scheduled(fixedRate = 300000)
    public void scheduledFlush() {
        if(count.get() > 0 && flushCheck.compareAndSet(false, true)) {
            flush();
        }
    }

    private void flush() {
        try{
            List<MessageEntity> list = new ArrayList<>();
            MessageEntity entity;

            while (list.size() <= 200 && (entity = messageBuffer.poll()) != null) {
                list.add(entity);
            }

            if(!list.isEmpty()) {
                messageRepository.saveAll(list);
                count.addAndGet(-list.size());
            }
        }
        catch(Exception e) {
            log.error("에러 : " , e);
        }

        finally{
            flushCheck.set(false);
        }
    }
}
