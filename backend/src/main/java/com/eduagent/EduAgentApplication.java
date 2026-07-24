package com.eduagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({"com.eduagent.mapper", "com.eduagent.repository"})
public class EduAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduAgentApplication.class, args);
    }
}