package com.example.Chatting_Server_Project.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class ParticipantDTO {


    private String roomId;
    private String userId;
    private LocalDateTime joinedAt;
    private LocalDateTime exitedAt;
}
