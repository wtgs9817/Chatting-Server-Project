package com.example.Chatting_Server_Project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChattingServerProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChattingServerProjectApplication.class, args);
	}

}
