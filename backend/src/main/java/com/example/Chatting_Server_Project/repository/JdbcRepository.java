package com.example.Chatting_Server_Project.repository;

import com.example.Chatting_Server_Project.entity.MessageEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;



@Repository
@RequiredArgsConstructor
public class JdbcRepository {
    private final JdbcTemplate jdbcTemplate;


    public void insert(MessageEntity msg) {
        String sql = "INSERT INTO message (room_id, sender_id, content, created_at) VALUES (?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                msg.getRoomId(),
                msg.getUserId(),
                msg.getContent(),
                Timestamp.valueOf(msg.getCreatedAt())
        );
    }
}




