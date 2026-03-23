package com.example.Chatting_Server_Project.service;


import com.example.Chatting_Server_Project.DTO.MessageDTO;
import com.example.Chatting_Server_Project.entity.MessageEntity;
import com.example.Chatting_Server_Project.repository.MessageBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.HandlerMapping;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
@Service

public class MessageService {

    //private final MessageRepository messageRepository;
    private final MessageBatchRepository messageBatchRepository;
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicBoolean flushCheck = new AtomicBoolean(false);
    private final Queue<MessageEntity> messageBuffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger addCount = new AtomicInteger(0);
    private final Executor batchFlushExecutor;

    //private final HandlerMapping stompWebSocketHandlerMapping;

    public void addMessage(String RoomId, MessageDTO messageDTO) {
        int total = addCount.incrementAndGet();



        messageDTO.setRoomId(RoomId);
        if (messageDTO.getCreatedAt() == null) {
            messageDTO.setCreatedAt(LocalDateTime.now());
        }
        MessageEntity messageEntity = MessageEntity.toMessageEntity(messageDTO);
        messageBuffer.offer(messageEntity);
        int cnt = count.incrementAndGet();

        if(cnt >= 1000 && flushCheck.compareAndSet(false, true)) {
            //flush();
            CompletableFuture.runAsync(this::flush, batchFlushExecutor);
        }
    }

    @Scheduled(fixedRate = 50000)
    public void scheduledFlush() {
        if(count.get() > 0 && flushCheck.compareAndSet(false, true)) {
            CompletableFuture.runAsync(this::flush, batchFlushExecutor);
        }
    }


    private void flush() {
        try {
            List<MessageEntity> list = new ArrayList<>();
            MessageEntity entity;
            while (list.size() < 1000 && (entity = messageBuffer.poll()) != null) {
                list.add(entity);
            }
            if (!list.isEmpty()) {
                messageBatchRepository.batchInsert(list);
                count.addAndGet(-list.size());
            }
        } catch (Exception e) {
            log.error("에러 : ", e);
        } finally {
            flushCheck.set(false);
        }
    }
}
