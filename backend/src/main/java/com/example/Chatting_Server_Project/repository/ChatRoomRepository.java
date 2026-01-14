package com.example.Chatting_Server_Project.repository;

import com.example.Chatting_Server_Project.entity.ChatRoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoomEntity, Long> {
}
