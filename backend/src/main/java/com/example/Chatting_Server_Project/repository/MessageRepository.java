package com.example.Chatting_Server_Project.repository;

import com.example.Chatting_Server_Project.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<MessageEntity, String> {
}
