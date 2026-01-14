package com.example.Chatting_Server_Project.service;


import com.example.Chatting_Server_Project.DTO.ParticipantDTO;
import com.example.Chatting_Server_Project.DTO.RequestDTO;
import com.example.Chatting_Server_Project.entity.ChatParticipantEntity;
import com.example.Chatting_Server_Project.repository.ChatParticipantRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Getter
public class ParticipantService {

    private final ChatParticipantRepository chatParticipantRepository;

    public void createUser(RequestDTO requestDTO, String id) {
        ParticipantDTO participantDTO = ParticipantDTO.builder()
                .userId(requestDTO.getUserId())
                .roomId(id)
                .joinedAt(LocalDateTime.now())
                .exitedAt(null)
                .build();

        ChatParticipantEntity cp = ChatParticipantEntity.toParticipantEntity(participantDTO);
        chatParticipantRepository.save(cp);
    }



}
