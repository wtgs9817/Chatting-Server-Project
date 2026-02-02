package com.example.Chatting_Server_Project.repository;

import com.example.Chatting_Server_Project.entity.ChatParticipantEntity;
import com.example.Chatting_Server_Project.entity.ChatParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipantEntity, ChatParticipantId> {

}
