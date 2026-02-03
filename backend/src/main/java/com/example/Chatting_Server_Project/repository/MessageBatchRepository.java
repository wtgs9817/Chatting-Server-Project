package com.example.Chatting_Server_Project.repository;

import com.example.Chatting_Server_Project.entity.MessageEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Slf4j
public class MessageBatchRepository {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void batchInsert(List<MessageEntity> messages) {
        // 1. 실제 DB 연결 옵션 확인 (URL에 옵션이 살아있는지!)
        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            log.info("🔍 [DB 확인] 실제 연결 URL: {}", conn.getMetaData().getURL());
        } catch (SQLException e) {
            log.error("URL 확인 실패", e);
        }

        log.info("Batch Size: {}   !!!!!!!!!", messages.size());

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