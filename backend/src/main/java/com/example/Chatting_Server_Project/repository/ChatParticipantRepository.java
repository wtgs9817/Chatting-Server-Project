package com.example.Chatting_Server_Project.repository;

import com.example.Chatting_Server_Project.entity.ChatParticipantEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
@RequiredArgsConstructor
public class ChatParticipantRepository {
    private final JdbcTemplate jdbcTemplate;

    public void save(ChatParticipantEntity entity) {
        String sql = "INSERT INTO chat_participant (room_id, user_id, joined_at, exited_at) VALUES (?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                entity.getRoomId(),
                entity.getUserId(),
                Timestamp.valueOf(entity.getJoinedAt()),
                entity.getExitedAt() != null ? Timestamp.valueOf(entity.getExitedAt()) : null
        );
    }
}
