package com.example.Chatting_Server_Project.DTO;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ResponseUserDTO {

    private String userId;
    private String roomId;
}
