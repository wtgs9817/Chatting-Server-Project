package com.example.Chatting_Server_Project.entity;


import com.example.Chatting_Server_Project.DTO.MessageDTO;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;


@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter

@Table(name = "message")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "room_id", nullable = false, length = 36)
    private String roomId;

    @Column(name = "sender_id", nullable = false, length = 15)
    private String userId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at")
    private LocalDateTime createdAt;


    public static MessageEntity toMessageEntity(MessageDTO messageDTO) {
        return MessageEntity.builder()
                .roomId(messageDTO.getRoomId())
                .userId(messageDTO.getUserId())
                .content(messageDTO.getContent())
                .createdAt(messageDTO.getCreatedAt()).build();
    }

}
