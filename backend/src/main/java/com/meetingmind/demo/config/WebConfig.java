package com.meetingmind.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:5173", "https://*.ngrok-free.app", "https://*.ngrok-free.dev", "https://*.ngrok.io", "https://*.ngrok.app", "https://*.ngrok.dev")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
