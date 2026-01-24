package com.example.Chatting_Server_Project.DTO;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class JoinDTO {
    private String sessionId;
    private String destination;
    private String roomId;
    private String userId;

}
