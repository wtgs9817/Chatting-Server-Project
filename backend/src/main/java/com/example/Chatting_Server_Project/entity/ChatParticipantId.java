package com.example.Chatting_Server_Project.entity;

import lombok.*;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class ChatParticipantId implements Serializable {

    private String roomId;
    private String userId;
}
