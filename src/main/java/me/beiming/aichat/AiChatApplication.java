package me.beiming.aichat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(exclude = {org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class})
@MapperScan("me.beiming.aichat.mapper")
@EnableTransactionManagement
@EnableScheduling
public class AiChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiChatApplication.class, args);
    }

}