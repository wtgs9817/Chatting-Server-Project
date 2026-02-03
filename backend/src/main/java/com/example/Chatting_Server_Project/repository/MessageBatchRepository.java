package com.example.Chatting_Server_Project.repository;

import com.example.Chatting_Server_Project.entity.MessageEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MessageBatchRepository {

    private final JdbcTemplate jdbcTemplate;

    public void batchInsert(List<MessageEntity> messages) {
        String sql = "INSERT INTO message (room_id, sender_id, content, created_at) VALUES (?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                MessageEntity msg = messages.get(i);
                ps.setString(1, msg.getRoomId());
                ps.setString(2, msg.getUserId());
                ps.setString(3, msg.getContent());
                ps.setTimestamp(4, Timestamp.valueOf(msg.getCreatedAt()));
            }

            @Override
            public int getBatchSize() {
                return messages.size();
            }
        });
    }
}