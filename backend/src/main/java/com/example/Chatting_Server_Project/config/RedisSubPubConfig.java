package com.example.Chatting_Server_Project.config;


import com.example.Chatting_Server_Project.DTO.JoinDTO;
import com.example.Chatting_Server_Project.DTO.MessageDTO;
import com.example.Chatting_Server_Project.redis.RedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RedisSubPubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory, MessageListenerAdapter chatAdapter, MessageListenerAdapter roomAdapter) {
        //RedisConnectionFactory redis 서버 연결 정보 (Spring에서 자동 주입)
        //MessageListener 메시지 수신 처리

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        // 메시지 수신 담당 객체( ex) 컨테이너 = 라디오 수신기)
        container.setConnectionFactory(connectionFactory);

        //container.addMessageListener(redisSubscriber, new ChannelTopic("chat:messages"));
        // 이것도 가능함. container.addMessageListener(redisRoomInfoSubcriber, new ChannelTopic("chat:roominfo"));

        container.addMessageListener(chatAdapter, new ChannelTopic("chat:messages"));
        container.addMessageListener(roomAdapter, new ChannelTopic("room:info"));

        return container;
    }

    @Bean
    public MessageListenerAdapter chatAdapter(RedisSubscriber subscriber, ObjectMapper objectMapper) {
        MessageListenerAdapter chatAdpater = new MessageListenerAdapter(subscriber, "handleMessages");

        JacksonJsonRedisSerializer serializer = new JacksonJsonRedisSerializer<>(objectMapper, MessageDTO.class);
        chatAdpater.setSerializer(serializer);

        //return new MessageListenerAdapter(subscriber, "sendMessageToClient");
        return chatAdpater;
    }

    @Bean
    public MessageListenerAdapter roomAdapter(RedisSubscriber subscriber, ObjectMapper objectMapper) {
        MessageListenerAdapter roomAdpater = new MessageListenerAdapter(subscriber, "handleRoomInfo");

        JacksonJsonRedisSerializer serializer = new JacksonJsonRedisSerializer<>(objectMapper, JoinDTO.class);
        roomAdpater.setSerializer(serializer);

        return roomAdpater;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        //Key는 문자열로 저장
        template.setKeySerializer(new StringRedisSerializer());
        //Value는 JSON으로 저장하되, 클래스 정보를 포함해서 저장(범용)
        template.setValueSerializer(new JacksonJsonRedisSerializer<>(objectMapper, Object.class));

        return template;
    }

    @Bean
    public RedisTemplate<String, Long> testRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Long> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JacksonJsonRedisSerializer<>(Long.class));

        return template;
    }




}
