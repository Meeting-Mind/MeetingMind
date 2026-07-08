package com.meetingmind.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class MeetingMindApplicationTest {

    @Test
    void contextLoads() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MeetingMindApplication.class)
                .web(WebApplicationType.NONE)
                .run()) {
        }
    }
}
