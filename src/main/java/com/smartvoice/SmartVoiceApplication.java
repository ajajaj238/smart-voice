package com.smartvoice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.smartvoice")
public class SmartVoiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartVoiceApplication.class, args);
    }
}
