package com.example.Chatting_Server_Project.DTO;


import com.example.Chatting_Server_Project.entity.MessageEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class MessageDTO {
    private Long messageId;
    private String roomId;
    private String userId;
    private String content;
    private LocalDateTime createdAt;





}
