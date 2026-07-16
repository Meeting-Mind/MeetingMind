package com.meetingmind.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetingmind.demo.controller.LiveKitController;
import com.meetingmind.demo.controller.SttController;
import com.meetingmind.demo.controller.SttStreamController;
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
            assertThat(context.getBeansOfType(SttController.class)).isEmpty();
            assertThat(context.getBeansOfType(SttStreamController.class)).isEmpty();
            assertThat(context.getBeansOfType(LiveKitController.class)).isEmpty();
        }
    }

    @Test
    void enablesLegacySttControllersOnlyWithExplicitProfile() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MeetingMindApplication.class)
                .profiles("legacy-stt")
                .web(WebApplicationType.NONE)
                .run()) {
            assertThat(context.getBeansOfType(SttController.class)).hasSize(1);
            assertThat(context.getBeansOfType(SttStreamController.class)).hasSize(1);
        }
    }

    @Test
    void enablesLegacyLiveKitControllerOnlyWithExplicitProfile() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MeetingMindApplication.class)
                .profiles("legacy-livekit")
                .web(WebApplicationType.NONE)
                .run()) {
            assertThat(context.getBeansOfType(LiveKitController.class)).hasSize(1);
        }
    }
}
