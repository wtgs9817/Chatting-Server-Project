package com.example.Chatting_Server_Project.service;


import com.example.Chatting_Server_Project.DTO.MessageDTO;
import com.example.Chatting_Server_Project.entity.MessageEntity;
import com.example.Chatting_Server_Project.repository.MessageRepository;
import com.example.Chatting_Server_Project.traffic_test.MessageMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


@RequiredArgsConstructor
@Service
@Slf4j
public class MessageService {

    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicBoolean flushCheck = new AtomicBoolean(false);
    private final MessageMetrics messageMetrics;
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
    public void scheduledFlush() {
        if(count.get() > 0 && flushCheck.compareAndSet(false, true)) {

            flush();
        }
    }

    private void flush() {
        try {
            while (messageBuffer.peek() != null) {  // 버퍼 빌 때까지 반복
                List<MessageEntity> list = new ArrayList<>();
                MessageEntity entity;

                while (list.size() < 200 && (entity = messageBuffer.poll()) != null) {
                    list.add(entity);
                }

                if (!list.isEmpty()) {
                    messageRepository.saveAll(list);
                    messageMetrics.savedMessage(list.size());
                    count.addAndGet(-list.size());
                }

            }
        } catch (Exception e) {
            log.error("에러 : ", e);
        } finally {
            flushCheck.set(false);
        }
    }
}
