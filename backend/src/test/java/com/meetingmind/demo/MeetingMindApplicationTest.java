package com.meetingmind.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetingmind.demo.controller.LiveKitController;
import com.meetingmind.demo.controller.SttController;
import com.meetingmind.demo.controller.SttStreamController;
import com.meetingmind.demo.service.HttpMeetingAiGatewayClient;
import com.meetingmind.demo.service.HttpProjectAiGatewayClient;
import com.meetingmind.demo.service.HttpReportAiGatewayClient;
import com.meetingmind.demo.service.HttpTaskAiGatewayClient;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void wiresAiGatewayClientsFromOnPremEnvironmentProperties() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MeetingMindApplication.class)
                .web(WebApplicationType.NONE)
                .initializers(applicationContext -> TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                        applicationContext,
                        "MEETINGMIND_AI_BASE_URL=http://meetingmind-ai.internal:8000/",
                        "AI_INTERNAL_SERVICE_TOKEN=internal-service-token"
                ))
                .run()) {
            HttpMeetingAiGatewayClient meetingGateway = context.getBean(HttpMeetingAiGatewayClient.class);
            HttpProjectAiGatewayClient projectGateway = context.getBean(HttpProjectAiGatewayClient.class);
            HttpReportAiGatewayClient reportGateway = context.getBean(HttpReportAiGatewayClient.class);
            HttpTaskAiGatewayClient taskGateway = context.getBean(HttpTaskAiGatewayClient.class);

            assertThat(uriField(meetingGateway, "chatUri"))
                    .isEqualTo(URI.create("http://meetingmind-ai.internal:8000/api/internal/meeting-ai/chat"));
            assertThat(uriField(projectGateway, "chatUri"))
                    .isEqualTo(URI.create("http://meetingmind-ai.internal:8000/api/internal/project-ai/chat"));
            assertThat(uriField(reportGateway, "generateUri"))
                    .isEqualTo(URI.create("http://meetingmind-ai.internal:8000/api/internal/meeting-ai/generate-report"));
            assertThat(uriField(taskGateway, "extractUri"))
                    .isEqualTo(URI.create("http://meetingmind-ai.internal:8000/api/internal/meeting-ai/extract-tasks"));
            assertThat(ReflectionTestUtils.getField(meetingGateway, "serviceToken"))
                    .isEqualTo("internal-service-token");
            assertThat(ReflectionTestUtils.getField(projectGateway, "serviceToken"))
                    .isEqualTo("internal-service-token");
            assertThat(ReflectionTestUtils.getField(reportGateway, "serviceToken"))
                    .isEqualTo("internal-service-token");
            assertThat(ReflectionTestUtils.getField(taskGateway, "serviceToken"))
                    .isEqualTo("internal-service-token");
        }
    }

    private static URI uriField(Object target, String fieldName) {
        return (URI) ReflectionTestUtils.getField(target, fieldName);
    }
}
