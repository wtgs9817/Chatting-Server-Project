package com.example.Chatting_Server_Project.entity;

import com.example.Chatting_Server_Project.DTO.ParticipantDTO;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Entity
@Table(name = "chat_participant")
@IdClass(ChatParticipantId.class)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatParticipantEntity {

    @Id
    @Column(name = "room_id", length = 36)
    private String roomId;

    @Id
    @Column(name = "user_id", length = 15)
    private String userId;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "exited_at")
    private LocalDateTime exitedAt;


    public static ChatParticipantEntity toParticipantEntity(ParticipantDTO participantDTO) {
        return ChatParticipantEntity.builder()
                .roomId(participantDTO.getRoomId())
                .userId(participantDTO.getUserId())
                .joinedAt(LocalDateTime.now())
                .exitedAt(null).build();
    }


}
