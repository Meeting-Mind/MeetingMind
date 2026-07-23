package com.meetingmind.stt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SttApplication {

    public static void main(String[] args) {
        SpringApplication.run(SttApplication.class, args);
    }
}
