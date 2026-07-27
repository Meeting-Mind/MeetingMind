package com.meetingmind.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MeetingMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetingMindApplication.class, args);
    }
}
