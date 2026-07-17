package com.meetingmind.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MeetingMindAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetingMindAuthApplication.class, args);
    }
}
